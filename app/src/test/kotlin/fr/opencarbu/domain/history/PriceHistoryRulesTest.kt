package fr.opencarbu.domain.history

import fr.opencarbu.domain.model.FuelPrice
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.HistorySettings
import fr.opencarbu.domain.model.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceHistoryRulesTest {

    private fun station(id: Long, vararg prices: Pair<FuelType, FuelPrice>) =
        Station(id, 0.0, 0.0, "", "", "", prices.toMap(), false)

    private fun price(euro: Double, at: Long?) = FuelPrice(euro, at)

    @Test
    fun firstSightingIsRecorded() {
        val changes = PriceHistoryRules.changesToRecord(
            listOf(station(1, FuelType.DIESEL to price(1.789, 1000))), emptyMap(),
        )
        assertEquals(listOf(PriceChange(1, FuelType.DIESEL, 1000, 1789)), changes)
    }

    @Test
    fun samePriceIsNotRecordedAgainEvenWithNewerDate() {
        val last = mapOf(PriceHistoryRules.key(1, FuelType.DIESEL) to RecordedPrice(1000, 1789))
        val changes = PriceHistoryRules.changesToRecord(
            listOf(station(1, FuelType.DIESEL to price(1.789, 2000))), last,
        )
        assertTrue(changes.isEmpty())
    }

    @Test
    fun changedPriceWithNewerDateIsRecorded() {
        val last = mapOf(PriceHistoryRules.key(1, FuelType.DIESEL) to RecordedPrice(1000, 1789))
        val changes = PriceHistoryRules.changesToRecord(
            listOf(station(1, FuelType.DIESEL to price(1.759, 2000))), last,
        )
        assertEquals(listOf(PriceChange(1, FuelType.DIESEL, 2000, 1759)), changes)
    }

    @Test
    fun olderOrEqualDateIsIgnored() {
        val last = mapOf(PriceHistoryRules.key(1, FuelType.DIESEL) to RecordedPrice(2000, 1789))
        val older = PriceHistoryRules.changesToRecord(listOf(station(1, FuelType.DIESEL to price(1.5, 1500))), last)
        val equal = PriceHistoryRules.changesToRecord(listOf(station(1, FuelType.DIESEL to price(1.5, 2000))), last)
        assertTrue(older.isEmpty() && equal.isEmpty())
    }

    @Test
    fun priceWithoutDateIsIgnored() {
        assertTrue(
            PriceHistoryRules.changesToRecord(listOf(station(1, FuelType.SP95 to price(1.9, null))), emptyMap()).isEmpty(),
        )
    }

    @Test
    fun keysDifferByStationAndFuel() {
        val keys = FuelType.entries.map { PriceHistoryRules.key(7, it) } + FuelType.entries.map { PriceHistoryRules.key(8, it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun priceIsRoundedToThousandths() {
        assertEquals(1789, PriceHistoryRules.toMilliEuros(1.7889999999))
        assertEquals(1790, PriceHistoryRules.toMilliEuros(1.79))
    }

    @Test
    fun curveStartsWithThePriceInForceAndExtendsToNow() {
        val points = listOf(PricePoint(100, 1800), PricePoint(500, 1750), PricePoint(900, 1700))
        val curve = PriceHistoryRules.curve(points, fromEpochMillis = 300, nowEpochMillis = 1000)
        assertEquals(
            listOf(PricePoint(300, 1800), PricePoint(500, 1750), PricePoint(900, 1700), PricePoint(1000, 1700)),
            curve,
        )
    }

    @Test
    fun curveIsEmptyWhenNothingIsKnown() {
        assertTrue(PriceHistoryRules.curve(emptyList(), 0, 1000).isEmpty())
        // Only a change after the window: nothing known at its start, and nothing inside either.
        assertTrue(PriceHistoryRules.curve(listOf(PricePoint(2000, 1700)), 0, 1000).isEmpty())
    }

    @Test
    fun curveBeginsAtFirstChangeWhenNoPriceBeforeTheWindow() {
        val curve = PriceHistoryRules.curve(listOf(PricePoint(500, 1750)), 300, 1000)
        assertEquals(listOf(PricePoint(500, 1750), PricePoint(1000, 1750)), curve)
    }

    @Test
    fun retentionCutoff() {
        val day = 24L * 60 * 60 * 1000
        assertNull(HistorySettings(retentionMonths = HistorySettings.FOREVER).retentionCutoff(1_000_000_000_000))
        assertEquals(1_000_000_000_000 - 90 * day, HistorySettings(retentionMonths = 3).retentionCutoff(1_000_000_000_000))
        assertFailsWith<IllegalArgumentException> { HistorySettings(retentionMonths = 5) }
    }
}
