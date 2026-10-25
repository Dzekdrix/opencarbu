package fr.opencarbu.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddressResponseParserTest {

    private fun feature(label: Any?, type: Any?, coordinates: Any?) = mapOf(
        "geometry" to mapOf("type" to "Point", "coordinates" to coordinates),
        "properties" to mapOf("label" to label, "type" to type),
    )

    @Test
    fun readsLabelPointAndKind() {
        val root = mapOf("features" to listOf(feature("8 Boulevard du Port 80000 Amiens", "housenumber", listOf(2.29, 49.89))))
        val result = AddressResponseParser.parse(root).single()
        assertEquals("8 Boulevard du Port 80000 Amiens", result.label)
        // GeoJSON is [longitude, latitude]
        assertEquals(49.89, result.point.latitude, 0.0)
        assertEquals(2.29, result.point.longitude, 0.0)
    }

    @Test
    fun areaAroundAResultIsTenKilometres() {
        assertEquals(10.0, SEARCH_RADIUS_KM, 0.0)
    }

    @Test
    fun skipsBrokenFeaturesButKeepsTheGoodOnes() {
        val root = mapOf(
            "features" to listOf(
                feature("", "street", listOf(2.0, 48.0)),          // no label
                feature("Ok", "street", listOf(2.0)),               // missing latitude
                feature("Far", "street", listOf(2.0, 95.0)),        // impossible latitude
                feature("Text", "street", listOf("2.0", "48.0")),   // not numbers
                "garbage",
                feature("Good", "street", listOf(2.0, 48.0)),
            ),
        )
        assertEquals(listOf("Good"), AddressResponseParser.parse(root).map { it.label })
    }

    @Test
    fun unexpectedRootsGiveAnEmptyList() {
        assertTrue(AddressResponseParser.parse(null).isEmpty())
        assertTrue(AddressResponseParser.parse(listOf(1, 2)).isEmpty())
        assertTrue(AddressResponseParser.parse(mapOf("features" to "x")).isEmpty())
        assertTrue(AddressResponseParser.parse(mapOf("features" to emptyList<Any>())).isEmpty())
    }
}
