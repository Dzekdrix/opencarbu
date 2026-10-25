package fr.opencarbu.domain.nearest

import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.routing.RouteResult

/**
 * Remembers the road distance from the user to each station (by station id), so reopening the
 * nearest-stations list does not query the routing server again. Valid while the user stays
 * within [toleranceKm] (100 m) of the position the distances were computed from, and for the same
 * server. A station with no route is remembered too (a null result), to not ask again.
 *
 * Not thread-safe: use it from one thread (the main one).
 */
class RoadDistanceCache(private val toleranceKm: Double = DEFAULT_TOLERANCE_KM) {
    private var anchor: GeoPoint? = null
    private var serverUrl: String? = null
    private val results = HashMap<Long, RouteResult?>()

    /** True when what is remembered was computed for (about) [origin] and [server]. */
    private fun matches(origin: GeoPoint, server: String): Boolean {
        val a = anchor ?: return false
        return server == serverUrl && a.straightLineKmTo(origin) <= toleranceKm
    }

    /**
     * The distances of [ids], in order, if all are remembered for (about) [origin]; else null.
     * (A null element means "remembered: no route".)
     */
    fun all(origin: GeoPoint, server: String, ids: List<Long>): List<RouteResult?>? {
        if (!matches(origin, server) || !ids.all { results.containsKey(it) }) return null
        return ids.map { results[it] }
    }

    /** The ids among [ids] that are not remembered for (about) [origin]. */
    fun missing(origin: GeoPoint, server: String, ids: List<Long>): List<Long> =
        if (matches(origin, server)) ids.filter { !results.containsKey(it) } else ids

    /**
     * Remembers [distances] for [origin]. If the user has moved since the previous ones, those are
     * forgotten first. The anchor stays the first position of a series, so small steps cannot add up.
     */
    fun put(origin: GeoPoint, server: String, distances: Map<Long, RouteResult?>) {
        if (!matches(origin, server)) {
            results.clear()
            anchor = origin
            serverUrl = server
        }
        results.putAll(distances)
    }

    companion object {
        const val DEFAULT_TOLERANCE_KM = 0.1
    }
}
