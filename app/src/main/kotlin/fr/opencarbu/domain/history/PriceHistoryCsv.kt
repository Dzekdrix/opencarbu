package fr.opencarbu.domain.history

import fr.opencarbu.domain.model.FuelType
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The export format of the price history: CSV, UTF-8, one line per price change, which any
 * spreadsheet or script reads.
 *
 *     station_id,fuel,changed_at,price_eur
 *     1000001,DIESEL,2026-10-15T08:30:00Z,1.789
 *
 * `fuel` is the app's fuel name (DIESEL, SP95, E10, SP98, E85, LPG), `changed_at` the moment the
 * station reported the price (ISO 8601, UTC) and `price_eur` euros per litre with a decimal point.
 * The station id is the one of the open-data feed (the same id in the "id" column of the
 * Ministry's dataset).
 */
object PriceHistoryCsv {
    const val HEADER = "station_id,fuel,changed_at,price_eur"

    fun line(stationId: Long, fuel: FuelType, atEpochMillis: Long, milliEuros: Int): String =
        "$stationId,${fuel.name},${Instant.ofEpochMilli(atEpochMillis).truncatedTo(ChronoUnit.SECONDS)},${euros(milliEuros)}"

    /** True if [line] is the header of an exported history (extra columns after the first four are tolerated). */
    fun isHeader(line: String): Boolean {
        val columns = line.trim().removePrefix("\uFEFF").split(',').map { it.trim().lowercase() }
        return columns.size >= 4 && columns.take(4) == HEADER.split(',')
    }

    /**
     * Reads one line of an exported history, or returns null if it cannot be one: fewer than four columns,
     * an unknown fuel, an unreadable date or station, a price outside 0,100 to 10,000 €/L. Lenient on
     * purpose, so that a file edited in a spreadsheet still loads: fuel in any case, spaces around the values,
     * any ISO 8601 date with a zone, extra columns ignored.
     */
    fun parseLine(line: String): PriceChange? {
        val columns = line.split(',')
        if (columns.size < 4) return null
        val station = columns[0].trim().toLongOrNull() ?: return null
        val fuel = FuelType.entries.firstOrNull { it.name.equals(columns[1].trim(), ignoreCase = true) } ?: return null
        val at = parseInstant(columns[2].trim()) ?: return null
        if (at < MIN_EPOCH_MILLIS) return null
        val milli = try {
            java.math.BigDecimal(columns[3].trim().replace(',', '.')).movePointRight(3)
                .setScale(0, java.math.RoundingMode.HALF_UP).intValueExact()
        } catch (e: ArithmeticException) {
            return null
        } catch (e: NumberFormatException) {
            return null
        }
        if (milli !in MIN_MILLI_EUROS..MAX_MILLI_EUROS) return null
        return PriceChange(station, fuel, at, milli)
    }

    private fun parseInstant(text: String): Long? = try {
        Instant.parse(text).toEpochMilli()
    } catch (e: java.time.format.DateTimeParseException) {
        try {
            java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (e2: java.time.format.DateTimeParseException) {
            null
        }
    }

    /** 1 January 2000: nothing earlier can be a price of this feed. */
    private const val MIN_EPOCH_MILLIS = 946_684_800_000L
    private const val MIN_MILLI_EUROS = 100
    private const val MAX_MILLI_EUROS = 10_000

    /** 1789 -> "1.789" (never a comma, whatever the phone's language). */
    fun euros(milliEuros: Int): String {
        val sign = if (milliEuros < 0) "-" else ""
        val abs = Math.abs(milliEuros)
        return "$sign${abs / 1000}.${(abs % 1000).toString().padStart(3, '0')}"
    }
}
