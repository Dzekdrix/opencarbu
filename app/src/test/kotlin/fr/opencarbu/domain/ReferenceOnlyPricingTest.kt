package fr.opencarbu.domain

import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.pricing.PriceColors
import fr.opencarbu.domain.pricing.PriceStatus
import fr.opencarbu.domain.pricing.ViewportPricing
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferenceOnlyPricingTest {
    @Test
    fun referenceOnlyStationsAreDrawnWhateverTheFuelAndStayOutOfTheScale() {
        val only = Station(9, 48.0, 2.0, "", "", "", emptyMap(), false, isReferenceOnly = true)
        val dots = ViewportPricing.compute(listOf(only), FuelType.DIESEL, BoundingBox(47.0, 1.0, 49.0, 3.0), 0L)
        assertEquals(1, dots.size)
        assertEquals(PriceStatus.REFERENCE_ONLY, dots[0].status)
        assertEquals(PriceColors.REFERENCE_ONLY_ARGB, dots[0].colorArgb)
        assertEquals(null, dots[0].ratio)
    }
}
