package fr.opencarbu.data

import fr.opencarbu.domain.model.MapDisplaySettings

/** How [MapDisplaySettings] are written to, and read back from, the app's saved settings. */
object MapDisplaySettingsPreference {
    const val KEY_PLACE_NAME_OPACITY = "place_name_opacity"
    const val KEY_START_RADIUS_KM = "start_radius_km"
    const val KEY_STATION_SIZE_FACTOR = "station_size_factor"
    const val KEY_PLACE_NAME_SIZE_FACTOR = "place_name_size_factor"
    const val KEY_CENTER_ON_STATION = "center_on_station"

    /**
     * Settings for the saved values. A missing value (null), NaN or one out of range falls back
     * to the default (each value independently), so a corrupt entry can never break the map.
     */
    fun fromStored(
        placeNameOpacity: Float?,
        startRadiusKm: Int? = null,
        stationSizeFactor: Float? = null,
        placeNameSizeFactor: Float? = null,
        centerOnStation: Boolean? = null,
    ): MapDisplaySettings = MapDisplaySettings(
        placeNameOpacity = placeNameOpacity
            ?.takeIf { it in 0f..1f } // false for NaN too
            ?: MapDisplaySettings.DEFAULT_PLACE_NAME_OPACITY,
        startRadiusKm = startRadiusKm
            ?.takeIf { it in MapDisplaySettings.MIN_START_RADIUS_KM..MapDisplaySettings.MAX_START_RADIUS_KM }
            ?: MapDisplaySettings.DEFAULT_START_RADIUS_KM,
        stationSizeFactor = stationSizeFactor
            ?.takeIf { it in MapDisplaySettings.MIN_STATION_SIZE_FACTOR..MapDisplaySettings.MAX_STATION_SIZE_FACTOR }
            ?: MapDisplaySettings.DEFAULT_STATION_SIZE_FACTOR,
        placeNameSizeFactor = placeNameSizeFactor
            ?.takeIf { it in MapDisplaySettings.MIN_PLACE_NAME_SIZE_FACTOR..MapDisplaySettings.MAX_PLACE_NAME_SIZE_FACTOR }
            ?: MapDisplaySettings.DEFAULT_PLACE_NAME_SIZE_FACTOR,
        centerOnStation = centerOnStation ?: MapDisplaySettings.DEFAULT_CENTER_ON_STATION,
    )
}
