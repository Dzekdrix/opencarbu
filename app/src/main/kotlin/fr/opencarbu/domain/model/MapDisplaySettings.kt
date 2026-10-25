package fr.opencarbu.domain.model

/**
 * How the map is presented. Meant to become user settings (settings screen to come).
 *
 * @property placeNameOpacity opacity of the town and village names drawn ON TOP of the station
 *   dots, from 0.0 (invisible) to 1.0 (opaque). They are semi-transparent on purpose, so that
 *   the prices underneath stay readable.
 * @property startRadiusKm after the user is located, the map shows this radius around them,
 *   in kilometres.
 * @property placeNameSizeFactor multiplier for the size of the town and village names (1.0 = standard).
 * @property stationSizeFactor multiplier for the size of the station dots, their shortage badges
 *   and their price text: 1.0 = the standard size, 2.0 = twice as big.
 * @property centerOnStation when a station is tapped on the map, the camera glides to put it in the
 *   middle of the map part above the station sheet. Off = the map stays still (smoother on slow phones).
 */
data class MapDisplaySettings(
    val placeNameOpacity: Float = DEFAULT_PLACE_NAME_OPACITY,
    val startRadiusKm: Int = DEFAULT_START_RADIUS_KM,
    val stationSizeFactor: Float = DEFAULT_STATION_SIZE_FACTOR,
    val placeNameSizeFactor: Float = DEFAULT_PLACE_NAME_SIZE_FACTOR,
    val centerOnStation: Boolean = DEFAULT_CENTER_ON_STATION,
) {
    init {
        require(placeNameOpacity in 0f..1f) { "Opacity must be between 0 and 1" }
        require(placeNameSizeFactor in MIN_PLACE_NAME_SIZE_FACTOR..MAX_PLACE_NAME_SIZE_FACTOR) {
            "Place-name size factor must be between $MIN_PLACE_NAME_SIZE_FACTOR and $MAX_PLACE_NAME_SIZE_FACTOR"
        }
        require(stationSizeFactor in MIN_STATION_SIZE_FACTOR..MAX_STATION_SIZE_FACTOR) {
            "Size factor must be between $MIN_STATION_SIZE_FACTOR and $MAX_STATION_SIZE_FACTOR"
        }
        require(startRadiusKm in MIN_START_RADIUS_KM..MAX_START_RADIUS_KM) {
            "Radius must be between $MIN_START_RADIUS_KM and $MAX_START_RADIUS_KM km"
        }
    }

    companion object {
        const val DEFAULT_CENTER_ON_STATION = true
        const val DEFAULT_PLACE_NAME_OPACITY = 0.8f
        const val DEFAULT_START_RADIUS_KM = 15
        const val MIN_START_RADIUS_KM = 5
        const val MAX_START_RADIUS_KM = 60
        const val DEFAULT_STATION_SIZE_FACTOR = 1.0f
        const val MIN_STATION_SIZE_FACTOR = 0.5f
        const val MAX_STATION_SIZE_FACTOR = 2.0f
        const val DEFAULT_PLACE_NAME_SIZE_FACTOR = 1.0f
        const val MIN_PLACE_NAME_SIZE_FACTOR = 0.5f
        const val MAX_PLACE_NAME_SIZE_FACTOR = 2.0f
    }
}
