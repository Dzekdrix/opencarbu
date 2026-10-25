package fr.opencarbu.domain.details

import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.pricing.PricingSettings
import fr.opencarbu.domain.pricing.ViewportPricing

/** How one fuel line of the station sheet must be presented. */
enum class FuelRowStatus {
    /** A price updated recently enough to be trusted. */
    CURRENT,

    /** A price older than the allowed age (or of unknown age): shown, but flagged. */
    OLD_PRICE,

    /** The station reports a recent temporary shortage of this fuel. */
    SHORTAGE,
}

/**
 * One fuel line of the station sheet.
 *
 * @property euroPerLitre the price, or null when none is reported (typically a shortage).
 * @property updatedAtEpochMillis when the price was last updated, null if unknown.
 * @property shortageSinceEpochMillis for [FuelRowStatus.SHORTAGE]: when it began, null if unknown.
 */
data class FuelRow(
    val fuel: FuelType,
    val euroPerLitre: Double?,
    val updatedAtEpochMillis: Long?,
    val shortageSinceEpochMillis: Long?,
    val status: FuelRowStatus,
)

/**
 * Everything the station sheet displays.
 *
 * @property fuels every fuel the station sells or reports as temporarily out, in the order of [FuelType].
 * @property straightLineKm distance from the user in a straight line, or null if the user's
 *   position is unknown. This is NOT the road distance (see the architecture notes).
 */
data class StationDetails(
    val station: Station,
    val fuels: List<FuelRow>,
    val straightLineKm: Double?,
)

/** Builds [StationDetails]. Pure function: easy to test, no Android involved. */
object StationDetailsBuilder {

    fun build(
        station: Station,
        userLocation: GeoPoint?,
        nowEpochMillis: Long,
        settings: PricingSettings = PricingSettings(),
    ): StationDetails {
        val rows = FuelType.entries.mapNotNull { fuel ->
            rowFor(station, fuel, nowEpochMillis, settings)
        }
        val distance = userLocation?.straightLineKmTo(GeoPoint(station.latitude, station.longitude))
        return StationDetails(station, rows, distance)
    }

    /** Same rules as the map dots (see [ViewportPricing]), so sheet and map never disagree. */
    private fun rowFor(station: Station, fuel: FuelType, now: Long, settings: PricingSettings): FuelRow? {
        val price = station.prices[fuel]
        if (ViewportPricing.hasActiveShortage(station, fuel, now, settings.maxShortageAgeMillis)) {
            return FuelRow(
                fuel = fuel,
                euroPerLitre = price?.euroPerLitre,
                updatedAtEpochMillis = price?.updatedAtEpochMillis,
                shortageSinceEpochMillis = station.temporaryShortages[fuel],
                status = FuelRowStatus.SHORTAGE,
            )
        }
        // No active shortage and no price: the fuel is not sold here (an old shortage flag is ignored).
        price ?: return null
        val updatedAt = price.updatedAtEpochMillis
        val isOld = updatedAt == null || now - updatedAt > settings.maxPriceAgeMillis
        return FuelRow(
            fuel = fuel,
            euroPerLitre = price.euroPerLitre,
            updatedAtEpochMillis = updatedAt,
            shortageSinceEpochMillis = null,
            status = if (isOld) FuelRowStatus.OLD_PRICE else FuelRowStatus.CURRENT,
        )
    }
}
