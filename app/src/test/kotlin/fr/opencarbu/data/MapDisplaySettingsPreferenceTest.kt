package fr.opencarbu.data

import fr.opencarbu.domain.model.MapDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapDisplaySettingsPreferenceTest {

    @Test
    fun defaultsAreEightyPercentOpacityAndFifteenKilometres() {
        assertEquals(0.8f, MapDisplaySettings().placeNameOpacity)
        assertEquals(15, MapDisplaySettings().startRadiusKm)
    }

    @Test
    fun savedRadiusIsRestoredAndInvalidOnesFallBack() {
        assertEquals(MapDisplaySettings.MAX_START_RADIUS_KM, MapDisplaySettingsPreference.fromStored(null, MapDisplaySettings.MAX_START_RADIUS_KM).startRadiusKm)
        assertEquals(40, MapDisplaySettingsPreference.fromStored(null, 40).startRadiusKm)
        assertEquals(15, MapDisplaySettingsPreference.fromStored(null, 0).startRadiusKm)
        assertEquals(15, MapDisplaySettingsPreference.fromStored(null, MapDisplaySettings.MAX_START_RADIUS_KM + 1).startRadiusKm)
        assertEquals(15, MapDisplaySettingsPreference.fromStored(null, 5000).startRadiusKm)
        assertEquals(0.3f, MapDisplaySettingsPreference.fromStored(0.3f, 5000).placeNameOpacity)
    }

    @Test
    fun sizeFactorDefaultsToOneAndInvalidValuesFallBack() {
        assertEquals(1.0f, MapDisplaySettings().stationSizeFactor)
        assertEquals(1.5f, MapDisplaySettingsPreference.fromStored(null, null, 1.5f).stationSizeFactor)
        assertEquals(1.0f, MapDisplaySettingsPreference.fromStored(null, null, 0f).stationSizeFactor)
        assertEquals(1.0f, MapDisplaySettingsPreference.fromStored(null, null, 9f).stationSizeFactor)
        assertEquals(1.0f, MapDisplaySettingsPreference.fromStored(null, null, Float.NaN).stationSizeFactor)
        assertFailsWith<IllegalArgumentException> { MapDisplaySettings(stationSizeFactor = 3f) }
    }

    @Test
    fun placeNameSizeFactorDefaultsToOneAndInvalidValuesFallBack() {
        assertEquals(1.0f, MapDisplaySettings().placeNameSizeFactor)
        assertEquals(1.4f, MapDisplaySettingsPreference.fromStored(null, null, null, 1.4f).placeNameSizeFactor)
        assertEquals(1.0f, MapDisplaySettingsPreference.fromStored(null, null, null, 0f).placeNameSizeFactor)
        assertEquals(1.0f, MapDisplaySettingsPreference.fromStored(null, null, null, Float.NaN).placeNameSizeFactor)
        assertFailsWith<IllegalArgumentException> { MapDisplaySettings(placeNameSizeFactor = 5f) }
    }

    @Test
    fun theSettingsObjectRejectsAnOutOfRangeRadius() {
        assertFailsWith<IllegalArgumentException> { MapDisplaySettings(startRadiusKm = 0) }
    }

    @Test
    fun savedValueIsRestored() {
        assertEquals(MapDisplaySettings(0.25f), MapDisplaySettingsPreference.fromStored(0.25f))
        assertEquals(MapDisplaySettings(0f), MapDisplaySettingsPreference.fromStored(0f))
        assertEquals(MapDisplaySettings(1f), MapDisplaySettingsPreference.fromStored(1f))
    }

    @Test
    fun missingOrInvalidValuesFallBackToTheDefault() {
        val default = MapDisplaySettings()
        assertEquals(default, MapDisplaySettingsPreference.fromStored(null))
        assertEquals(default, MapDisplaySettingsPreference.fromStored(-1f))
        assertEquals(default, MapDisplaySettingsPreference.fromStored(1.5f))
        assertEquals(default, MapDisplaySettingsPreference.fromStored(Float.NaN))
    }

    @Test
    fun theSettingsObjectItselfRejectsAnOutOfRangeOpacity() {
        assertFailsWith<IllegalArgumentException> { MapDisplaySettings(2f) }
    }

    @Test
    fun centeringOnStationIsOnByDefaultAndKeptWhenStored() {
        assertEquals(true, MapDisplaySettings().centerOnStation)
        assertEquals(true, MapDisplaySettingsPreference.fromStored(0.8f).centerOnStation)
        assertEquals(false, MapDisplaySettingsPreference.fromStored(0.8f, centerOnStation = false).centerOnStation)
    }
}
