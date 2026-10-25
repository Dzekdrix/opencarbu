package fr.opencarbu.data

import fr.opencarbu.domain.pricing.PricingSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PricingSettingsPreferenceTest {

    @Test
    fun defaultsAreThreeMonthsForPricesAndThirtyDaysForShortages() {
        val defaults = PricingSettings()
        assertEquals(90, defaults.maxPriceAgeDays)
        assertEquals(30, defaults.maxShortageAgeDays)
        assertEquals(90L * 24 * 60 * 60 * 1000, defaults.maxPriceAgeMillis)
    }

    @Test
    fun savedValuesAreRestored() {
        assertEquals(PricingSettings(14, 7), PricingSettingsPreference.fromStored(14, 7))
    }

    @Test
    fun missingOrInvalidValuesFallBackToTheDefaultOfEachThreshold() {
        assertEquals(PricingSettings(), PricingSettingsPreference.fromStored(null, null))
        assertEquals(PricingSettings(maxPriceAgeDays = 14), PricingSettingsPreference.fromStored(14, 0))
        assertEquals(PricingSettings(maxShortageAgeDays = 7), PricingSettingsPreference.fromStored(-5, 7))
    }
}
