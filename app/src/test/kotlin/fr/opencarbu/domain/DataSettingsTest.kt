package fr.opencarbu.domain

import fr.opencarbu.data.DataSettingsPreference
import fr.opencarbu.domain.model.DataSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataSettingsTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    @Test
    fun defaultIsEightHours() {
        assertEquals(480, DataSettings().refreshIntervalMinutes)
    }

    @Test
    fun dataIsStaleOnlyOnceTheIntervalHasPassed() {
        val settings = DataSettings(refreshIntervalMinutes = 60)
        assertFalse(settings.isStale(now - 59 * minute, now))
        assertTrue(settings.isStale(now - 60 * minute, now))
        assertTrue(settings.isStale(now - 5 * 60 * minute, now))
    }

    @Test
    fun noDataOrADateInTheFutureIsStale() {
        val settings = DataSettings()
        assertTrue(settings.isStale(null, now))
        assertTrue(settings.isStale(now + minute, now))
    }

    @Test
    fun oneMinuteAndFortyEightHoursAreAccepted() {
        assertEquals(1, DataSettings(1).refreshIntervalMinutes)
        assertEquals(2880, DataSettings(2880).refreshIntervalMinutes)
        assertFailsWith<IllegalArgumentException> { DataSettings(0) }
        assertFailsWith<IllegalArgumentException> { DataSettings(2881) }
    }

    @Test
    fun everySliderChoiceIsValid() {
        assertEquals(1, DataSettings.CHOICES_MINUTES.first())
        assertEquals(2880, DataSettings.CHOICES_MINUTES.last())
        for (choice in DataSettings.CHOICES_MINUTES) DataSettings(choice)
        assertEquals(DataSettings.CHOICES_MINUTES.sorted(), DataSettings.CHOICES_MINUTES)
    }

    @Test
    fun namesFileDefaultsToThirtyDaysAndIsStaleAfterThem() {
        val settings = DataSettings()
        val day = 24 * 60 * minute
        assertEquals(30, settings.namesRefreshIntervalDays)
        assertFalse(settings.isNamesStale(now - 29 * day, now))
        assertTrue(settings.isNamesStale(now - 30 * day, now))
        assertTrue(settings.isNamesStale(null, now))
        assertTrue(settings.isNamesStale(now + minute, now))
    }

    @Test
    fun namesIntervalChoicesAreValidAndSavedValuesFallBack() {
        for (days in DataSettings.CHOICES_NAMES_DAYS) DataSettings(namesRefreshIntervalDays = days)
        assertFailsWith<IllegalArgumentException> { DataSettings(namesRefreshIntervalDays = 0) }
        assertFailsWith<IllegalArgumentException> { DataSettings(namesRefreshIntervalDays = 366) }
        assertEquals(7, DataSettingsPreference.fromStored(15, 7).namesRefreshIntervalDays)
        assertEquals(30, DataSettingsPreference.fromStored(15, null).namesRefreshIntervalDays)
        assertEquals(30, DataSettingsPreference.fromStored(15, 0).namesRefreshIntervalDays)
        assertEquals(30, DataSettingsPreference.fromStored(15, 4000).namesRefreshIntervalDays)
    }

    @Test
    fun savedValueIsRestoredAndInvalidOnesFallBack() {
        assertEquals(15, DataSettingsPreference.fromStored(15).refreshIntervalMinutes)
        assertEquals(480, DataSettingsPreference.fromStored(null).refreshIntervalMinutes)
        assertEquals(480, DataSettingsPreference.fromStored(0).refreshIntervalMinutes)
        assertEquals(480, DataSettingsPreference.fromStored(99999).refreshIntervalMinutes)
    }
}
