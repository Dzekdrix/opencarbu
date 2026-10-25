package fr.opencarbu

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import fr.opencarbu.ui.MainViewModel
import fr.opencarbu.ui.map.MapScreen
import fr.opencarbu.ui.menu.MenuActions
import fr.opencarbu.ui.theme.OpenCarbuTheme

/**
 * The app's only activity: it shows the map screen. Its lifecycle is passed to the map,
 * which must follow it (see StationMapView).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Each time the app comes to the screen (launch, or return from the background). */
    override fun onStart() {
        super.onStart()
        viewModel.onAppOpened()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The map goes behind the system bars (Android 15 does this for every app; this does it on older
        // versions too). Both bars are transparent; the translucent tint that keeps the status bar icons
        // readable over the map is drawn by MapScreen (it follows the theme colour). With 3-button
        // navigation the system adds its own scrim at the bottom.
        // "light" = dark icons: the app is always in its light scheme, even when the phone is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val menuActions = MenuActions(
            onPricingChanged = viewModel::setPricingSettings,
            onDisplayChanged = viewModel::setDisplaySettings,
            onDefaultFuelChanged = viewModel::setDefaultFuel,
            onRefresh = viewModel::refresh,
            onRefreshNames = viewModel::refreshNames,
            onResetAll = viewModel::resetSettings,
            onDataChanged = viewModel::setDataSettings,
            onRoutingChanged = viewModel::setRoutingSettings,
            onNearestChanged = viewModel::setNearestSettings,
            onHistoryChanged = viewModel::setHistorySettings,
            onHistoryClear = viewModel::clearHistory,
            onHistoryExport = viewModel::exportHistory,
            onHistoryImport = viewModel::importHistory,
        )
        setContent {
            OpenCarbuTheme {
                val state by viewModel.state.collectAsState()
                val mapData by viewModel.mapData.collectAsState()
                MapScreen(
                    state = state,
                    mapData = mapData,
                    lifecycle = lifecycle,
                    onCameraIdle = viewModel::onCameraIdle,
                    onFuelSelected = viewModel::selectFuel,
                    onLocated = viewModel::onLocated,
                    onLocateProblem = viewModel::onLocateProblem,
                    onStartupLocateLaunched = viewModel::markStartupLocateDone,
                    onAreaShown = viewModel::onAreaShown,
                    onStationTapped = viewModel::onStationTapped,
                    onStationDismissed = viewModel::onStationDismissed,
                    onLegendExpandedChange = viewModel::setLegendExpanded,
                    onSearchAddress = viewModel::searchAddress,
                    onAddressChosen = viewModel::onAddressChosen,
                    onRoadRoute = viewModel::roadRoute,
                    onRoadDistances = viewModel::roadDistances,
                    cachedRoadDistances = viewModel::cachedRoadDistances,
                    onNearestChosen = viewModel::onNearestChosen,
                    onPriceHistory = viewModel::priceHistory,
                    menuActions = menuActions,
                )
            }
        }
    }
}
