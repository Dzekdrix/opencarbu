package fr.opencarbu.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import fr.opencarbu.ui.insetsBottomAndSides
import fr.opencarbu.ui.insetsTopAndSides
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import androidx.lifecycle.Lifecycle
import fr.opencarbu.R
import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.history.PricePoint
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.pricing.PriceBand
import fr.opencarbu.domain.pricing.PriceColors
import fr.opencarbu.domain.routing.RouteResult
import fr.opencarbu.domain.search.AddressResult
import fr.opencarbu.ui.nearest.NearestStationsPage
import fr.opencarbu.ui.search.AddressSearchPage
import fr.opencarbu.domain.pricing.PricingSettings
import fr.opencarbu.ui.MapCamera
import fr.opencarbu.ui.UiState
import fr.opencarbu.ui.details.StationSheet
import fr.opencarbu.ui.labelRes
import fr.opencarbu.ui.location.LocateProblem
import fr.opencarbu.ui.location.rememberLocateAction
import fr.opencarbu.ui.menu.MenuActions
import fr.opencarbu.ui.menu.MenuButton
import fr.opencarbu.ui.menu.MenuPage
import fr.opencarbu.ui.menu.MenuPageHost

/** The loading arrow is twice as wide as a map button (40 dp). */
private val LOADING_ARROW_SIZE = 80.dp

/** Wait this long after the sheet's edge last moved before centring the station. */
private const val SHEET_SETTLE_MILLIS = 150L

/** Zoom level brought in on a station chosen from the nearest list when the map is further out. */
private const val NEAREST_CHOSEN_ZOOM = 10.0

/** The loading arrow never spins longer than this. */
private const val LOADING_GIVE_UP_MILLIS = 20_000L

/**
 * The app's main screen: the map full-screen, with the fuel selector on top and a legend and
 * data status at the bottom.
 */
@Composable
fun MapScreen(
    state: UiState,
    mapData: MapData,
    lifecycle: Lifecycle,
    onCameraIdle: (BoundingBox, MapCamera) -> Unit,
    onFuelSelected: (FuelType) -> Unit,
    onLocated: (GeoPoint) -> Unit,
    onLocateProblem: (LocateProblem) -> Unit,
    onStartupLocateLaunched: () -> Unit,
    onAreaShown: () -> Unit,
    onStationTapped: (Long) -> Unit,
    onStationDismissed: () -> Unit,
    onLegendExpandedChange: (Boolean) -> Unit,
    onSearchAddress: suspend (String) -> List<AddressResult>,
    onAddressChosen: (AddressResult) -> Unit,
    onRoadRoute: suspend (GeoPoint, GeoPoint) -> RouteResult,
    onRoadDistances: suspend (GeoPoint, List<Pair<Long, GeoPoint>>) -> List<RouteResult?>,
    cachedRoadDistances: (GeoPoint, List<Long>) -> List<RouteResult?>?,
    onNearestChosen: (Station) -> Unit,
    onPriceHistory: suspend (Long, FuelType, Long) -> List<PricePoint>,
    menuActions: MenuActions,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var nearestOpen by rememberSaveable { mutableStateOf(false) }
    // The page open over the map (Info or Settings), if any. Survives screen rotation.
    var menuPage by rememberSaveable { mutableStateOf<MenuPage?>(null) }
    val locate = rememberLocateAction(onLocated = onLocated, onProblem = onLocateProblem)

    // Once per app launch (not per rotation): try to centre the map on the user, silently.
    LaunchedEffect(Unit) {
        if (!state.startupLocateDone) {
            onStartupLocateLaunched()
            locate(false)
        }
    }

    // The tapped station, looked up once per change (a plain scan of ~10 000 stations).
    val selectedStation = remember(state.stations, state.selectedStationId) {
        state.selectedStationId?.let { id -> state.stations.firstOrNull { it.id == id } }
    }

    // Where the price feed puts the selected station (the dot may use another position).
    val feedPosition = remember(state.feedStations, state.selectedStationId) {
        state.selectedStationId?.let { id ->
            state.feedStations.firstOrNull { it.id == id }?.let { GeoPoint(it.latitude, it.longitude) }
        }
    }

    // The sheet's top edge (window y), once known: the station is then put in the middle of the map above it.
    var sheetTop by remember { mutableStateOf<Int?>(null) }
    // True when the station was picked from the nearest list (the map may be far away): zoom in on it.
    var zoomInOnStation by rememberSaveable { mutableStateOf(false) }
    var stationFocus by remember { mutableStateOf<StationFocus?>(null) }
    val centerOnStation = state.display.centerOnStation
    // `sheetTop` is only read inside snapshotFlow, never in composition: it changes on every frame while
    // the sheet slides in, and reading it here would recompose this whole screen each time.
    LaunchedEffect(selectedStation?.id, centerOnStation) {
        val station = selectedStation
        // Centring switched off in Settings: the map stays still for a station tapped on it. A station
        // chosen from the nearest list is still shown, since the map may be far from it.
        if (station == null || (!centerOnStation && !zoomInOnStation)) {
            stationFocus = null
            if (station == null) sheetTop = null
            return@LaunchedEffect
        }
        // One move per station: wait for the sheet's top edge to stop moving, then go once.
        var focused = false
        snapshotFlow { sheetTop }.filterNotNull().collectLatest { top ->
            if (focused) return@collectLatest
            delay(SHEET_SETTLE_MILLIS) // cancelled, and restarted, if the edge moves again meanwhile
            stationFocus = StationFocus(
                GeoPoint(station.latitude, station.longitude),
                top,
                if (zoomInOnStation) NEAREST_CHOSEN_ZOOM else null,
            )
            focused = true
        }
    }

    val loadingDescription = stringResource(R.string.status_loading)
    // Loading animation: while the first prices are awaited, or the map is not ready to report where it looks.
    var loadingGaveUp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_GIVE_UP_MILLIS) // offline with no map style: do not spin for ever
        loadingGaveUp = true
    }
    val loading = !loadingGaveUp &&
        ((state.stations.isEmpty() && state.isRefreshing) || (state.stations.isNotEmpty() && state.visibleArea == null))

    Box(Modifier.fillMaxSize()) {
        StationMapView(
            initialCamera = state.camera,
            geoJson = mapData.geoJson,
            userLocation = state.userLocation,
            placeNameOpacity = state.display.placeNameOpacity,
            stationSizeFactor = state.display.stationSizeFactor,
            placeNameSizeFactor = state.display.placeNameSizeFactor,
            areaToShow = state.areaToShow,
            onAreaShown = onAreaShown,
            stationFocus = stationFocus,
            lifecycle = lifecycle,
            onCameraIdle = onCameraIdle,
            onStationTapped = { zoomInOnStation = false; onStationTapped(it) },
        )
        // Tint of the status bar, over the map: light grey with a touch of the theme colour, translucent.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(lerp(STATUS_BAR_GREY, MaterialTheme.colorScheme.primary, STATUS_BAR_TINT).copy(alpha = STATUS_BAR_OPACITY)),
        )
        selectedStation?.let { station ->
            StationSheet(
                station = station,
                identity = state.stationNames[station.id],
                feedPosition = feedPosition,
                birds = state.birds,
                userLocation = state.userLocation,
                pricing = state.pricing,
                routing = state.routing,
                onRoadRoute = onRoadRoute,
                historyFuel = if (state.history.historyEnabled) state.selectedFuel else null,
                fuel = state.selectedFuel,
                nearest = state.nearest,
                onPriceHistory = onPriceHistory,
                onTopEdgeChanged = { sheetTop = it },
                onDismiss = onStationDismissed,
            )
        }
        if (loading) {
            // Centre of the screen; draws only, so the whole interface stays visible and usable.
            LoadingArrow(
                size = LOADING_ARROW_SIZE,
                modifier = Modifier.align(Alignment.Center).semantics { contentDescription = loadingDescription },
            )
        }
        // Under the fuel selector, at the top right.
        MenuButton(
            onPageChosen = { menuPage = it },
            modifier = Modifier.align(Alignment.TopEnd).insetsTopAndSides().padding(top = 52.dp, end = 8.dp),
        )
        // Right under the menu button (same size, 40 dp + 8 dp gap); the locate button follows.
        SearchButton(
            onClick = { searchOpen = true },
            modifier = Modifier.align(Alignment.TopEnd).insetsTopAndSides().padding(top = 100.dp, end = 8.dp),
        )
        NearestButton(
            enabled = state.userLocation != null,
            onClick = { nearestOpen = true },
            modifier = Modifier.align(Alignment.TopEnd).insetsTopAndSides().padding(top = 148.dp, end = 8.dp),
        )
        LocateButton(
            onClick = { locate(true) },
            // Fourth button of the column on the right, under the nearest-stations button.
            modifier = Modifier.align(Alignment.TopEnd).insetsTopAndSides().padding(top = 196.dp, end = 8.dp),
        )
        FuelSelector(
            selected = state.selectedFuel,
            onSelected = onFuelSelected,
            modifier = Modifier.align(Alignment.TopStart).insetsTopAndSides().padding(8.dp),
        )
        InfoPanel(
            state = state,
            onLegendExpandedChange = onLegendExpandedChange,
            // Right margin: keeps clear of the map's attribution button (bottom-right).
            modifier = Modifier
                .align(Alignment.BottomStart)
                .insetsBottomAndSides()
                .padding(start = 8.dp, end = 64.dp, bottom = 8.dp),
        )
        if (searchOpen) {
            AddressSearchPage(
                onSearch = onSearchAddress,
                // A station found by its identifier is shown like one picked in the nearest list: zoomed to if far.
                onChosen = { searchOpen = false; zoomInOnStation = it.stationId != null; onAddressChosen(it) },
                onClose = { searchOpen = false },
            )
        }
        val userLocation = state.userLocation
        if (nearestOpen && userLocation != null) {
            NearestStationsPage(
                stations = state.stations,
                names = state.stationNames,
                fuel = state.selectedFuel,
                origin = userLocation,
                settings = state.nearest,
                routing = state.routing,
                onRoadDistances = onRoadDistances,
                cachedRoadDistances = cachedRoadDistances,
                onChosen = { nearestOpen = false; zoomInOnStation = true; onNearestChosen(it) },
                onClose = { nearestOpen = false },
            )
        }
        // Info / Settings page laid over everything when open.
        menuPage?.let { page ->
            MenuPageHost(page = page, state = state, actions = menuActions, onClose = { menuPage = null })
        }
    }
}

/** Round button that opens the address search. The magnifier is drawn: no icon library needed. */
@Composable
private fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.search_open)
    val tint = MaterialTheme.colorScheme.onSurface
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            val radius = size.minDimension * 0.34f
            val lens = Offset(radius + stroke / 2, radius + stroke / 2)
            drawCircle(tint, radius = radius, center = lens, style = Stroke(width = stroke))
            // Handle: from the lens edge (45°) to the bottom-right corner.
            val edge = radius * 0.7071f
            drawLine(
                tint,
                Offset(lens.x + edge, lens.y + edge),
                Offset(size.width - stroke / 2, size.height - stroke / 2),
                stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/**
 * Round button that opens the list of the nearest stations; a right-pointing arrow, drawn. Greyed
 * out (and inert) while the user's position is unknown. The M3 FAB has no `enabled` parameter,
 * hence the faded tint and the no-op click.
 */
@Composable
private fun NearestButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.nearest_open)
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    SmallFloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = modifier.semantics {
            contentDescription = description
            if (!enabled) disabled()
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            val y = size.height / 2f
            val head = size.width * 0.4f
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            drawLine(tint, Offset(stroke / 2, y), Offset(size.width - stroke / 2, y), stroke, cap = cap)
            drawLine(tint, Offset(size.width - head, y - head), Offset(size.width - stroke / 2, y), stroke, cap = cap)
            drawLine(tint, Offset(size.width - head, y + head), Offset(size.width - stroke / 2, y), stroke, cap = cap)
        }
    }
}

/** Round button that centres the map on the user. The crosshair is drawn: no icon library needed. */
@Composable
private fun LocateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.locate_me)
    val tint = MaterialTheme.colorScheme.onSurface
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val centre = center
            val ring = size.minDimension * 0.28f
            val stroke = Stroke(width = 2.dp.toPx())
            drawCircle(tint, radius = ring, center = centre, style = stroke)
            drawCircle(tint, radius = ring * 0.4f, center = centre)
            val tick = size.minDimension / 2f
            drawLine(tint, Offset(centre.x, 0f), Offset(centre.x, tick - ring), stroke.width)
            drawLine(tint, Offset(centre.x, tick + ring), Offset(centre.x, size.height), stroke.width)
            drawLine(tint, Offset(0f, centre.y), Offset(tick - ring, centre.y), stroke.width)
            drawLine(tint, Offset(tick + ring, centre.y), Offset(size.width, centre.y), stroke.width)
        }
    }
}

/** Background of the fuel chips that are not selected. */
private val UNSELECTED_CHIP_GREY = Color(0xFFE0E0E0)

/** One chip per fuel, in a horizontally scrollable row. */
@Composable
private fun FuelSelector(selected: FuelType, onSelected: (FuelType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (fuel in FuelType.entries) {
            FilterChip(
                selected = fuel == selected,
                onClick = { onSelected(fuel) },
                label = { Text(stringResource(fuel.labelRes)) },
                // Unselected chips get a solid grey background so they stay readable over the
                // map; the selected chip keeps the theme's highlight colour.
                colors = FilterChipDefaults.filterChipColors(containerColor = UNSELECTED_CHIP_GREY),
            )
        }
    }
}

/**
 * The legend, with the problems that need the user's attention (location
 * not found) below it. Tapping the legend collapses it to a small dot.
 */
@Composable
private fun InfoPanel(state: UiState, onLegendExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        // Open on the first launch, then as the user left it (saved by the ViewModel).
        val legendExpanded = state.legendExpanded

        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Collapsed legend: only this small dot remains; tapping it brings the legend back.
            if (!legendExpanded) LegendDot(onClick = { onLegendExpandedChange(true) })
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (legendExpanded) Legend(pricing = state.pricing, onHide = { onLegendExpandedChange(false) })
                state.locateProblem?.let {
                    Text(
                        stringResource(
                            if (it == LocateProblem.PERMISSION_DENIED) R.string.location_denied
                            else R.string.location_unavailable,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * The legend: the four price bands (cheap → expensive), then the two grey cases (old price; shortage = grey
 * dot with a red badge). Tapping it hides it.
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow is still marked experimental in this Compose version
@Composable
private fun Legend(pricing: PricingSettings, onHide: () -> Unit) {
    // The whole legend is one tappable block: the title above, the symbols below.
    Column(
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.legend_hide), role = Role.Button, onClick = onHide),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.legend_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        // FlowRow: when the legend is wider than the screen, the rest continues on the next line
        // instead of being cut off.
        FlowRow(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Each group is a Row of its own, so a symbol never gets separated from its caption
            // when the legend wraps.
            LegendGroup {
                Text(stringResource(R.string.legend_cheaper), style = MaterialTheme.typography.bodyMedium)
                // The four bands, drawn with the very colours the dots use, so they cannot drift apart.
                Row(Modifier.clip(RoundedCornerShape(5.dp))) {
                    for (band in PriceBand.entries) Box(Modifier.width(14.dp).height(10.dp).background(Color(band.argb)))
                }
                Text(stringResource(R.string.legend_pricier), style = MaterialTheme.typography.bodyMedium)
            }
            LegendGroup {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(PriceColors.UNRELIABLE_ARGB)))
                Text(stringResource(R.string.legend_old_price), style = MaterialTheme.typography.bodyMedium)
            }
            LegendGroup {
                // A miniature of the map symbol: grey dot with the red badge at its top-right corner.
                Box(Modifier.size(14.dp)) {
                    Box(Modifier.size(10.dp).align(Alignment.BottomStart).clip(CircleShape).background(Color(PriceColors.UNRELIABLE_ARGB)))
                    Box(Modifier.size(6.dp).align(Alignment.TopEnd).clip(CircleShape).background(Color(PriceColors.SHORTAGE_BADGE_ARGB)))
                }
                Text(stringResource(R.string.legend_shortage), style = MaterialTheme.typography.bodyMedium)
            }
        }
        // What the two grey symbols mean in days, and a reminder that the thresholds are user settings.
        Text(
            stringResource(
                R.string.legend_thresholds_hint,
                pluralStringResource(R.plurals.legend_days, pricing.maxPriceAgeDays, pricing.maxPriceAgeDays),
                pluralStringResource(R.plurals.legend_days, pricing.maxShortageAgeDays, pricing.maxShortageAgeDays),
            ),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun LegendGroup(content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/**
 * The small dot that stands for the collapsed legend. It wears the four price colours (a
 * round split in four sectors) so that it is recognisable. The visible dot is small, but the
 * tappable area is 40 dp, the usual minimum for a finger.
 */
@Composable
private fun LegendDot(onClick: () -> Unit) {
    // Four equal sectors with sharp edges (two stops at each boundary).
    val bands = PriceBand.entries
    val stops = bands.flatMapIndexed { i, band ->
        listOf((i / 4f) to Color(band.argb), ((i + 1) / 4f) to Color(band.argb))
    }.toTypedArray()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClickLabel = stringResource(R.string.legend_show), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(Brush.sweepGradient(colorStops = stops)))
    }
}

/** The status bar over the map: this grey, mixed with [STATUS_BAR_TINT] of the theme's main colour, at this opacity. */
private val STATUS_BAR_GREY = Color(0xFFD0D0D0)
private const val STATUS_BAR_TINT = 0.18f
private const val STATUS_BAR_OPACITY = 0.45f
