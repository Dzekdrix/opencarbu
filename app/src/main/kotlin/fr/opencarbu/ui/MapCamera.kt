package fr.opencarbu.ui

/** Position of the map camera: centre in degrees and zoom level (0 = whole world, ~15 = streets). */
data class MapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
) {
    companion object {
        /** Mainland France, fully visible on a phone screen. */
        val FRANCE = MapCamera(latitude = 46.6, longitude = 2.5, zoom = 5.2)
    }
}
