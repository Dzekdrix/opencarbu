package fr.opencarbu.data

import fr.opencarbu.domain.model.RoutingSettings

/** How [RoutingSettings] are written to, and read back from, the app's saved settings. */
object RoutingSettingsPreference {
    const val KEY_ENABLED = "routing_enabled"
    const val KEY_SERVER_URL = "routing_server_url"

    /** Missing (null) or invalid values fall back to the defaults: disabled, public server. */
    fun fromStored(enabled: Boolean?, serverUrl: String?): RoutingSettings = RoutingSettings(
        enabled = enabled ?: false,
        serverUrl = serverUrl?.takeIf(RoutingSettings::isValidUrl)?.let(RoutingSettings::normalize)
            ?: RoutingSettings.DEFAULT_SERVER_URL,
    )
}
