package fr.opencarbu.domain.model

import kotlin.math.abs
import kotlin.math.cos

/**
 * A geographic rectangle expressed in degrees, typically the part of the map currently visible.
 *
 * Limitation: boxes crossing the antimeridian (longitude ±180°) are not supported.
 * This is irrelevant for France and keeps [contains] a handful of comparisons.
 */
data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    /** True if the point lies inside the box (borders included). */
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in south..north && longitude in west..east

    companion object {
        /** Length of one degree of latitude on a spherical Earth of radius 6371 km. */
        private const val KM_PER_DEGREE_OF_LATITUDE = 111.195

        /**
         * The smallest box that contains the disc of [radiusKm] kilometres around [center].
         *
         * A degree of longitude gets shorter away from the equator (it is multiplied by the
         * cosine of the latitude), so the box is wider in degrees than it is tall. The result
         * is clamped to the valid ranges, which only matters close to the poles.
         */
        fun around(center: GeoPoint, radiusKm: Double): BoundingBox {
            require(radiusKm > 0) { "The radius must be positive" }
            val latitudeDelta = radiusKm / KM_PER_DEGREE_OF_LATITUDE
            val longitudeScale = cos(Math.toRadians(center.latitude))
            // Next to a pole the scale tends to 0: cover every longitude instead of dividing by ~0.
            val longitudeDelta =
                if (abs(longitudeScale) < 1e-6) 180.0 else latitudeDelta / longitudeScale

            return BoundingBox(
                south = (center.latitude - latitudeDelta).coerceAtLeast(-90.0),
                west = (center.longitude - longitudeDelta).coerceAtLeast(-180.0),
                north = (center.latitude + latitudeDelta).coerceAtMost(90.0),
                east = (center.longitude + longitudeDelta).coerceAtMost(180.0),
            )
        }
    }
}
