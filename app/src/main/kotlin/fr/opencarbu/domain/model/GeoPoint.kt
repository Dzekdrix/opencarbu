package fr.opencarbu.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A position on Earth, in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double) {

    /**
     * Distance to [other] in kilometres, in a straight line over the Earth's surface ("as the crow
     * flies", haversine formula). Always shorter than the road distance.
     */
    fun straightLineKmTo(other: GeoPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    private companion object {
        const val EARTH_RADIUS_KM = 6371.0
    }
}
