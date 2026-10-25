package fr.opencarbu.domain.nearest

import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.NearestSettings
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.routing.RouteResult

/** A station near the user that sells the selected fuel, with its straight-line distance. */
class NearbyStation(val station: Station, val straightKm: Double, val euroPerLitre: Double) {
    val position: GeoPoint get() = GeoPoint(station.latitude, station.longitude)
}

/**
 * One line of the list.
 * @property distanceKm by road when [isRoad], else in a straight line (routing off or failed).
 * @property tripCostEuro cost of the fuel burnt to get to the station, one way.
 * @property fillUpCostEuro what a full tank ([tankLitres]) costs at this station's price.
 */
class NearestEntry(
    val station: Station,
    val distanceKm: Double,
    val isRoad: Boolean,
    val euroPerLitre: Double,
    val tripCostEuro: Double,
    val fillUpCostEuro: Double,
) {
    /** Fill-up plus trip, see [JourneyCost.totalCostEuro]. */
    val totalCostEuro: Double
        get() = JourneyCost(tripCostEuro, fillUpCostEuro).totalCostEuro
}

/** What going to a station and filling up there costs: the trip (one way) and a full tank. */
class JourneyCost(val tripCostEuro: Double, val fillUpCostEuro: Double) {
    /**
     * Fill-up plus trip. Each part is rounded to the cent first, as it is displayed, so the total shown
     * is always the sum of the two amounts shown (never a cent off).
     */
    val totalCostEuro: Double
        get() = (Math.round(fillUpCostEuro * 100) + Math.round(tripCostEuro * 100)) / 100.0

    companion object {
        fun of(distanceKm: Double, euroPerLitre: Double, consumptionLitresPer100Km: Double, tankLitres: Int) =
            JourneyCost(
                tripCostEuro = NearestStations.tripCost(distanceKm, euroPerLitre, consumptionLitresPer100Km),
                fillUpCostEuro = tankLitres * euroPerLitre,
            )
    }
}

/** Picks and sorts the stations of the "nearest stations" list. */
object NearestStations {
    /**
     * Never more than this many stations are listed (the nearest ones): one routing request
     * handles about a hundred of them, and the list would be too long to read anyway.
     */
    const val MAX_CANDIDATES = 300

    /**
     * Road distances are asked for in order of straight-line distance, [ROAD_BATCH_SIZE] stations per request: up to
     * 50 stations answer in a single request when the page opens, a longer list takes one more request per 50. The
     * public routing server needs about 10 s per request whatever the number of destinations, so few big requests
     * fill the list much faster than many small ones.
     */
    const val ROAD_BATCH_SIZE = 50

    /** [candidates] cut into the groups road distances are asked in, nearest first. */
    fun roadBatches(candidates: List<NearbyStation>): List<List<NearbyStation>> = candidates.chunked(ROAD_BATCH_SIZE)

    /**
     * Stations that sell [fuel] (a price is known) within [radiusKm] of [origin] in a straight
     * line, nearest first, at most [MAX_CANDIDATES]. Stations known only from the reference file
     * have no price and are left out.
     */
    fun candidates(stations: List<Station>, fuel: FuelType, origin: GeoPoint, radiusKm: Double): List<NearbyStation> {
        val box = BoundingBox.around(origin, radiusKm) // cheap rejection before the trigonometry
        val found = ArrayList<NearbyStation>()
        for (station in stations) {
            if (station.isReferenceOnly) continue
            val price = station.prices[fuel]?.euroPerLitre ?: continue
            if (!box.contains(station.latitude, station.longitude)) continue
            val straight = origin.straightLineKmTo(GeoPoint(station.latitude, station.longitude))
            if (straight <= radiusKm) found.add(NearbyStation(station, straight, price))
        }
        found.sortBy { it.straightKm }
        return if (found.size > MAX_CANDIDATES) found.subList(0, MAX_CANDIDATES).toList() else found
    }

    /**
     * The list lines, nearest first. [roads] has one entry per candidate (same order) when road
     * distances are known; it may be shorter than [candidates] (batches still arriving, see [roadBatches]), and a missing or
     * null entry falls back to the straight-line distance.
     */
    fun rank(
        candidates: List<NearbyStation>,
        roads: List<RouteResult?>?,
        consumptionLitresPer100Km: Double,
        tankLitres: Int = NearestSettings.DEFAULT_TANK_LITRES,
    ): List<NearestEntry> =
        candidates.mapIndexed { index, candidate ->
            val road = roads?.getOrNull(index)
            val distance = road?.distanceKm ?: candidate.straightKm
            NearestEntry(
                station = candidate.station,
                distanceKm = distance,
                isRoad = road != null,
                euroPerLitre = candidate.euroPerLitre,
                tripCostEuro = tripCost(distance, candidate.euroPerLitre, consumptionLitresPer100Km),
                fillUpCostEuro = tankLitres * candidate.euroPerLitre,
            )
        }.sortedBy { it.distanceKm }

    /** Fuel burnt over [distanceKm] at [consumptionLitresPer100Km], paid at [euroPerLitre]. */
    fun tripCost(distanceKm: Double, euroPerLitre: Double, consumptionLitresPer100Km: Double): Double =
        distanceKm * consumptionLitresPer100Km / 100.0 * euroPerLitre
}
