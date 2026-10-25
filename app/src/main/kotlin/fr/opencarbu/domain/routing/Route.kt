package fr.opencarbu.domain.routing

import fr.opencarbu.domain.model.GeoPoint
import java.util.Locale

/** A driving route summary: how far and how long. */
data class RouteResult(val distanceKm: Double, val durationMinutes: Int)

/** Builds the request for an OSRM-compatible server. */
object OsrmRequest {
    /** `<base>/route/v1/driving/lon,lat;lon,lat?overview=false...`: GeoJSON order, longitude first. */
    fun url(baseUrl: String, from: GeoPoint, to: GeoPoint): String {
        fun coordinates(p: GeoPoint) = String.format(Locale.ROOT, "%.5f,%.5f", p.longitude, p.latitude)
        return baseUrl.trim().trimEnd('/') + "/route/v1/driving/" + coordinates(from) + ";" + coordinates(to) +
            "?overview=false&alternatives=false&steps=false"
    }
}

/** Builds the "table" request: distances from one origin to many destinations in a single call. */
object OsrmTableRequest {
    /** Most coordinates (origin included) the OSRM default configuration accepts in one table. */
    const val MAX_COORDINATES = 100

    /**
     * `<base>/table/v1/driving/origin;dest1;dest2...?sources=0&annotations=distance,duration`.
     * The answer's first row is the origin to every coordinate, itself first (distance 0).
     */
    fun url(baseUrl: String, origin: GeoPoint, destinations: List<GeoPoint>): String {
        fun coordinates(p: GeoPoint) = String.format(Locale.ROOT, "%.5f,%.5f", p.longitude, p.latitude)
        return baseUrl.trim().trimEnd('/') + "/table/v1/driving/" +
            (listOf(origin) + destinations).joinToString(";", transform = ::coordinates) +
            "?sources=0&annotations=distance,duration"
    }
}

/** Reads an OSRM `table` answer, already turned into plain Maps/Lists by a JSON reader. */
object OsrmTableParser {
    /**
     * One [RouteResult] per destination (null where no route exists), or null if the answer is
     * not a usable table for [destinationCount] destinations.
     */
    fun parse(root: Any?, destinationCount: Int): List<RouteResult?>? {
        val map = root as? Map<*, *> ?: return null
        if (map["code"] != "Ok") return null
        val distances = ((map["distances"] as? List<*>)?.firstOrNull() as? List<*>) ?: return null
        val durations = ((map["durations"] as? List<*>)?.firstOrNull() as? List<*>) ?: return null
        // Entry 0 is the origin itself.
        if (distances.size != destinationCount + 1 || durations.size != destinationCount + 1) return null
        return List(destinationCount) { index ->
            val metres = (distances[index + 1] as? Number)?.toDouble()?.takeIf { it >= 0 && it.isFinite() }
            val seconds = (durations[index + 1] as? Number)?.toDouble()?.takeIf { it >= 0 && it.isFinite() }
            if (metres == null || seconds == null) null
            else RouteResult(distanceKm = metres / 1000.0, durationMinutes = Math.round(seconds / 60.0).toInt())
        }
    }
}

/**
 * Reads an OSRM `route` answer, already turned into plain Maps/Lists by a JSON reader
 * (`{"code":"Ok","routes":[{"distance":<metres>,"duration":<seconds>}]}`).
 */
object OsrmResponseParser {
    /** The first route, or null when the server found none or the answer is not an OSRM one. */
    fun parse(root: Any?): RouteResult? {
        val map = root as? Map<*, *> ?: return null
        if (map["code"] != "Ok") return null
        val route = (map["routes"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
        val metres = (route["distance"] as? Number)?.toDouble()?.takeIf { it >= 0 && it.isFinite() } ?: return null
        val seconds = (route["duration"] as? Number)?.toDouble()?.takeIf { it >= 0 && it.isFinite() } ?: return null
        return RouteResult(distanceKm = metres / 1000.0, durationMinutes = Math.round(seconds / 60.0).toInt())
    }

    /** The server's own error code ("NoRoute", "InvalidQuery"...), for the error message. */
    fun errorCode(root: Any?): String? = ((root as? Map<*, *>)?.get("code") as? String)?.takeIf { it != "Ok" }
}
