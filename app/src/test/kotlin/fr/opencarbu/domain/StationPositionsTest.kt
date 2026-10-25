package fr.opencarbu.domain

import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.model.StationIdentity
import fr.opencarbu.domain.model.StationPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StationPositionsTest {

    private fun station(id: Long, lat: Double, lon: Double) =
        Station(id, lat, lon, "addr", "75000", "Paris", emptyMap(), false)

    @Test
    fun replacesOnlyTheStationsThatHaveAnOsmPosition() {
        val stations = listOf(station(1, 48.0, 2.0), station(2, 45.0, 4.0), station(3, 44.0, 3.0))
        val identities = mapOf(
            1L to StationIdentity("Avia", null, position = GeoPoint(48.1, 2.1)),
            2L to StationIdentity("Esso", null), // no position: untouched
        )
        val result = StationPositions.apply(stations, identities)
        assertEquals(GeoPoint(48.1, 2.1), GeoPoint(result[0].latitude, result[0].longitude))
        assertEquals(stations[1], result[1])
        assertEquals(stations[2], result[2])
        assertEquals("addr", result[0].address) // the rest of the station is kept
    }

    @Test
    fun returnsTheSameListWhenNothingChanges() {
        val stations = listOf(station(1, 48.0, 2.0))
        assertSame(stations, StationPositions.apply(stations, emptyMap()))
        assertSame(stations, StationPositions.apply(stations, mapOf(1L to StationIdentity("A", null, position = GeoPoint(48.0, 2.0)))))
    }

    @Test
    fun addsStationsOnlyKnownFromTheReferenceFileWhenAsked() {
        val stations = listOf(station(1, 48.0, 2.0))
        val identities = mapOf(
            1L to StationIdentity("Avia", null, position = GeoPoint(48.0, 2.0)),
            2L to StationIdentity("Esso", null, "12 Rue X", "80000", "Amiens", GeoPoint(49.9, 2.3)),
            3L to StationIdentity("Q8", null), // no position: cannot be drawn
        )
        assertSame(stations, StationPositions.apply(stations, identities)) // off by default
        val result = StationPositions.apply(stations, identities, includeReferenceOnly = true)
        assertEquals(listOf(1L, 2L), result.map { it.id })
        val extra = result[1]
        assertEquals(true, extra.isReferenceOnly)
        assertEquals("12 Rue X", extra.address)
        assertEquals(49.9, extra.latitude, 0.0)
        assertEquals(emptyMap(), extra.prices)
    }
}
