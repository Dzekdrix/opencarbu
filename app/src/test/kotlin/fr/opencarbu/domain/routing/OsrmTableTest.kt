package fr.opencarbu.domain.routing

import fr.opencarbu.domain.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OsrmTableTest {
    @Test
    fun requestListsOriginFirstThenDestinations() {
        val url = OsrmTableRequest.url("https://example.org/routed-car/", GeoPoint(43.0, 4.0), listOf(GeoPoint(44.0, 5.0), GeoPoint(45.0, 6.0)))
        assertEquals(
            "https://example.org/routed-car/table/v1/driving/4.00000,43.00000;5.00000,44.00000;6.00000,45.00000" +
                "?sources=0&annotations=distance,duration",
            url,
        )
    }

    @Test
    fun skipsTheOriginColumnAndKeepsOrder() {
        val root = mapOf(
            "code" to "Ok",
            "distances" to listOf(listOf(0.0, 12000.0, 3500.0)),
            "durations" to listOf(listOf(0.0, 900.0, 300.0)),
        )
        val result = assertNotNull(OsrmTableParser.parse(root, 2))
        assertEquals(12.0, result[0]!!.distanceKm, 1e-9)
        assertEquals(15, result[0]!!.durationMinutes)
        assertEquals(3.5, result[1]!!.distanceKm, 1e-9)
    }

    @Test
    fun unreachableDestinationIsNull() {
        val root = mapOf(
            "code" to "Ok",
            "distances" to listOf(listOf(0.0, null, 100.0)),
            "durations" to listOf(listOf(0.0, null, 10.0)),
        )
        val result = assertNotNull(OsrmTableParser.parse(root, 2))
        assertNull(result[0])
        assertNotNull(result[1])
    }

    @Test
    fun rejectsErrorsAndWrongSizes() {
        assertNull(OsrmTableParser.parse(mapOf("code" to "InvalidQuery"), 1))
        assertNull(OsrmTableParser.parse(null, 1))
        val short = mapOf("code" to "Ok", "distances" to listOf(listOf(0.0)), "durations" to listOf(listOf(0.0)))
        assertNull(OsrmTableParser.parse(short, 2))
    }
}
