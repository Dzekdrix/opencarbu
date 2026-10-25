package fr.opencarbu.domain

import fr.opencarbu.domain.details.FuelRowStatus
import fr.opencarbu.domain.details.StationDetailsBuilder
import fr.opencarbu.domain.model.FuelPrice
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationDetailsBuilderTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun station(
        prices: Map<FuelType, FuelPrice>,
        shortages: Map<FuelType, Long?> = emptyMap(),
    ) = Station(
        id = 1, latitude = 45.764, longitude = 4.8357, address = "1 rue X", postalCode = "69001",
        city = "Lyon", prices = prices, isOpen24h = false, temporaryShortages = shortages,
    )

    @Test
    fun fuelsAreListedInFuelTypeOrderAndOnlyIfSoldHere() {
        val details = StationDetailsBuilder.build(
            station(mapOf(FuelType.E10 to FuelPrice(1.9, now), FuelType.DIESEL to FuelPrice(1.8, now))),
            userLocation = null, nowEpochMillis = now,
        )
        assertEquals(listOf(FuelType.DIESEL, FuelType.E10), details.fuels.map { it.fuel })
        assertTrue(details.fuels.all { it.status == FuelRowStatus.CURRENT })
    }

    @Test
    fun anOldOrUndatedPriceIsFlagged() {
        val details = StationDetailsBuilder.build(
            station(mapOf(FuelType.DIESEL to FuelPrice(1.8, now - 100 * day), FuelType.E10 to FuelPrice(1.9, null))),
            userLocation = null, nowEpochMillis = now,
        )
        assertEquals(FuelRowStatus.OLD_PRICE, details.fuels[0].status)
        assertEquals(FuelRowStatus.OLD_PRICE, details.fuels[1].status)
        assertNull(details.fuels[1].updatedAtEpochMillis)
    }

    @Test
    fun aRecentShortageIsShownEvenWithoutPrice() {
        val details = StationDetailsBuilder.build(
            station(emptyMap(), mapOf(FuelType.SP98 to now - 3 * day)),
            userLocation = null, nowEpochMillis = now,
        )
        val row = details.fuels.single()
        assertEquals(FuelRowStatus.SHORTAGE, row.status)
        assertNull(row.euroPerLitre)
        assertEquals(now - 3 * day, row.shortageSinceEpochMillis)
    }

    @Test
    fun anOldShortageWithoutPriceIsIgnoredAndWithPriceFallsBackToThePrice() {
        val withoutPrice = StationDetailsBuilder.build(
            station(emptyMap(), mapOf(FuelType.SP98 to now - 400 * day)), null, now,
        )
        assertTrue(withoutPrice.fuels.isEmpty())

        val withPrice = StationDetailsBuilder.build(
            station(mapOf(FuelType.SP98 to FuelPrice(2.0, now)), mapOf(FuelType.SP98 to now - 400 * day)), null, now,
        )
        assertEquals(FuelRowStatus.CURRENT, withPrice.fuels.single().status)
    }

    @Test
    fun distanceIsOnlyKnownWhenTheUserIsLocated() {
        val s = station(mapOf(FuelType.DIESEL to FuelPrice(1.8, now)))
        assertNull(StationDetailsBuilder.build(s, null, now).straightLineKm)
        val km = assertNotNull(StationDetailsBuilder.build(s, GeoPoint(45.764, 4.8357), now).straightLineKm)
        assertEquals(0.0, km, 0.001)
    }

    @Test
    fun straightLineDistanceParisLyonIsAbout392Km() {
        val km = GeoPoint(48.8566, 2.3522).straightLineKmTo(GeoPoint(45.7640, 4.8357))
        assertTrue(km in 389.0..394.0, "got $km")
    }
}
