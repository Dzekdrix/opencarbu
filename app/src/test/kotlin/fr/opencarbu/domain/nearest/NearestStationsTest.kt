package fr.opencarbu.domain.nearest

import fr.opencarbu.domain.model.FuelPrice
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.routing.RouteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NearestStationsTest {
    private val origin = GeoPoint(48.0, 2.0)

    /** [dLat] degrees north of the origin: 0.01° is about 1.11 km. */
    private fun station(id: Long, dLat: Double, price: Double? = 1.8, referenceOnly: Boolean = false) = Station(
        id = id, latitude = 48.0 + dLat, longitude = 2.0, address = "", postalCode = "", city = "",
        prices = if (price == null) emptyMap() else mapOf(FuelType.E10 to FuelPrice(price, null)),
        isOpen24h = false, isReferenceOnly = referenceOnly,
    )

    @Test
    fun keepsStationsInRadiusWithAPriceNearestFirst() {
        val stations = listOf(
            station(1, 0.20),                  // ~22 km
            station(2, 0.05),                  // ~5.6 km
            station(3, 0.50),                  // ~55 km: too far
            station(4, 0.02, price = null),    // does not sell E10
            station(5, 0.01, referenceOnly = true),
        )
        val found = NearestStations.candidates(stations, FuelType.E10, origin, 30.0)
        assertEquals(listOf(2L, 1L), found.map { it.station.id })
        assertTrue(found[0].straightKm in 5.0..6.0)
    }

    @Test
    fun neverReturnsMoreThanTheCap() {
        val many = (1..NearestStations.MAX_CANDIDATES + 50).map { station(it.toLong(), it * 0.0001) }
        val found = NearestStations.candidates(many, FuelType.E10, origin, 30.0)
        assertEquals(NearestStations.MAX_CANDIDATES, found.size)
        assertEquals(1L, found.first().station.id)
    }

    @Test
    fun tripCostIsFuelBurntTimesPrice() {
        // 20 km at 6 L/100 km = 1.2 L, at 1.5 €/L = 1.8 €
        assertEquals(1.8, NearestStations.tripCost(20.0, 1.5, 6.0), 1e-9)
    }

    @Test
    fun roadDistancesReplaceStraightOnesAndReorderTheList() {
        val candidates = NearestStations.candidates(listOf(station(1, 0.05), station(2, 0.10)), FuelType.E10, origin, 30.0)
        // Station 1 is nearer as the crow flies but far by road.
        val roads = listOf(RouteResult(40.0, 50), RouteResult(15.0, 20))
        val entries = NearestStations.rank(candidates, roads, 5.0)
        assertEquals(listOf(2L, 1L), entries.map { it.station.id })
        assertTrue(entries.all { it.isRoad })
        assertEquals(15.0, entries[0].distanceKm, 1e-9)
        assertEquals(15.0 * 5.0 / 100 * 1.8, entries[0].tripCostEuro, 1e-9)
    }

    @Test
    fun missingRoadFallsBackToStraightLine() {
        val candidates = NearestStations.candidates(listOf(station(1, 0.05), station(2, 0.10)), FuelType.E10, origin, 30.0)
        val partial = NearestStations.rank(candidates, listOf(null, RouteResult(30.0, 30)), 6.0)
        assertEquals(false, partial.first { it.station.id == 1L }.isRoad)
        assertEquals(true, partial.first { it.station.id == 2L }.isRoad)
        val none = NearestStations.rank(candidates, null, 6.0)
        assertTrue(none.none { it.isRoad })
        assertEquals(listOf(1L, 2L), none.map { it.station.id })
    }

    @Test
    fun roadDistancesAreAskedFiftyAtATimeNearestFirst() {
        val many = (1..120).map { station(it.toLong(), it * 0.001) }
        val candidates = NearestStations.candidates(many, FuelType.E10, origin, 30.0)
        val batches = NearestStations.roadBatches(candidates)
        assertEquals(listOf(50, 50, 20), batches.map { it.size })
        assertEquals((1L..50L).toList(), batches.first().map { it.station.id })
        assertEquals((1L..120L).toList(), batches.flatten().map { it.station.id })
    }

    @Test
    fun fewStationsAreOneBatchAndNoneIsNothing() {
        val few = (1..4).map { station(it.toLong(), it * 0.005) }
        val candidates = NearestStations.candidates(few, FuelType.E10, origin, 30.0)
        assertEquals(listOf(4), NearestStations.roadBatches(candidates).map { it.size })
        assertEquals(emptyList(), NearestStations.roadBatches(emptyList()))
    }

    @Test
    fun rowsNotYetAnsweredKeepTheirStraightDistance() {
        val many = (1..15).map { station(it.toLong(), it * 0.005) }
        val candidates = NearestStations.candidates(many, FuelType.E10, origin, 30.0)
        val roads = candidates.take(10).map { RouteResult(it.straightKm * 1.3, 10) }
        val entries = NearestStations.rank(candidates, roads, 6.0)
        assertEquals(15, entries.size)
        assertEquals(10, entries.count { it.isRoad })
        assertTrue(entries.filter { !it.isRoad }.all { it.station.id > 10 })
    }

    @Test
    fun fillUpCostIsTankLitresTimesPrice() {
        val candidates = NearestStations.candidates(listOf(station(1, 0.05, price = 1.8)), FuelType.E10, origin, 30.0)
        val default = NearestStations.rank(candidates, null, 6.0).single()
        assertEquals(45 * 1.8, default.fillUpCostEuro, 1e-9) // 45 L by default
        val big = NearestStations.rank(candidates, null, 6.0, tankLitres = 120).single()
        assertEquals(120 * 1.8, big.fillUpCostEuro, 1e-9)
    }

    @Test
    fun totalIsTheSumOfTheRoundedAmounts() {
        val station = Station(1, 0.0, 0.0, "", "", "", emptyMap(), false)
        // 10.004 -> 10.00 and 5.004 -> 5.00: the total is 15.00, not 15.008 rounded.
        val entry = NearestEntry(station, 1.0, false, 1.0, tripCostEuro = 5.004, fillUpCostEuro = 10.004)
        assertEquals(15.0, entry.totalCostEuro, 1e-9)
        // 10.006 -> 10.01 and 5.006 -> 5.01: 15.02, where the exact sum (15.012) would show 15.01.
        val half = NearestEntry(station, 1.0, false, 1.0, tripCostEuro = 5.006, fillUpCostEuro = 10.006)
        assertEquals(15.02, half.totalCostEuro, 1e-9)
    }

    @Test
    fun journeyCostAddsTheTripAndTheFillUp() {
        // 20 km at 6 L/100 km = 1.2 L at 1.50 = 1.80 for the trip; 40 L at 1.50 = 60.00 for the fill-up.
        val cost = JourneyCost.of(distanceKm = 20.0, euroPerLitre = 1.5, consumptionLitresPer100Km = 6.0, tankLitres = 40)
        assertEquals(1.8, cost.tripCostEuro, 1e-9)
        assertEquals(60.0, cost.fillUpCostEuro, 1e-9)
        assertEquals(61.8, cost.totalCostEuro, 1e-9)
    }
}
