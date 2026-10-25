package fr.opencarbu.data

import fr.opencarbu.domain.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals

class FuelPreferenceTest {

    @Test
    fun everyFuelSurvivesASaveAndReload() {
        for (fuel in FuelType.entries) {
            assertEquals(fuel, FuelPreference.fromStored(FuelPreference.toStored(fuel)))
        }
    }

    @Test
    fun theDefaultFuelSettingRoundTripsAndNullMeansLastUsed() {
        for (fuel in FuelType.entries) {
            assertEquals(fuel, FuelPreference.defaultFromStored(FuelPreference.defaultToStored(fuel)))
        }
        assertEquals(null, FuelPreference.defaultFromStored(FuelPreference.defaultToStored(null)))
        assertEquals(null, FuelPreference.defaultFromStored(null))
        assertEquals(null, FuelPreference.defaultFromStored("HYDROGEN"))
    }

    @Test
    fun startupFuelPrefersTheChosenDefaultThenTheLastUsed() {
        assertEquals(FuelType.E85, FuelPreference.startupFuel(FuelType.E85, "SP98"))
        assertEquals(FuelType.SP98, FuelPreference.startupFuel(null, "SP98"))
        assertEquals(FuelPreference.DEFAULT, FuelPreference.startupFuel(null, null))
    }

    @Test
    fun missingOrUnknownValuesFallBackToTheDefault() {
        assertEquals(FuelPreference.DEFAULT, FuelPreference.fromStored(null))
        assertEquals(FuelPreference.DEFAULT, FuelPreference.fromStored(""))
        assertEquals(FuelPreference.DEFAULT, FuelPreference.fromStored("HYDROGEN"))
        assertEquals(FuelPreference.DEFAULT, FuelPreference.fromStored("e10")) // names are case-sensitive
    }
}
