package fr.opencarbu.data

import android.content.SharedPreferences
import fr.opencarbu.domain.model.HistorySettings

/** How [HistorySettings] are written to, and read back from, the app's saved settings. */
object HistorySettingsPreference {
    const val KEY_HISTORY_ENABLED = "history_enabled"
    const val KEY_RETENTION_MONTHS = "history_retention_months"
    const val KEY_BACKGROUND_REFRESH = "background_refresh"
    const val KEY_BACKGROUND_WIFI_ONLY = "background_wifi_only"

    /** Settings for the saved values; a missing or unknown value falls back to its default. */
    fun fromStored(
        historyEnabled: Boolean?,
        retentionMonths: Int?,
        backgroundRefresh: Boolean?,
        backgroundWifiOnly: Boolean?,
    ): HistorySettings = HistorySettings(
        historyEnabled = historyEnabled ?: false,
        retentionMonths = retentionMonths?.takeIf { it in HistorySettings.CHOICES_RETENTION_MONTHS }
            ?: HistorySettings.DEFAULT_RETENTION_MONTHS,
        backgroundRefresh = backgroundRefresh ?: false,
        backgroundWifiOnly = backgroundWifiOnly ?: true,
    )

    /** Reads the settings from [prefs] (also used by the background job, which has no ViewModel). */
    fun read(prefs: SharedPreferences): HistorySettings = fromStored(
        historyEnabled = prefs.getBoolean(KEY_HISTORY_ENABLED, false),
        retentionMonths = if (prefs.contains(KEY_RETENTION_MONTHS)) prefs.getInt(KEY_RETENTION_MONTHS, 0) else null,
        backgroundRefresh = prefs.getBoolean(KEY_BACKGROUND_REFRESH, false),
        backgroundWifiOnly = prefs.getBoolean(KEY_BACKGROUND_WIFI_ONLY, true),
    )

    fun write(prefs: SharedPreferences, settings: HistorySettings) {
        prefs.edit()
            .putBoolean(KEY_HISTORY_ENABLED, settings.historyEnabled)
            .putInt(KEY_RETENTION_MONTHS, settings.retentionMonths)
            .putBoolean(KEY_BACKGROUND_REFRESH, settings.backgroundRefresh)
            .putBoolean(KEY_BACKGROUND_WIFI_ONLY, settings.backgroundWifiOnly)
            .apply()
    }
}
