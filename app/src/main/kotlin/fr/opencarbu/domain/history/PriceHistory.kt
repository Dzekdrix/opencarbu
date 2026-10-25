package fr.opencarbu.domain.history

import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.Station

/**
 * One price seen for a fuel at a station, at the moment the station reported it.
 * [milliEuros] is the price in thousandths of a euro (1,789 € = 1789): an exact whole number,
 * small to store and free of rounding surprises when two prices are compared.
 */
data class PriceChange(val stationId: Long, val fuel: FuelType, val atEpochMillis: Long, val milliEuros: Int)

/** A point of a price curve. */
data class PricePoint(val atEpochMillis: Long, val milliEuros: Int)

/** The last price recorded for a station and a fuel. */
data class RecordedPrice(val atEpochMillis: Long, val milliEuros: Int)

/**
 * What to keep of a downloaded feed. Only the CHANGES are stored, to keep the history small:
 * a price is recorded when it differs from the last recorded one for the same station and fuel
 * (or when there is none yet), and when the station's own update date is newer.
 */
object PriceHistoryRules {

    /** Key of a (station, fuel) pair in the map of last recorded prices. */
    fun key(stationId: Long, fuel: FuelType): Long = stationId * FUEL_SLOTS + fuel.ordinal

    fun toMilliEuros(euroPerLitre: Double): Int = Math.round(euroPerLitre * 1000).toInt()

    /**
     * The changes to record for [stations], given the last recorded prices ([lastRecorded], by [key]).
     * A price with no update date is ignored (it cannot be placed in time). When the date is not
     * newer than the recorded one, nothing is recorded either: the feed shows an older state.
     */
    fun changesToRecord(stations: List<Station>, lastRecorded: Map<Long, RecordedPrice>): List<PriceChange> {
        val changes = ArrayList<PriceChange>()
        for (station in stations) {
            for ((fuel, price) in station.prices) {
                val at = price.updatedAtEpochMillis ?: continue
                val milli = toMilliEuros(price.euroPerLitre)
                val last = lastRecorded[key(station.id, fuel)]
                if (last == null || (at > last.atEpochMillis && milli != last.milliEuros)) {
                    changes.add(PriceChange(station.id, fuel, at, milli))
                }
            }
        }
        return changes
    }

    /**
     * The curve to draw from [changes] (one station and fuel, any order): the price in force at
     * [fromEpochMillis] (the last change before it), then every change up to [nowEpochMillis], where
     * the last price is extended. Empty when no price is known in the window.
     */
    fun curve(changes: List<PricePoint>, fromEpochMillis: Long, nowEpochMillis: Long): List<PricePoint> {
        val sorted = changes.sortedBy { it.atEpochMillis }
        val before = sorted.lastOrNull { it.atEpochMillis <= fromEpochMillis }
        val inside = sorted.filter { it.atEpochMillis > fromEpochMillis && it.atEpochMillis <= nowEpochMillis }
        val points = ArrayList<PricePoint>()
        if (before != null) points.add(PricePoint(fromEpochMillis, before.milliEuros))
        points.addAll(inside)
        val last = points.lastOrNull() ?: return emptyList()
        if (last.atEpochMillis < nowEpochMillis) points.add(PricePoint(nowEpochMillis, last.milliEuros))
        return points
    }

    /** Number of slots per station in [key]; larger than the number of fuel types. */
    const val FUEL_SLOTS = 16
}
