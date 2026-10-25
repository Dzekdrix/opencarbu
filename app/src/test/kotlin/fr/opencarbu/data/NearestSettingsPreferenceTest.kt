package fr.opencarbu.data

import fr.opencarbu.domain.model.NearestSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class NearestSettingsPreferenceTest {
    @Test
    fun defaultsAreThirtyKilometres() {
        assertEquals(30, NearestSettings().radiusKm)
        assertEquals(NearestSettings(), NearestSettingsPreference.fromStored(null, null))
    }

    @Test
    fun savedValuesAreRestored() {
        assertEquals(NearestSettings(75, 7.5f), NearestSettingsPreference.fromStored(75, 7.5f))
    }

    @Test
    fun eachInvalidValueFallsBackAlone() {
        assertEquals(NearestSettings(radiusKm = 50), NearestSettingsPreference.fromStored(50, 99f))
        assertEquals(NearestSettings(consumptionLitresPer100Km = 8f), NearestSettingsPreference.fromStored(1, 8f))
        assertEquals(NearestSettings(), NearestSettingsPreference.fromStored(500, Float.NaN))
    }

    @Test
    fun tankDefaultsToFortyFiveLitresAndStaysInRange() {
        assertEquals(45, NearestSettings().tankLitres)
        assertEquals(60, NearestSettingsPreference.fromStored(null, null, 60).tankLitres)
        assertEquals(45, NearestSettingsPreference.fromStored(null, null, 4).tankLitres)
        assertEquals(45, NearestSettingsPreference.fromStored(null, null, 121).tankLitres)
        assertEquals(5, NearestSettingsPreference.fromStored(null, null, 5).tankLitres)
        assertEquals(120, NearestSettingsPreference.fromStored(null, null, 120).tankLitres)
    }
}
