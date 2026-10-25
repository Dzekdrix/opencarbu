package fr.opencarbu.domain

import fr.opencarbu.domain.pricing.PriceColors
import kotlin.test.Test
import kotlin.test.assertEquals

class PriceColorsTest {

    @Test
    fun cheapestIsPureGreen() = assertEquals(0xFF00FF00.toInt(), PriceColors.argbForRatio(0f))

    @Test
    fun mostExpensiveIsPureRed() = assertEquals(0xFFFF0000.toInt(), PriceColors.argbForRatio(1f))

    @Test
    fun middleIsYellow() = assertEquals(0xFFFFFF00.toInt(), PriceColors.argbForRatio(0.5f))

    @Test
    fun quarterIsYellowishGreen() {
        // Hue 90°: half red, full green.
        assertEquals(0xFF80FF00.toInt(), PriceColors.argbForRatio(0.25f))
    }

    @Test
    fun outOfRangeRatiosAreClamped() {
        assertEquals(PriceColors.argbForRatio(0f), PriceColors.argbForRatio(-3f))
        assertEquals(PriceColors.argbForRatio(1f), PriceColors.argbForRatio(7f))
    }
}
