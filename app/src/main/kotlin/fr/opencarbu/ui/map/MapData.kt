package fr.opencarbu.ui.map

import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.pricing.NationalPriceScale
import fr.opencarbu.domain.pricing.PricingSettings
import fr.opencarbu.domain.pricing.ViewportPricing

/** What the map must display right now. */
class MapData(
    /** GeoJSON text of the dots, see [StationGeoJson]. */
    val geoJson: String = StationGeoJson.EMPTY,
    /** Number of dots in [geoJson]. */
    val shownCount: Int = 0,
)

/** Produces the [MapData] for a map position and a fuel: the whole pipeline in one pure function. */
object MapDataFactory {

    /**
     * @param stations every known station.
     * @param fuel the fuel selected by the user.
     * @param visibleArea the part of the map currently on screen.
     * @param nowEpochMillis the current time (to judge how old prices are).
     * @param formatPrice price → label text, in the user's locale.
     * @param settings the age thresholds for prices and shortages.
     * @param scale the national price scale for [fuel] (see [ViewportPricing.nationalScale]); built
     *   from [stations] when null. Pass a remembered one to avoid sorting ~10 000 prices on every pan.
     */
    fun build(
        stations: List<Station>,
        fuel: FuelType,
        visibleArea: BoundingBox,
        nowEpochMillis: Long,
        formatPrice: (Double) -> String,
        settings: PricingSettings = PricingSettings(),
        scale: NationalPriceScale? = ViewportPricing.nationalScale(
            stations, fuel, nowEpochMillis, settings.maxPriceAgeMillis, settings.maxShortageAgeMillis,
        ),
    ): MapData {
        val dots = ViewportPricing.compute(
            stations, fuel, visibleArea, nowEpochMillis,
            maxPriceAgeMillis = settings.maxPriceAgeMillis,
            maxShortageAgeMillis = settings.maxShortageAgeMillis,
            scale = scale,
        )
        return MapData(StationGeoJson.build(dots, formatPrice), dots.size)
    }
}
