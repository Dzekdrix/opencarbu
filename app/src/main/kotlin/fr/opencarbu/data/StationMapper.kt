package fr.opencarbu.data

import fr.opencarbu.domain.model.FuelPrice
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.Station
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * One record of the open-data feed, as a generic map of column name → value.
 *
 * Values are whatever the JSON reader produced: numbers, strings, booleans, nested
 * maps/lists, or null. Using a generic map keeps this class independent of the JSON
 * library and lets us unit-test it on the JVM.
 */
typealias RawRecord = Map<String, Any?>

/**
 * Converts one raw record of the government "Prix des carburants – flux instantané v2"
 * dataset into a [Station].
 *
 * ## Column names
 * Verified on 2026-10-08 against a real record set (two stations, see
 * `StationMapperTest.mapsRealRecordsFromTheOfficialFeed`). Every name is declared once,
 * in [Columns], so a future change of the feed is a one-line fix there.
 * To re-check, download a fresh sample (command in docs/ARCHITECTURE.md).
 *
 * ## Philosophy: be tolerant, never crash
 * Real-world open data is messy. A record with an unusable position is dropped (returns
 * null); an unusable price is ignored; any other missing field falls back to an empty
 * value. One bad record must never prevent the other ~10 000 from loading.
 */
object StationMapper {

    /** Every column name used by this mapper, in one place. */
    private object Columns {
        const val ID = "id"
        const val ADDRESS = "adresse"
        const val POSTAL_CODE = "cp"
        const val CITY = "ville"
        const val OPEN_24H = "horaires_automate_24_24"

        /** Nested object `{ "lat": ..., "lon": ... }` in degrees: preferred position source. */
        const val GEOMETRY = "geom"
        const val GEOMETRY_LAT = "lat"
        const val GEOMETRY_LON = "lon"

        /** Fallback position columns (may be in degrees or in 1e-5 degrees, see [readCoordinates]). */
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"

        /** Per-fuel columns are `<prefix>_prix` (price) and `<prefix>_maj` (last update). */
        const val PRICE_SUFFIX = "_prix"
        const val UPDATE_SUFFIX = "_maj"

        /** Per-fuel shortage kind: `<prefix>_rupture_type`, e.g. "definitive" (or "temporaire"). */
        const val SHORTAGE_TYPE_SUFFIX = "_rupture_type"
        const val TEMPORARY_SHORTAGE_PREFIX = "temp"

        /** Per-fuel start of the shortage: `<prefix>_rupture_debut`, ISO-8601 like the update dates. */
        const val SHORTAGE_START_SUFFIX = "_rupture_debut"

        /** Summary column listing temporarily missing fuels, e.g. "Gazole;SP98" (or a list). */
        const val TEMPORARY_SHORTAGE_LIST = "carburants_rupture_temporaire"
    }

    /** Column prefix of each fuel in the feed, e.g. DIESEL → `gazole_prix`, `gazole_maj`. */
    private val fuelPrefixes: Map<FuelType, String> = mapOf(
        FuelType.DIESEL to "gazole",
        FuelType.SP95 to "sp95",
        FuelType.E10 to "e10",
        FuelType.SP98 to "sp98",
        FuelType.E85 to "e85",
        FuelType.LPG to "gplc",
    )

    /**
     * Every column this mapper reads. The download asks the server for exactly these
     * (and nothing else): a full record is ~3 KB because of large text columns such as
     * opening hours and services, which the app does not use yet. Deriving the list from
     * the mapper keeps the two in sync automatically.
     */
    val requestedColumns: List<String> = buildList {
        add(Columns.ID)
        add(Columns.ADDRESS)
        add(Columns.POSTAL_CODE)
        add(Columns.CITY)
        add(Columns.OPEN_24H)
        add(Columns.GEOMETRY)
        add(Columns.LATITUDE)
        add(Columns.LONGITUDE)
        add(Columns.TEMPORARY_SHORTAGE_LIST)
        for (prefix in fuelPrefixes.values) {
            add(prefix + Columns.PRICE_SUFFIX)
            add(prefix + Columns.UPDATE_SUFFIX)
            add(prefix + Columns.SHORTAGE_TYPE_SUFFIX)
            add(prefix + Columns.SHORTAGE_START_SUFFIX)
        }
    }

    /**
     * Prices outside this interval (€/L) are treated as data-entry errors and ignored
     * (e.g. "1789" typed instead of "1.789"). Deliberately wide: it only needs to catch
     * obvious mistakes, not real price spikes.
     */
    const val MIN_PLAUSIBLE_PRICE = 0.5
    const val MAX_PLAUSIBLE_PRICE = 5.0

    /** Source coordinates larger than this (in absolute value) are in 1e-5 degrees. */
    private const val MAX_DEGREES = 180.0
    private const val MICRODEGREE_DIVISOR = 100_000.0

    /** @return the station, or null if the record has no usable id or position. */
    fun map(record: RawRecord): Station? {
        val id = asDouble(record[Columns.ID])?.toLong() ?: return null
        val (latitude, longitude) = readCoordinates(record) ?: return null

        return Station(
            id = id,
            latitude = latitude,
            longitude = longitude,
            address = asText(record[Columns.ADDRESS]),
            postalCode = asText(record[Columns.POSTAL_CODE]),
            city = asText(record[Columns.CITY]),
            prices = readPrices(record),
            isOpen24h = asText(record[Columns.OPEN_24H]).equals("oui", ignoreCase = true) ||
                record[Columns.OPEN_24H] == true,
            temporaryShortages = readTemporaryShortages(record),
        )
    }

    /**
     * Fuels reported as temporarily out of stock. Two columns carry this information, so
     * both are read and combined (a fuel flagged by either one counts):
     *  - `<fuel>_rupture_type` equal to "temporaire" (matched on its first letters, so a
     *    small spelling change such as "temporaire"/"temporary" still works);
     *  - the summary column `carburants_rupture_temporaire`, whose entries are the
     *    fuel names ("Gazole", "SP98"...), as a `;`-separated text or as a list.
     *
     * The feed's labels are exactly the column prefixes (case aside): "GPLc" ↔ "gplc".
     * "definitive" shortages (fuel no longer sold) are intentionally NOT reported.
     */
    private fun readTemporaryShortages(record: RawRecord): Map<FuelType, Long?> {
        val shortages = HashSet<FuelType>()

        for ((fuel, prefix) in fuelPrefixes) {
            val type = asText(record[prefix + Columns.SHORTAGE_TYPE_SUFFIX])
            if (type.startsWith(Columns.TEMPORARY_SHORTAGE_PREFIX, ignoreCase = true)) shortages.add(fuel)
        }

        val labels: List<String> = when (val summary = record[Columns.TEMPORARY_SHORTAGE_LIST]) {
            is String -> summary.split(';')
            is List<*> -> summary.map { it.toString() }
            else -> emptyList()
        }
        val fuelByLabel = fuelPrefixes.entries.associate { (fuel, prefix) -> prefix to fuel }
        for (label in labels) {
            fuelByLabel[label.trim().lowercase()]?.let { shortages.add(it) }
        }
        // Attach the start date of each shortage (null if the feed gives none or it is malformed).
        return shortages.associateWith { fuel ->
            parseInstantMillis(record[fuelPrefixes.getValue(fuel) + Columns.SHORTAGE_START_SUFFIX])
        }
    }

    /** Reads the price (and its update time) of every fuel that has a plausible price. */
    private fun readPrices(record: RawRecord): Map<FuelType, FuelPrice> {
        val prices = HashMap<FuelType, FuelPrice>(fuelPrefixes.size)
        for ((fuel, prefix) in fuelPrefixes) {
            val price = asDouble(record[prefix + Columns.PRICE_SUFFIX]) ?: continue
            if (price < MIN_PLAUSIBLE_PRICE || price > MAX_PLAUSIBLE_PRICE) continue
            val updatedAt = parseInstantMillis(record[prefix + Columns.UPDATE_SUFFIX])
            prices[fuel] = FuelPrice(price, updatedAt)
        }
        return prices
    }

    private data class Coordinates(val latitude: Double, val longitude: Double)

    /**
     * Position in degrees. Tries the `geom` object first, then the plain latitude/longitude
     * columns. In the original XML feed those are expressed in 1e-5 degrees
     * (e.g. 4885000 for 48.85°), so large values are rescaled.
     */
    private fun readCoordinates(record: RawRecord): Coordinates? {
        val geometry = record[Columns.GEOMETRY] as? Map<*, *>
        val candidates = listOf(
            asDouble(geometry?.get(Columns.GEOMETRY_LAT)) to asDouble(geometry?.get(Columns.GEOMETRY_LON)),
            asDouble(record[Columns.LATITUDE]) to asDouble(record[Columns.LONGITUDE]),
        )
        for ((rawLat, rawLon) in candidates) {
            if (rawLat == null || rawLon == null) continue
            val lat = rescaleIfNeeded(rawLat)
            val lon = rescaleIfNeeded(rawLon)
            val isValid = lat in -90.0..90.0 && lon in -MAX_DEGREES..MAX_DEGREES && !(lat == 0.0 && lon == 0.0)
            if (isValid) return Coordinates(lat, lon)
        }
        return null
    }

    private fun rescaleIfNeeded(value: Double): Double =
        if (kotlin.math.abs(value) > MAX_DEGREES) value / MICRODEGREE_DIVISOR else value

    /** Number, or text such as "1,789" / "1.789" (French decimal comma accepted). */
    private fun asDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }

    private fun asText(value: Any?): String = (value as? String)?.trim().orEmpty()

    /** ISO-8601 date-time with offset, e.g. "2026-10-08T07:12:00+00:00"; null if absent or malformed. */
    private fun parseInstantMillis(value: Any?): Long? {
        val text = value as? String ?: return null
        return try {
            OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
