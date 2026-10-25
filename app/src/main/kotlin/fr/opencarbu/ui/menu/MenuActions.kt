package fr.opencarbu.ui.menu

import fr.opencarbu.domain.model.DataSettings
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.HistorySettings
import fr.opencarbu.domain.model.MapDisplaySettings
import fr.opencarbu.domain.model.NearestSettings
import fr.opencarbu.domain.model.RoutingSettings
import fr.opencarbu.domain.pricing.PricingSettings

/** What the menu pages can ask the app to do. Grouped to keep the screen's parameter list short. */
class MenuActions(
    val onPricingChanged: (PricingSettings) -> Unit,
    val onDisplayChanged: (MapDisplaySettings) -> Unit,
    /** null = reopen on the last fuel used. */
    val onDefaultFuelChanged: (FuelType?) -> Unit,
    val onDataChanged: (DataSettings) -> Unit,
    val onRoutingChanged: (RoutingSettings) -> Unit,
    val onNearestChanged: (NearestSettings) -> Unit,
    val onHistoryChanged: (HistorySettings) -> Unit,
    val onHistoryClear: () -> Unit,
    /** Exports the history as CSV into the file at this location (chosen by the user). */
    val onHistoryExport: (android.net.Uri) -> Unit,
    /** Adds the history of the CSV file at this location (chosen by the user) to the one on the phone. */
    val onHistoryImport: (android.net.Uri) -> Unit,
    val onRefresh: () -> Unit,
    val onRefreshNames: () -> Unit,
    val onResetAll: () -> Unit,
)

/** The pages reachable from the menu button. */
enum class MenuPage { INFO, SETTINGS }
