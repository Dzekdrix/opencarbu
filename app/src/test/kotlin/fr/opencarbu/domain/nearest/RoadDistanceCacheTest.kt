package fr.opencarbu.domain.nearest

import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.routing.RouteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoadDistanceCacheTest {
    private val here = GeoPoint(48.0, 2.0)
    private val server = "https://a.example/routed-car"
    private val r1 = RouteResult(5.0, 6)

    @Test
    fun answersWhenEverythingIsKnownForTheSamePlace() {
        val cache = RoadDistanceCache()
        cache.put(here, server, mapOf(1L to r1, 2L to null))
        // ~50 m north: still the same place
        val near = GeoPoint(48.00045, 2.0)
        assertEquals(listOf(r1, null), cache.all(near, server, listOf(1L, 2L)))
        assertEquals(emptyList(), cache.missing(near, server, listOf(1L, 2L)))
    }

    @Test
    fun missingStationsAreReportedAndBlockTheAnswer() {
        val cache = RoadDistanceCache()
        cache.put(here, server, mapOf(1L to r1))
        assertNull(cache.all(here, server, listOf(1L, 3L)))
        assertEquals(listOf(3L), cache.missing(here, server, listOf(1L, 3L)))
    }

    @Test
    fun movingMoreThan100mOrChangingServerForgetsEverything() {
        val cache = RoadDistanceCache()
        cache.put(here, server, mapOf(1L to r1))
        val far = GeoPoint(48.0015, 2.0) // ~167 m
        assertNull(cache.all(far, server, listOf(1L)))
        assertEquals(listOf(1L), cache.missing(far, server, listOf(1L)))
        assertNull(cache.all(here, "https://b.example", listOf(1L)))
    }

    @Test
    fun smallStepsDoNotDragTheAnchorAlong() {
        val cache = RoadDistanceCache()
        cache.put(here, server, mapOf(1L to r1))
        cache.put(GeoPoint(48.0007, 2.0), server, mapOf(2L to r1)) // ~78 m: added to the same series
        assertEquals(listOf(r1, r1), cache.all(here, server, listOf(1L, 2L)))
        cache.put(GeoPoint(48.0014, 2.0), server, mapOf(3L to r1)) // ~156 m from the anchor: new series
        assertNull(cache.all(GeoPoint(48.0014, 2.0), server, listOf(1L)))
    }
}
