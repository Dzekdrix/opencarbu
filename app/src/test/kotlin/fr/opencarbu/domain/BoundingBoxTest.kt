package fr.opencarbu.domain

import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoundingBoxTest {

    /** Reference distance between two points (haversine formula), in kilometres. */
    private fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.latitude * PI / 180
        val lat2 = b.latitude * PI / 180
        val dLat = lat2 - lat1
        val dLon = (b.longitude - a.longitude) * PI / 180
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * 6371.0 * asin(sqrt(h))
    }

    private val marseille = GeoPoint(43.2965, 5.3698)

    @Test
    fun boxAroundAPointContainsIt() {
        assertTrue(BoundingBox.around(marseille, 200.0).contains(marseille.latitude, marseille.longitude))
    }

    @Test
    fun boxReachesTwoHundredKilometresNorthSouthEastAndWest() {
        val box = BoundingBox.around(marseille, 200.0)
        val middleLat = marseille.latitude
        val middleLon = marseille.longitude
        assertEquals(200.0, distanceKm(marseille, GeoPoint(box.north, middleLon)), 1.0)
        assertEquals(200.0, distanceKm(marseille, GeoPoint(box.south, middleLon)), 1.0)
        // Measured along the east and west edges, at the latitude of the centre.
        assertEquals(200.0, distanceKm(marseille, GeoPoint(middleLat, box.east)), 1.0)
        assertEquals(200.0, distanceKm(marseille, GeoPoint(middleLat, box.west)), 1.0)
    }

    @Test
    fun boxIsWiderInDegreesThanTallAwayFromTheEquator() {
        val box = BoundingBox.around(marseille, 200.0)
        assertTrue(box.east - box.west > box.north - box.south)
    }

    @Test
    fun theWholeDiscFitsInsideTheBox() {
        val box = BoundingBox.around(marseille, 200.0)
        // Points just inside 200 km (199.9, to stay clear of rounding errors on the edge) must lie within the box.
        for (bearingDegrees in listOf(0, 45, 90, 135, 180, 225, 270, 315)) {
            val bearing = bearingDegrees * PI / 180
            val d = 199.9 / 6371.0
            val lat1 = marseille.latitude * PI / 180
            val lon1 = marseille.longitude * PI / 180
            val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(bearing))
            val lon2 = lon1 + kotlin.math.atan2(sin(bearing) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
            assertTrue(box.contains(lat2 * 180 / PI, lon2 * 180 / PI), "bearing $bearingDegrees")
        }
    }

    @Test
    fun boxIsClampedAtThePoles() {
        val box = BoundingBox.around(GeoPoint(89.9, 10.0), 200.0)
        assertEquals(90.0, box.north)
        assertEquals(-180.0, box.west)
        assertEquals(180.0, box.east)
    }

    @Test
    fun radiusMustBePositive() {
        assertFailsWith<IllegalArgumentException> { BoundingBox.around(marseille, 0.0) }
    }
}
