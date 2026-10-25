package fr.opencarbu.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import fr.opencarbu.domain.history.PriceHistoryCsv
import fr.opencarbu.domain.history.PriceHistoryRules
import fr.opencarbu.domain.history.PricePoint
import fr.opencarbu.domain.history.RecordedPrice
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.Station
import java.io.File

/**
 * The price history, in a small SQLite file (built into Android, no library). Blocking: callers
 * use a background thread.
 *
 * Two tables:
 *  - `changes`: one row per recorded price change (station, fuel, moment, price in thousandths of
 *    a euro), clustered on (station, fuel, moment) so a curve is one contiguous read. About 15 bytes
 *    a row, a few hundred kB a day for the whole country.
 *  - `latest`: the last recorded price of each station and fuel (about 40 000 rows), read in one
 *    go to decide which prices of a new download are changes.
 *
 * The file lives in `noBackupFilesDir`: it is left out of Android's cloud backup, whose size limit
 * it could exceed (a backup that is too large fails for the whole app).
 */
class PriceHistoryStore private constructor(private val file: File, context: Context) :
    SQLiteOpenHelper(context.applicationContext, file.path, null, SCHEMA_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE changes (station INTEGER NOT NULL, fuel INTEGER NOT NULL, at INTEGER NOT NULL, " +
                "price INTEGER NOT NULL, PRIMARY KEY (station, fuel, at)) WITHOUT ROWID",
        )
        db.execSQL(
            "CREATE TABLE latest (key INTEGER PRIMARY KEY, at INTEGER NOT NULL, price INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX changes_by_time ON changes (at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit // version 1 only

    /**
     * Records what changed in [stations] compared with the last recorded prices, then forgets what is
     * older than [cutoffEpochMillis] (null = keep everything).
     * @return the number of price changes recorded.
     */
    @Synchronized
    fun recordFrom(stations: List<Station>, cutoffEpochMillis: Long?): Int {
        val db = writableDatabase
        val changes = PriceHistoryRules.changesToRecord(stations, lastRecorded(db))
        db.beginTransaction()
        try {
            val row = ContentValues()
            for (change in changes) {
                row.clear()
                row.put("station", change.stationId)
                row.put("fuel", change.fuel.ordinal)
                row.put("at", change.atEpochMillis)
                row.put("price", change.milliEuros)
                db.insertWithOnConflict("changes", null, row, SQLiteDatabase.CONFLICT_IGNORE)
                row.clear()
                row.put("key", PriceHistoryRules.key(change.stationId, change.fuel))
                row.put("at", change.atEpochMillis)
                row.put("price", change.milliEuros)
                db.insertWithOnConflict("latest", null, row, SQLiteDatabase.CONFLICT_REPLACE)
            }
            if (cutoffEpochMillis != null) pruneOlderThan(db, cutoffEpochMillis)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return changes.size
    }

    /** Price changes of one station and fuel since [sinceEpochMillis], plus the one in force before it. */
    @Synchronized
    fun points(stationId: Long, fuel: FuelType, sinceEpochMillis: Long): List<PricePoint> {
        val points = ArrayList<PricePoint>()
        readableDatabase.rawQuery(
            "SELECT at, price FROM changes WHERE station = ? AND fuel = ? AND at >= " +
                "COALESCE((SELECT MAX(at) FROM changes WHERE station = ? AND fuel = ? AND at <= ?), 0) ORDER BY at",
            arrayOf(stationId.toString(), fuel.ordinal.toString(), stationId.toString(), fuel.ordinal.toString(), sinceEpochMillis.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) points.add(PricePoint(cursor.getLong(0), cursor.getInt(1)))
        }
        return points
    }

    /**
     * Writes the whole history as CSV (see [PriceHistoryCsv]) to [out], station by station, oldest change
     * first, reading the table row by row (never all in memory). The caller closes [out].
     * @return the number of price lines written (not counting the header).
     */
    @Synchronized
    fun exportCsv(out: java.io.Writer): Int {
        out.write(PriceHistoryCsv.HEADER)
        out.write("\n")
        var count = 0
        readableDatabase.rawQuery("SELECT station, fuel, at, price FROM changes ORDER BY station, fuel, at", null).use { cursor ->
            while (cursor.moveToNext()) {
                val fuel = FuelType.entries.getOrNull(cursor.getInt(1)) ?: continue
                out.write(PriceHistoryCsv.line(cursor.getLong(0), fuel, cursor.getLong(2), cursor.getInt(3)))
                out.write("\n")
                count++
            }
        }
        return count
    }

    /**
     * Adds the lines of an exported history read from [input] (see [PriceHistoryCsv]) to the history.
     * Nothing already there is changed or removed: a line whose (station, fuel, moment) exists is ignored.
     * Lines older than [cutoffEpochMillis] (the retention setting), in the future, or unreadable are
     * skipped. The whole import is one transaction: a failure leaves the history as it was.
     * @throws NotHistoryFileException if the first line is not the header of an exported history.
     */
    @Synchronized
    fun importCsv(input: java.io.BufferedReader, cutoffEpochMillis: Long?, nowEpochMillis: Long): ImportResult {
        val header = generateSequence { input.readLine() }.firstOrNull { it.isNotBlank() }
        if (header == null || !PriceHistoryCsv.isHeader(header)) throw NotHistoryFileException()
        val db = writableDatabase
        var imported = 0
        var skipped = 0
        db.beginTransaction()
        try {
            db.compileStatement("INSERT OR IGNORE INTO changes (station, fuel, at, price) VALUES (?, ?, ?, ?)").use { insert ->
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isBlank()) continue
                    val change = PriceHistoryCsv.parseLine(line)
                    if (change == null || change.atEpochMillis > nowEpochMillis + ONE_DAY_MILLIS ||
                        (cutoffEpochMillis != null && change.atEpochMillis < cutoffEpochMillis)
                    ) {
                        skipped++
                        continue
                    }
                    insert.clearBindings()
                    insert.bindLong(1, change.stationId)
                    insert.bindLong(2, change.fuel.ordinal.toLong())
                    insert.bindLong(3, change.atEpochMillis)
                    insert.bindLong(4, change.milliEuros.toLong())
                    // executeUpdateDelete: the number of rows changed (0 = already there); executeInsert is
                    // unreliable on a WITHOUT ROWID table.
                    if (insert.executeUpdateDelete() > 0) imported++ else skipped++
                }
            }
            // The newest row of each series becomes its "latest" (SQLite gives the other columns of the MAX row).
            if (imported > 0) {
                db.execSQL(
                    "INSERT OR REPLACE INTO latest (key, at, price) " +
                        "SELECT station * ${PriceHistoryRules.FUEL_SLOTS} + fuel, MAX(at), price FROM changes GROUP BY station, fuel",
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ImportResult(imported, skipped)
    }

    /** Applies a shorter retention now (the setting was changed). */
    @Synchronized
    fun prune(cutoffEpochMillis: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            pruneOlderThan(db, cutoffEpochMillis)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Empties the history. */
    @Synchronized
    fun clear() {
        val db = writableDatabase
        db.delete("changes", null, null)
        db.delete("latest", null, null)
        db.execSQL("VACUUM")
    }

    /** Space used on the phone, in bytes. */
    @Synchronized
    fun sizeBytes(): Long = if (file.isFile) file.length() else 0L

    private fun lastRecorded(db: SQLiteDatabase): Map<Long, RecordedPrice> {
        val map = HashMap<Long, RecordedPrice>()
        db.rawQuery("SELECT key, at, price FROM latest", null).use { cursor ->
            while (cursor.moveToNext()) map[cursor.getLong(0)] = RecordedPrice(cursor.getLong(1), cursor.getInt(2))
        }
        return map
    }

    /** Deletes changes older than the cutoff, but keeps each series' last one: it is the price in force. */
    private fun pruneOlderThan(db: SQLiteDatabase, cutoff: Long) {
        db.execSQL(
            "DELETE FROM changes WHERE at < ? AND at < " +
                "(SELECT MAX(c.at) FROM changes c WHERE c.station = changes.station AND c.fuel = changes.fuel)",
            arrayOf<Any>(cutoff),
        )
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

        @Volatile
        private var instance: PriceHistoryStore? = null

        /** One instance for the whole process, shared by the screen and the background job. */
        fun get(context: Context): PriceHistoryStore = instance ?: synchronized(this) {
            instance ?: PriceHistoryStore(File(context.applicationContext.noBackupFilesDir, "price-history.db"), context)
                .also { instance = it }
        }
    }
}

/** What an import did: [imported] lines added; [skipped] lines already there, too old, in the future or unreadable. */
data class ImportResult(val imported: Int, val skipped: Int)

/** The file given to the import is not an exported OpenCarbu price history. */
class NotHistoryFileException : java.io.IOException("Not an OpenCarbu price history file")
