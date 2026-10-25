package fr.opencarbu.domain.history

import fr.opencarbu.domain.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PriceHistoryCsvTest {
    @Test
    fun lineIsIsoUtcWithDecimalPoint() {
        // 2026-10-15T08:30:00Z
        assertEquals(
            "1000001,DIESEL,2026-10-15T08:30:00Z,1.789",
            PriceHistoryCsv.line(1000001, FuelType.DIESEL, 1_792_053_000_000, 1789),
        )
    }

    @Test
    fun millisecondsAreDroppedAndPricesPadded() {
        assertEquals("5,E85,2026-10-15T08:30:00Z,0.999", PriceHistoryCsv.line(5, FuelType.E85, 1_792_053_000_999, 999))
        assertEquals("5,LPG,2026-10-15T08:30:00Z,1.005", PriceHistoryCsv.line(5, FuelType.LPG, 1_792_053_000_000, 1005))
    }

    @Test
    fun headerNamesTheColumns() {
        assertEquals(4, PriceHistoryCsv.HEADER.split(",").size)
    }

    @Test
    fun exportedLinesReadBackIdentically() {
        val original = PriceChange(1120005, FuelType.SP98, 1_792_053_000_000, 1999)
        val line = PriceHistoryCsv.line(original.stationId, original.fuel, original.atEpochMillis, original.milliEuros)
        assertEquals(original, PriceHistoryCsv.parseLine(line))
    }

    @Test
    fun headerIsRecognisedWithBomSpacesAndExtraColumns() {
        assertEquals(true, PriceHistoryCsv.isHeader(PriceHistoryCsv.HEADER))
        assertEquals(true, PriceHistoryCsv.isHeader("\uFEFFstation_id, FUEL ,changed_at,price_eur,city"))
        assertEquals(false, PriceHistoryCsv.isHeader("id,name,price"))
        assertEquals(false, PriceHistoryCsv.isHeader(""))
    }

    @Test
    fun parserIsLenientOnCaseSpacesZonesAndExtraColumns() {
        assertEquals(
            PriceChange(5, FuelType.DIESEL, 1_792_053_000_000, 1789),
            PriceHistoryCsv.parseLine("5, diesel ,2026-10-15T10:30:00+02:00,1.789,Dagneux"),
        )
        assertEquals(1789, PriceHistoryCsv.parseLine("5,E10,2026-10-15T08:30:00Z,1.7890")?.milliEuros)
    }

    @Test
    fun badLinesAreRejected() {
        val bad = listOf(
            "", "1,DIESEL,2026-10-15T08:30:00Z", "x,DIESEL,2026-10-15T08:30:00Z,1.789",
            "1,KEROSENE,2026-10-15T08:30:00Z,1.789", "1,DIESEL,yesterday,1.789",
            "1,DIESEL,1970-01-01T00:00:00Z,1.789", "1,DIESEL,2026-10-15T08:30:00Z,abc",
            "1,DIESEL,2026-10-15T08:30:00Z,0.05", "1,DIESEL,2026-10-15T08:30:00Z,25.0",
        )
        for (line in bad) assertNull(PriceHistoryCsv.parseLine(line), line)
    }
}
