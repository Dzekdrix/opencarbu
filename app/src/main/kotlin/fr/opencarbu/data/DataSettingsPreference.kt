package fr.opencarbu.data

import fr.opencarbu.domain.model.DataSettings

/** How [DataSettings] are written to, and read back from, the app's saved settings. */
object DataSettingsPreference {
    const val KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes"
    const val KEY_NAMES_REFRESH_INTERVAL_DAYS = "names_refresh_interval_days"
    const val KEY_SHOW_REFERENCE_ONLY = "show_reference_only_stations"

    /** Settings for the saved value; null or out of range falls back to the default. */
    fun fromStored(
        refreshIntervalMinutes: Int?,
        namesRefreshIntervalDays: Int? = null,
        showReferenceOnlyStations: Boolean? = null,
    ): DataSettings = DataSettings(
        refreshIntervalMinutes = refreshIntervalMinutes
            ?.takeIf { it in DataSettings.MIN_REFRESH_INTERVAL_MINUTES..DataSettings.MAX_REFRESH_INTERVAL_MINUTES }
            ?: DataSettings.DEFAULT_REFRESH_INTERVAL_MINUTES,
        namesRefreshIntervalDays = namesRefreshIntervalDays
            ?.takeIf { it in DataSettings.MIN_NAMES_REFRESH_INTERVAL_DAYS..DataSettings.MAX_NAMES_REFRESH_INTERVAL_DAYS }
            ?: DataSettings.DEFAULT_NAMES_REFRESH_INTERVAL_DAYS,
        showReferenceOnlyStations = showReferenceOnlyStations ?: DataSettings.DEFAULT_SHOW_REFERENCE_ONLY_STATIONS,
    )
}
