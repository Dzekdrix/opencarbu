package fr.opencarbu.domain

import fr.opencarbu.domain.pricing.PriceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceRangeTest {

    @Test
    fun emptyInputGivesNoRange() {
        assertNull(PriceRange.from(doubleArrayOf()))
    }

    @Test
    fun fewPricesUseExactMinAndMax() {
        val range = assertNotNull(PriceRange.from(doubleArrayOf(1.90, 1.70, 1.80)))
        assertEquals(1.70, range.low)
        assertEquals(1.90, range.high)
        assertEquals(0f, range.ratioOf(1.70))
        assertEquals(0.5f, range.ratioOf(1.80), 1e-6f)
        assertEquals(1f, range.ratioOf(1.90))
    }

    @Test
    fun identicalPricesAreAllGreen() {
        val range = assertNotNull(PriceRange.from(doubleArrayOf(1.8, 1.8, 1.8)))
        assertEquals(0f, range.ratioOf(1.8))
    }

    @Test
    fun outliersDoNotSquashTheScale() {
        // 99 "normal" prices between 1.80 and 1.98, plus one absurd outlier.
        val prices = DoubleArray(100) { 1.80 + it * 0.0018 }
        prices[99] = 9.0
        val range = assertNotNull(PriceRange.from(prices))
        // The 95th percentile ignores the outlier, so the scale stays close to the normal spread.
        assertTrue(range.high < 2.0, "high was ${range.high}")
        // The outlier is simply clamped to the reddest value.
        assertEquals(1f, range.ratioOf(9.0))
    }

    @Test
    fun percentileInterpolatesBetweenValues() {
        val sorted = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(1.0, PriceRange.percentile(sorted, 0.0))
        assertEquals(3.0, PriceRange.percentile(sorted, 0.5))
        assertEquals(5.0, PriceRange.percentile(sorted, 1.0))
        assertEquals(1.4, PriceRange.percentile(sorted, 0.1), 1e-9)
    }

    @Test
    fun inputArrayIsNotModified() {
        val prices = doubleArrayOf(3.0, 1.0, 2.0)
        PriceRange.from(prices)
        assertEquals(listOf(3.0, 1.0, 2.0), prices.toList())
    }
}
