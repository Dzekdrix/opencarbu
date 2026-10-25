package fr.opencarbu.data

import fr.opencarbu.domain.model.NearestSettings

/** How [NearestSettings] are written to, and read back from, the app's saved settings. */
object NearestSettingsPreference {
    const val KEY_RADIUS_KM = "nearest_radius_km"
    const val KEY_CONSUMPTION = "nearest_consumption"
    const val KEY_TANK_LITRES = "nearest_tank_litres"

    /** Each value independently: missing (null), NaN or out of range falls back to its default. */
    fun fromStored(radiusKm: Int?, consumption: Float?, tankLitres: Int? = null): NearestSettings = NearestSettings(
        radiusKm = radiusKm?.takeIf { it in NearestSettings.MIN_RADIUS_KM..NearestSettings.MAX_RADIUS_KM }
            ?: NearestSettings.DEFAULT_RADIUS_KM,
        consumptionLitresPer100Km = consumption
            ?.takeIf { it in NearestSettings.MIN_CONSUMPTION..NearestSettings.MAX_CONSUMPTION }
            ?: NearestSettings.DEFAULT_CONSUMPTION,
        tankLitres = tankLitres
            ?.takeIf { it in NearestSettings.MIN_TANK_LITRES..NearestSettings.MAX_TANK_LITRES }
            ?: NearestSettings.DEFAULT_TANK_LITRES,
    )
}
