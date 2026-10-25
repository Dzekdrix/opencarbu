package fr.opencarbu.ui

import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.FuelPrice
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.pricing.PriceColors
import fr.opencarbu.domain.pricing.PriceStatus
import fr.opencarbu.domain.pricing.PricedStation
import fr.opencarbu.ui.map.MapDataFactory
import fr.opencarbu.ui.map.StationGeoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StationGeoJsonTest {

    private fun station(id: Long, lat: Double = 43.2965, lon: Double = 5.3698) =
        Station(id, lat, lon, "", "", "", emptyMap(), false)

    private fun dot(
        id: Long,
        price: Double? = 1.789,
        status: PriceStatus = PriceStatus.FRESH,
        ratio: Float? = 0f,
        color: Int = PriceColors.argbForRatio(0f),
    ) = PricedStation(station(id), price, status, ratio, color)

    private val plainFormat: (Double) -> String = { "%.3f".format(java.util.Locale.ROOT, it) }

    private fun countOf(text: String, part: String) = text.windowed(part.length).count { it == part }

    @Test
    fun noDotsGivesAnEmptyCollection() {
        assertEquals(StationGeoJson.EMPTY, StationGeoJson.build(emptyList(), plainFormat))
    }

    @Test
    fun writesLongitudeBeforeLatitudeAndTheDotProperties() {
        val json = StationGeoJson.build(listOf(dot(42)), plainFormat)
        assertTrue("\"coordinates\":[5.3698,43.2965]" in json, json)
        assertTrue("\"id\":42" in json, json)
        assertTrue("\"label\":\"1.789\"" in json, json)
        assertTrue("\"color\":\"#00FF00\"" in json, json) // cheapest = pure green
        assertTrue("\"badge\":0" in json, json)
    }

    @Test
    fun shortageWithoutPriceHasEmptyLabelGreyColourAndBadge() {
        val shortage = dot(
            1, price = null, status = PriceStatus.TEMPORARILY_OUT, ratio = null,
            color = PriceColors.UNRELIABLE_ARGB,
        )
        val json = StationGeoJson.build(listOf(shortage), plainFormat)
        assertTrue("\"label\":\"\"" in json, json)
        assertTrue("\"color\":\"#BDBDBD\"" in json, json)
        assertTrue("\"badge\":1" in json, json)
        assertTrue("\"sort\":2.0" in json, json) // unranked: loses every label collision
    }

    @Test
    fun cheapestDotsAreWrittenLastSoTheyAreDrawnOnTop() {
        val cheap = dot(1, ratio = 0f)
        val middle = dot(2, ratio = 0.5f)
        val expensive = dot(3, ratio = 1f)
        val grey = dot(4, status = PriceStatus.STALE, ratio = null)
        val json = StationGeoJson.build(listOf(cheap, middle, expensive, grey), plainFormat)
        val ids = Regex("\"id\":(\\d+)").findAll(json).map { it.groupValues[1].toInt() }.toList()
        assertEquals(listOf(4, 3, 2, 1), ids)
    }

    @Test
    fun coordinatesAreRoundedToAboutOneMetre() {
        val json = StationGeoJson.build(
            listOf(PricedStation(station(1, lat = 46.7535171497, lon = 3.14069121289), 1.8, PriceStatus.FRESH, 0f, 0)),
            plainFormat,
        )
        assertTrue("[3.14069,46.75352]" in json, json)
    }

    @Test
    fun specialCharactersInLabelsAreEscaped() {
        val json = StationGeoJson.build(listOf(dot(1)), { "a\"b\\c" })
        assertTrue("\"label\":\"a\\\"b\\\\c\"" in json, json)
    }

    @Test
    fun oneFeaturePerDot() {
        val json = StationGeoJson.build(List(250) { dot(it.toLong()) }, plainFormat)
        assertEquals(250, countOf(json, "\"type\":\"Feature\""))
    }

    @Test
    fun wholePipelineFromStationsToJson() {
        val now = 1_800_000_000_000L
        val fresh = now - 1000
        fun priced(id: Long, price: Double, lat: Double) = Station(
            id, lat, 2.5, "", "", "", mapOf(FuelType.DIESEL to FuelPrice(price, fresh)), false,
        )
        val stations = listOf(priced(1, 1.80, 48.5), priced(2, 1.90, 48.6), priced(3, 1.50, 10.0)) // 3: off-screen
        val area = BoundingBox(south = 48.0, west = 2.0, north = 49.0, east = 3.0)

        val data = MapDataFactory.build(stations, FuelType.DIESEL, area, now, plainFormat)

        assertEquals(2, data.shownCount)
        assertEquals(2, countOf(data.geoJson, "\"type\":\"Feature\""))
        // National prices 1.50 / 1.80 / 1.90: Q1 = 1.65, median = 1.80, Q3 = 1.85.
        assertTrue("\"color\":\"#00FF00\"" !in data.geoJson)
        assertTrue("\"color\":\"#FFFF00\"" in data.geoJson) // 1.80: not above the median
        assertTrue("\"color\":\"#FF0000\"" in data.geoJson) // 1.90: above Q3
    }

    @Test
    fun pipelineHonoursThePricingSettings() {
        val now = 1_800_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val station = Station(
            1, 48.5, 2.5, "", "", "", mapOf(FuelType.DIESEL to FuelPrice(1.8, now - 30 * day)), false,
        )
        val area = BoundingBox(south = 48.0, west = 2.0, north = 49.0, east = 3.0)
        fun colourWith(settings: fr.opencarbu.domain.pricing.PricingSettings) =
            MapDataFactory.build(listOf(station), FuelType.DIESEL, area, now, plainFormat, settings).geoJson

        assertTrue("\"color\":\"#00FF00\"" in colourWith(fr.opencarbu.domain.pricing.PricingSettings())) // 30 days: fresh
        assertTrue("\"color\":\"#BDBDBD\"" in colourWith(fr.opencarbu.domain.pricing.PricingSettings(maxPriceAgeDays = 7)))
    }

    @Test
    fun userMarkerIsAPointOrEmpty() {
        assertEquals(StationGeoJson.EMPTY, StationGeoJson.userPoint(null))
        val json = StationGeoJson.userPoint(GeoPoint(43.2965, 5.3698))
        assertTrue("\"coordinates\":[5.3698,43.2965]" in json, json)
    }
}
