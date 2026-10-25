package fr.opencarbu.domain.model

/**
 * Settings of the "nearest stations" list.
 *
 * @property radiusKm stations farther than this (in a straight line) from the user are not listed.
 * @property consumptionLitresPer100Km average consumption of the user's vehicle, to turn the
 *   distance to a station into the fuel burnt (and so paid) to get there.
 * @property tankLitres size of a "fill-up", to tell what filling the tank at a station costs.
 */
data class NearestSettings(
    val radiusKm: Int = DEFAULT_RADIUS_KM,
    val consumptionLitresPer100Km: Float = DEFAULT_CONSUMPTION,
    val tankLitres: Int = DEFAULT_TANK_LITRES,
) {
    init {
        require(radiusKm in MIN_RADIUS_KM..MAX_RADIUS_KM) { "Radius must be between $MIN_RADIUS_KM and $MAX_RADIUS_KM km" }
        require(consumptionLitresPer100Km in MIN_CONSUMPTION..MAX_CONSUMPTION) {
            "Consumption must be between $MIN_CONSUMPTION and $MAX_CONSUMPTION L/100 km"
        }
        require(tankLitres in MIN_TANK_LITRES..MAX_TANK_LITRES) {
            "Tank size must be between $MIN_TANK_LITRES and $MAX_TANK_LITRES L"
        }
    }

    companion object {
        const val DEFAULT_RADIUS_KM = 30
        const val MIN_RADIUS_KM = 5
        const val MAX_RADIUS_KM = 100

        const val DEFAULT_CONSUMPTION = 6f
        const val MIN_CONSUMPTION = 2f
        const val MAX_CONSUMPTION = 20f

        const val DEFAULT_TANK_LITRES = 45
        const val MIN_TANK_LITRES = 5
        const val MAX_TANK_LITRES = 120
    }
}
