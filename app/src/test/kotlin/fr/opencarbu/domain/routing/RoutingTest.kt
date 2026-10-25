package fr.opencarbu.domain.routing

import fr.opencarbu.data.RoutingSettingsPreference
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.RoutingSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingTest {

    @Test
    fun requestUsesLongitudeFirstAndNoTrailingSlash() {
        val url = OsrmRequest.url("https://example.org/routed-car/", GeoPoint(43.67, 4.43), GeoPoint(43.1, 5.9))
        assertEquals(
            "https://example.org/routed-car/route/v1/driving/4.43000,43.67000;5.90000,43.10000" +
                "?overview=false&alternatives=false&steps=false",
            url,
        )
    }

    @Test
    fun readsDistanceAndDuration() {
        val root = mapOf("code" to "Ok", "routes" to listOf(mapOf("distance" to 12345.6, "duration" to 905.0)))
        val result = OsrmResponseParser.parse(root)!!
        assertEquals(12.3456, result.distanceKm, 1e-9)
        assertEquals(15, result.durationMinutes)
    }

    @Test
    fun badAnswersGiveNullAndExposeTheErrorCode() {
        assertNull(OsrmResponseParser.parse(mapOf("code" to "NoRoute")))
        assertEquals("NoRoute", OsrmResponseParser.errorCode(mapOf("code" to "NoRoute")))
        assertNull(OsrmResponseParser.errorCode(mapOf("code" to "Ok")))
        assertNull(OsrmResponseParser.parse(mapOf("code" to "Ok", "routes" to emptyList<Any>())))
        assertNull(OsrmResponseParser.parse(mapOf("code" to "Ok", "routes" to listOf(mapOf("distance" to "x", "duration" to 1)))))
        assertNull(OsrmResponseParser.parse(mapOf("code" to "Ok", "routes" to listOf(mapOf("distance" to -1.0, "duration" to 1.0)))))
        assertNull(OsrmResponseParser.parse(null))
    }

    @Test
    fun urlValidationAcceptsHttpAddressesOnly() {
        assertTrue(RoutingSettings.isValidUrl("https://routing.openstreetmap.de/routed-car"))
        assertTrue(RoutingSettings.isValidUrl(" http://192.168.1.5:5000/ "))
        assertFalse(RoutingSettings.isValidUrl(""))
        assertFalse(RoutingSettings.isValidUrl("ftp://x.org"))
        assertFalse(RoutingSettings.isValidUrl("https://x.org/a b"))
        assertFalse(RoutingSettings.isValidUrl("https://x.org/a?b=c"))
        assertFalse(RoutingSettings.isValidUrl("routing.openstreetmap.de"))
    }

    @Test
    fun featureIsOffByDefaultAndStoredValuesAreChecked() {
        assertFalse(RoutingSettings().enabled)
        assertFalse(RoutingSettingsPreference.fromStored(null, null).enabled)
        assertEquals(RoutingSettings.DEFAULT_SERVER_URL, RoutingSettingsPreference.fromStored(true, "junk").serverUrl)
        val stored = RoutingSettingsPreference.fromStored(true, " https://my.server/car/ ")
        assertTrue(stored.enabled)
        assertEquals("https://my.server/car", stored.serverUrl)
    }
}
