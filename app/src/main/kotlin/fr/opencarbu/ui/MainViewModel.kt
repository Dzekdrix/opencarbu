package fr.opencarbu.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.opencarbu.R
import fr.opencarbu.data.AddressSearcher
import fr.opencarbu.data.DataSettingsPreference
import fr.opencarbu.data.GeoplatformAddressSearcher
import fr.opencarbu.data.FuelPreference
import fr.opencarbu.data.DailyRefreshJobService
import fr.opencarbu.data.HistorySettingsPreference
import fr.opencarbu.data.MapDisplaySettingsPreference
import fr.opencarbu.data.NotHistoryFileException
import fr.opencarbu.data.PriceHistoryStore
import fr.opencarbu.data.MoshiFeedParser
import fr.opencarbu.data.FeedTimingPreference
import fr.opencarbu.data.OkHttpFeedDownloader
import fr.opencarbu.data.TimedFeed
import fr.opencarbu.domain.model.FeedTiming
import fr.opencarbu.data.NearestSettingsPreference
import fr.opencarbu.data.OsrmRouteProvider
import fr.opencarbu.data.PricingSettingsPreference
import fr.opencarbu.data.RoutingSettingsPreference
import fr.opencarbu.data.FeedConfig
import fr.opencarbu.data.FeedSnapshot
import fr.opencarbu.data.StationNamesRepository
import fr.opencarbu.data.StationRepository
import fr.opencarbu.domain.birds.BirdListParser
import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.DataSettings
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.history.PricePoint
import fr.opencarbu.domain.model.HistorySettings
import fr.opencarbu.domain.model.MapDisplaySettings
import fr.opencarbu.domain.model.NearestSettings
import fr.opencarbu.domain.nearest.RoadDistanceCache
import fr.opencarbu.domain.model.RoutingSettings
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.pricing.NationalPriceScale
import fr.opencarbu.domain.pricing.PricingSettings
import fr.opencarbu.domain.pricing.ViewportPricing
import fr.opencarbu.domain.routing.OsrmTableRequest
import fr.opencarbu.domain.routing.RouteResult
import fr.opencarbu.domain.search.AddressResult
import fr.opencarbu.domain.search.StationIdQuery
import fr.opencarbu.domain.search.SEARCH_RADIUS_KM
import fr.opencarbu.ui.location.LocateProblem
import fr.opencarbu.ui.map.MapData
import fr.opencarbu.ui.map.MapDataFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat

/**
 * Holds the station data and the map settings for the screen, and survives screen rotations.
 *
 * Startup sequence:
 *  1. show the cached data immediately, if any (instant, works offline);
 *  2. download a fresh copy in the background unless the cache is recent.
 *
 * Whenever the stations, the selected fuel or the visible map area change, the dots to draw
 * are recomputed on a background thread and published in [mapData].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // The cache lives in cacheDir: it can be re-downloaded, so the system may clear it.
    private val repository = StationRepository(
        cacheFile = File(application.cacheDir, "stations-feed.json"),
        downloader = OkHttpFeedDownloader(onTimed = { onFeedTimed(TimedFeed.PRICES, it) }),
        parser = MoshiFeedParser,
        clock = System::currentTimeMillis,
    )

    // Optional names and brands of the stations; any failure here is silent.
    private val namesRepository = StationNamesRepository(
        cacheFile = File(application.cacheDir, "station-names.csv"),
        downloader = OkHttpFeedDownloader(url = FeedConfig.NAMES_URL, onTimed = { onFeedTimed(TimedFeed.NAMES, it) }),
        clock = System::currentTimeMillis,
    )

    // Small saved settings (selected fuel, age thresholds). SharedPreferences is built into
    // Android and light enough for a handful of values; no extra library is needed.
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val defaultFuel = FuelPreference.defaultFromStored(prefs.getString(FuelPreference.KEY_DEFAULT, null))

    private val mutableState = MutableStateFlow(
        UiState(
            defaultFuel = defaultFuel,
            legendExpanded = prefs.getBoolean(KEY_LEGEND_EXPANDED, true), // never saved = first launch = open
            selectedFuel = FuelPreference.startupFuel(defaultFuel, prefs.getString(FuelPreference.KEY, null)),
            pricing = PricingSettingsPreference.fromStored(
                maxPriceAgeDays = prefs.getInt(PricingSettingsPreference.KEY_MAX_PRICE_AGE_DAYS, 0),
                maxShortageAgeDays = prefs.getInt(PricingSettingsPreference.KEY_MAX_SHORTAGE_AGE_DAYS, 0),
            ), // 0 = "never saved": rejected by fromStored, so the default applies
            display = MapDisplaySettingsPreference.fromStored(
                // A negative default means "never saved": fromStored then applies its own default.
                prefs.getFloat(MapDisplaySettingsPreference.KEY_PLACE_NAME_OPACITY, -1f),
                prefs.getInt(MapDisplaySettingsPreference.KEY_START_RADIUS_KM, 0), // 0 = never saved
                prefs.getFloat(MapDisplaySettingsPreference.KEY_STATION_SIZE_FACTOR, 0f), // 0 = never saved
                prefs.getFloat(MapDisplaySettingsPreference.KEY_PLACE_NAME_SIZE_FACTOR, 0f), // 0 = never saved
                prefs.getBoolean(MapDisplaySettingsPreference.KEY_CENTER_ON_STATION, MapDisplaySettings.DEFAULT_CENTER_ON_STATION),
            ),
            history = HistorySettingsPreference.read(prefs),
            nearest = NearestSettingsPreference.fromStored(
                radiusKm = if (prefs.contains(NearestSettingsPreference.KEY_RADIUS_KM)) {
                    prefs.getInt(NearestSettingsPreference.KEY_RADIUS_KM, 0)
                } else {
                    null
                },
                consumption = if (prefs.contains(NearestSettingsPreference.KEY_CONSUMPTION)) {
                    prefs.getFloat(NearestSettingsPreference.KEY_CONSUMPTION, 0f)
                } else {
                    null
                },
                tankLitres = if (prefs.contains(NearestSettingsPreference.KEY_TANK_LITRES)) {
                    prefs.getInt(NearestSettingsPreference.KEY_TANK_LITRES, 0)
                } else {
                    null
                },
            ),
            routing = RoutingSettingsPreference.fromStored(
                enabled = if (prefs.contains(RoutingSettingsPreference.KEY_ENABLED)) {
                    prefs.getBoolean(RoutingSettingsPreference.KEY_ENABLED, false)
                } else {
                    null
                },
                serverUrl = prefs.getString(RoutingSettingsPreference.KEY_SERVER_URL, null),
            ),
            data = DataSettingsPreference.fromStored(
                prefs.getInt(DataSettingsPreference.KEY_REFRESH_INTERVAL_MINUTES, 0), // 0 = never saved
                prefs.getInt(DataSettingsPreference.KEY_NAMES_REFRESH_INTERVAL_DAYS, 0),
                if (prefs.contains(DataSettingsPreference.KEY_SHOW_REFERENCE_ONLY)) {
                    prefs.getBoolean(DataSettingsPreference.KEY_SHOW_REFERENCE_ONLY, false)
                } else {
                    null // never saved: default
                },
            ),
        ),
    )

    /** Prevents two simultaneous downloads. Only touched from the main thread (viewModelScope). */
    private var downloadRunning = false

    /** The state to display; collected by the UI. */
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    private val mutableMapData = MutableStateFlow(MapData())

    /** The dots to draw on the map; collected by the UI. */
    val mapData: StateFlow<MapData> = mutableMapData.asStateFlow()

    // Opened on first use only: nothing is created on the phone while the history stays off.
    // Declared before `init`, whose coroutines may start using it at once.
    private val historyStore by lazy { PriceHistoryStore.get(application) }

    /** A feed download just finished (on a background thread): keep how long it took, for the Info page. */
    private fun onFeedTimed(feed: TimedFeed, timing: FeedTiming) {
        FeedTimingPreference.write(prefs, feed, timing)
        mutableState.update {
            when (feed) {
                TimedFeed.PRICES -> it.copy(pricesTiming = timing)
                TimedFeed.NAMES -> it.copy(namesTiming = timing)
                TimedFeed.ROUTING -> it.copy(routingTiming = timing)
            }
        }
    }

    /** A client of the routing server that reports how long each request took. */
    private fun routeProvider(server: String) = OsrmRouteProvider(server, onTimed = { onFeedTimed(TimedFeed.ROUTING, it) })

    init {
        mutableState.update {
            it.copy(
                pricesTiming = FeedTimingPreference.read(prefs, TimedFeed.PRICES),
                namesTiming = FeedTimingPreference.read(prefs, TimedFeed.NAMES),
                routingTiming = FeedTimingPreference.read(prefs, TimedFeed.ROUTING),
            )
        }
        viewModelScope.launch { loadOnStartup() }
        viewModelScope.launch { loadNames() }
        viewModelScope.launch { loadBirds() }
        viewModelScope.launch { recomputeMapDataOnChanges() }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { DailyRefreshJobService.apply(application, mutableState.value.history) }
            refreshHistorySize()
            refreshBackgroundDate()
        }
    }

    // ---- Price history ----

    /** The user changed the history or background settings (Settings > Price history / Background). */
    fun setHistorySettings(newSettings: HistorySettings) {
        val old = mutableState.value.history
        mutableState.update { it.copy(history = newSettings) }
        HistorySettingsPreference.write(prefs, newSettings)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DailyRefreshJobService.apply(getApplication(), newSettings)
                if (newSettings.historyEnabled) {
                    val now = System.currentTimeMillis()
                    if (!old.historyEnabled) {
                        // Just switched on: start from the prices already on the phone.
                        historyStore.recordFrom(mutableState.value.feedStations, newSettings.retentionCutoff(now))
                    } else {
                        newSettings.retentionCutoff(now)?.let { historyStore.prune(it) } // a shorter retention applies now
                    }
                }
            }
            refreshHistorySize()
        }
    }

    /** Deletes every recorded price. */
    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.clear() }
            refreshHistorySize()
        }
    }

    /** Writes the price history as CSV to the file the user chose ([uri]), then says how it went. */
    fun exportHistory(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val message = try {
                val lines = withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Cannot open the file")
                    stream.bufferedWriter(Charsets.UTF_8).use { historyStore.exportCsv(it) }
                }
                app.getString(R.string.history_export_done, lines)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                app.getString(R.string.history_export_failed, e.message ?: e.javaClass.simpleName)
            }
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    /** Adds the history in the CSV file the user chose ([uri]) to the one on the phone, then says how it went. */
    fun importHistory(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            Toast.makeText(app, app.getString(R.string.history_import_started), Toast.LENGTH_SHORT).show()
            val message = try {
                val now = System.currentTimeMillis()
                val cutoff = mutableState.value.history.retentionCutoff(now)
                val result = withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Cannot open the file")
                    stream.bufferedReader(Charsets.UTF_8).use { historyStore.importCsv(it, cutoff, now) }
                }
                refreshHistorySize()
                app.getString(R.string.history_import_done, result.imported, result.skipped)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotHistoryFileException) {
                app.getString(R.string.history_import_failed, app.getString(R.string.history_import_not_history))
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                app.getString(R.string.history_import_failed, e.message ?: e.javaClass.simpleName)
            }
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    /** The recorded price changes of one station and fuel since [sinceEpochMillis] (for the sheet's curve). */
    suspend fun priceHistory(stationId: Long, fuel: FuelType, sinceEpochMillis: Long): List<PricePoint> =
        withContext(Dispatchers.IO) { historyStore.points(stationId, fuel, sinceEpochMillis) }

    private suspend fun refreshHistorySize() {
        val bytes = withContext(Dispatchers.IO) { historyStore.sizeBytes() }
        mutableState.update { it.copy(historyBytes = bytes) }
    }

    /** Reads when the background job last ran (it runs while the app is closed, so this is read on opening). */
    private suspend fun refreshBackgroundDate() {
        val last = withContext(Dispatchers.IO) { DailyRefreshJobService.lastRefreshMillis(getApplication()) }
        mutableState.update { it.copy(backgroundLastRefreshMillis = last) }
    }

    /** After a download: records the price changes when the history is on. */
    private suspend fun recordHistory(snapshot: FeedSnapshot) {
        val settings = mutableState.value.history
        if (!settings.historyEnabled) return
        try {
            withContext(Dispatchers.IO) {
                historyStore.recordFrom(snapshot.stations, settings.retentionCutoff(System.currentTimeMillis()))
            }
            refreshHistorySize()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // The history is a bonus: a storage problem must never look like a failed price download.
        }
    }

    /** The user picked another fuel. */
    fun selectFuel(fuel: FuelType) {
        mutableState.update { it.copy(selectedFuel = fuel) }
        // apply() writes in the background: it never blocks the screen.
        prefs.edit().putString(FuelPreference.KEY, FuelPreference.toStored(fuel)).apply()
    }

    /** The user opened or closed the legend: remember it for the next launches. */
    fun setLegendExpanded(expanded: Boolean) {
        mutableState.update { it.copy(legendExpanded = expanded) }
        prefs.edit().putBoolean(KEY_LEGEND_EXPANDED, expanded).apply()
    }

    /** Sets the fuel selected at startup; null = reopen on the last fuel used. */
    fun setDefaultFuel(fuel: FuelType?) {
        mutableState.update { it.copy(defaultFuel = fuel) }
        prefs.edit().putString(FuelPreference.KEY_DEFAULT, FuelPreference.defaultToStored(fuel)).apply()
    }

    /** Changes the age thresholds and redraws the map (called by the settings screen). */
    fun setPricingSettings(newSettings: PricingSettings) {
        mutableState.update { it.copy(pricing = newSettings) }
        prefs.edit()
            .putInt(PricingSettingsPreference.KEY_MAX_PRICE_AGE_DAYS, newSettings.maxPriceAgeDays)
            .putInt(PricingSettingsPreference.KEY_MAX_SHORTAGE_AGE_DAYS, newSettings.maxShortageAgeDays)
            .apply()
    }

    /** Changes the map appearance (called by the settings screen). */
    fun setDisplaySettings(newSettings: MapDisplaySettings) {
        mutableState.update { it.copy(display = newSettings) }
        prefs.edit()
            .putFloat(MapDisplaySettingsPreference.KEY_PLACE_NAME_OPACITY, newSettings.placeNameOpacity)
            .putInt(MapDisplaySettingsPreference.KEY_START_RADIUS_KM, newSettings.startRadiusKm)
            .putFloat(MapDisplaySettingsPreference.KEY_STATION_SIZE_FACTOR, newSettings.stationSizeFactor)
            .putFloat(MapDisplaySettingsPreference.KEY_PLACE_NAME_SIZE_FACTOR, newSettings.placeNameSizeFactor)
            .putBoolean(MapDisplaySettingsPreference.KEY_CENTER_ON_STATION, newSettings.centerOnStation)
            .apply()
    }

    /** The automatic search at startup has been launched; do not launch it again on rotation. */
    fun markStartupLocateDone() {
        mutableState.update { it.copy(startupLocateDone = true) }
    }

    /** The user was found: remember where, and ask the map to show the configured radius around. */
    fun onLocated(point: GeoPoint) {
        mutableState.update {
            it.copy(
                userLocation = point,
                areaToShow = BoundingBox.around(point, it.display.startRadiusKm.toDouble()),
                locateProblem = null,
            )
        }
    }

    private val addressSearcher: AddressSearcher = GeoplatformAddressSearcher()

    /**
     * Looks [query] up in the address geocoder (network, off the main thread).
     * @throws java.io.IOException when the service cannot be reached.
     */
    suspend fun searchAddress(query: String): List<AddressResult> {
        // A station identifier ("1120005") is looked up among the known stations: no request is sent.
        StationIdQuery.parse(query)?.let { id ->
            val station = mutableState.value.stations.firstOrNull { it.id == id }
            if (station != null) return listOf(stationResult(station))
        }
        return withContext(Dispatchers.IO) { addressSearcher.search(query) }
    }

    /** A station found by its identifier, as a search result: "Station 1120005 · Name · 01120 Dagneux". */
    private fun stationResult(station: Station): AddressResult {
        val identity = mutableState.value.stationNames[station.id]
        val name = identity?.lines?.joinToString(" ")?.takeIf { it.isNotBlank() } ?: station.address.ifBlank { station.city }
        val place = "${identity?.postalCode ?: station.postalCode} ${identity?.city ?: station.city}".trim()
        val label = getApplication<Application>().getString(R.string.search_station_result, station.id.toString(), name, place)
        return AddressResult(label, GeoPoint(station.latitude, station.longitude), station.id)
    }

    /** The user picked a search result: close any open sheet and show the area around it. */
    fun onAddressChosen(result: AddressResult) {
        val stationId = result.stationId
        if (stationId != null) {
            // A station found by its identifier: open its sheet; the map moves to it with the sheet.
            mutableState.update { it.copy(selectedStationId = stationId) }
            return
        }
        mutableState.update {
            it.copy(
                areaToShow = BoundingBox.around(result.point, SEARCH_RADIUS_KM),
                selectedStationId = null,
            )
        }
    }

    /** The user could not be found; [problem] is displayed until the next attempt succeeds. */
    fun onLocateProblem(problem: LocateProblem) {
        mutableState.update { it.copy(locateProblem = problem) }
    }

    /** Changes how often the data is refreshed at opening (called by the settings screen). */
    fun setDataSettings(newSettings: DataSettings) {
        // The reference-only stations are added to (or removed from) the list shown on the map.
        mutableState.update { it.copy(data = newSettings).withCorrectedPositions() }
        prefs.edit()
            .putBoolean(DataSettingsPreference.KEY_SHOW_REFERENCE_ONLY, newSettings.showReferenceOnlyStations)
            .putInt(DataSettingsPreference.KEY_REFRESH_INTERVAL_MINUTES, newSettings.refreshIntervalMinutes)
            .putInt(DataSettingsPreference.KEY_NAMES_REFRESH_INTERVAL_DAYS, newSettings.namesRefreshIntervalDays)
            .apply()
    }

    /** Switches the road distance on or off, or changes its server (called by the settings screen). */
    fun setRoutingSettings(newSettings: RoutingSettings) {
        mutableState.update { it.copy(routing = newSettings) }
        prefs.edit()
            .putBoolean(RoutingSettingsPreference.KEY_ENABLED, newSettings.enabled)
            .putString(RoutingSettingsPreference.KEY_SERVER_URL, newSettings.serverUrl)
            .apply()
    }

    /** Changes the radius or the consumption of the "nearest stations" list. */
    fun setNearestSettings(newSettings: NearestSettings) {
        mutableState.update { it.copy(nearest = newSettings) }
        prefs.edit()
            .putInt(NearestSettingsPreference.KEY_RADIUS_KM, newSettings.radiusKm)
            .putFloat(NearestSettingsPreference.KEY_CONSUMPTION, newSettings.consumptionLitresPer100Km)
            .putInt(NearestSettingsPreference.KEY_TANK_LITRES, newSettings.tankLitres)
            .apply()
    }

    /** A station was picked in the "nearest stations" list: show it on the map and open its sheet. */
    fun onNearestChosen(station: Station) {
        // The map moves to the station when the sheet opens (see StationFocus), no area to show here.
        mutableState.update { it.copy(selectedStationId = station.id) }
    }

    // Road distances already asked for, so reopening a sheet costs no request. Small, oldest dropped first.
    private val routeCache = object : LinkedHashMap<String, RouteResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RouteResult>?) = size > ROUTE_CACHE_SIZE
    }
    private val routeLock = Mutex()

    /** Road distances of the nearest-stations list, kept while the user stays within 100 m. */
    private val roadDistanceCache = RoadDistanceCache()
    private var lastRouteRequestAtMillis = 0L

    /**
     * Driving distance and time from [from] to [to] with the configured server. Answers come from a
     * small memory cache when possible; real requests are spaced by at least one second, as the
     * public server asks. Does nothing and fails if the feature is off.
     * @throws java.io.IOException when the server cannot give a route.
     */
    suspend fun roadRoute(from: fr.opencarbu.domain.model.GeoPoint, to: fr.opencarbu.domain.model.GeoPoint): RouteResult {
        val settings = mutableState.value.routing
        if (!settings.enabled) throw java.io.IOException("Road distance is off")
        // About 100 m of precision on the user's position is enough for the cache key.
        val key = "${settings.serverUrl}|%.3f,%.3f|%.5f,%.5f".format(java.util.Locale.ROOT, from.latitude, from.longitude, to.latitude, to.longitude)
        return routeLock.withLock {
            routeCache[key]?.let { return@withLock it }
            val wait = lastRouteRequestAtMillis + MIN_ROUTE_REQUEST_SPACING_MILLIS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRouteRequestAtMillis = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) { routeProvider(settings.serverUrl).route(from, to) }
            routeCache[key] = result
            result
        }
    }

    /**
     * Road distances and times from [origin] to every one of [destinations], with the configured
     * server: one "table" request per ~99 destinations, spaced like [roadRoute]'s requests.
     * @throws java.io.IOException when the feature is off or the server cannot answer.
     */
    suspend fun roadDistances(
        origin: fr.opencarbu.domain.model.GeoPoint,
        destinations: List<Pair<Long, fr.opencarbu.domain.model.GeoPoint>>,
    ): List<RouteResult?> {
        val settings = mutableState.value.routing
        if (!settings.enabled) throw java.io.IOException("Road distance is off")
        val server = settings.serverUrl
        val ids = destinations.map { it.first }
        // Only what is not remembered (for about this position) is asked of the server.
        val missing = roadDistanceCache.missing(origin, server, ids).toSet()
        val toAsk = destinations.filter { it.first in missing }
        for (chunk in toAsk.chunked(OsrmTableRequest.MAX_COORDINATES - 1)) {
            val answers = routeLock.withLock {
                val wait = lastRouteRequestAtMillis + MIN_ROUTE_REQUEST_SPACING_MILLIS - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastRouteRequestAtMillis = System.currentTimeMillis()
                withContext(Dispatchers.IO) { routeProvider(server).table(origin, chunk.map { it.second }) }
            }
            roadDistanceCache.put(origin, server, chunk.map { it.first }.zip(answers).toMap())
        }
        return roadDistanceCache.all(origin, server, ids) ?: List(ids.size) { null }
    }

    /**
     * The road distances of [ids] if they are all remembered for about this position (within
     * 100 m), else null: lets the nearest-stations list reopen at once, without any request.
     */
    fun cachedRoadDistances(origin: fr.opencarbu.domain.model.GeoPoint, ids: List<Long>): List<RouteResult?>? {
        val settings = mutableState.value.routing
        if (!settings.enabled) return null
        return roadDistanceCache.all(origin, settings.serverUrl, ids)
    }

    /** Puts every setting of the Settings page back to its default value. */
    fun resetSettings() {
        setNearestSettings(NearestSettings())
        setRoutingSettings(RoutingSettings())
        setPricingSettings(PricingSettings())
        setDisplaySettings(MapDisplaySettings())
        setDataSettings(DataSettings())
        setHistorySettings(HistorySettings()) // the recorded prices stay; only the settings go back to off
        setDefaultFuel(null)
    }

    /**
     * The app came back to the screen (first opening, or returned to after being in the
     * background): download the prices again if the data on the phone is older than the
     * configured interval. A process kept alive in the background does not restart this
     * ViewModel, so this must be called each time the screen starts, not only at launch.
     */
    fun onAppOpened() {
        if (!startupLoadDone) return // the first opening is handled right after the cache is read
        viewModelScope.launch { downloadIfStale() }
        viewModelScope.launch { refreshNamesIfStale() }
        viewModelScope.launch { refreshBackgroundDate() }
    }

    /** The user tapped a station dot: open its sheet. */
    fun onStationTapped(id: Long) {
        mutableState.update { it.copy(selectedStationId = id) }
    }

    /** The station sheet was closed. The map stays where it is: closing a sheet never moves it. */
    fun onStationDismissed() {
        mutableState.update { it.copy(selectedStationId = null) }
    }

    /** The map has fitted the requested area. */
    fun onAreaShown() {
        mutableState.update { it.copy(areaToShow = null) }
    }

    /** The map stopped moving: [area] is what is now on screen, [camera] where it looks. */
    fun onCameraIdle(area: BoundingBox, camera: MapCamera) {
        mutableState.update { it.copy(visibleArea = area, camera = camera) }
    }

    /** Forces a download now (the "Refresh" button). Ignored if one is already running. */
    fun refresh() {
        viewModelScope.launch { download(force = true) }
    }

    /** The three inputs that decide which dots are drawn. */
    private class MapInput(
        val stations: List<Station>,
        val fuel: FuelType,
        val area: BoundingBox?,
        val pricing: PricingSettings,
    )

    /**
     * Rebuilds [mapData] each time its inputs change. `collectLatest` abandons a computation
     * still running when a newer request arrives (the user keeps panning), so the map never
     * waits on stale work.
     */
    private suspend fun recomputeMapDataOnChanges() {
        mutableState
            .map { MapInput(it.stations, it.selectedFuel, it.visibleArea, it.pricing) }
            // Compare the station list by identity: it is replaced as a whole on each download,
            // and comparing ~10 000 stations one by one on every camera move would be wasteful.
            .distinctUntilChanged { a, b -> a.stations === b.stations && a.fuel == b.fuel && a.area == b.area && a.pricing == b.pricing
            }
            .collectLatest { input ->
                val area = input.area ?: return@collectLatest // the map has not reported its position yet
                mutableMapData.value = withContext(Dispatchers.Default) {
                    // NumberFormat is not thread-safe, so each computation gets its own.
                    val priceFormat = NumberFormat.getInstance().apply {
                        minimumFractionDigits = PRICE_DECIMALS
                        maximumFractionDigits = PRICE_DECIMALS
                    }
                    val now = System.currentTimeMillis()
                    MapDataFactory.build(
                        input.stations, input.fuel, area, now,
                        formatPrice = { priceFormat.format(it) },
                        settings = input.pricing,
                        scale = nationalScaleFor(input, now),
                    )
                }
            }
    }

    /** A national price scale and what it was built from. Replaced as a whole, never edited. */
    private class RememberedScale(
        val stations: List<Station>,
        val fuel: FuelType,
        val pricing: PricingSettings,
        val builtAtMillis: Long,
        val scale: NationalPriceScale?,
    )

    @Volatile
    private var rememberedScale: RememberedScale? = null

    /**
     * The national price scale for [input]. It does not depend on the visible area, so it is
     * built once (a pass over ~10 000 stations plus a sort) and reused while panning and zooming;
     * rebuilt when the stations, the fuel or the age thresholds change, and at least hourly
     * (prices grow old as time passes).
     */
    private fun nationalScaleFor(input: MapInput, now: Long): NationalPriceScale? {
        rememberedScale?.let {
            if (it.stations === input.stations && it.fuel == input.fuel && it.pricing == input.pricing &&
                now - it.builtAtMillis < SCALE_MAX_AGE_MILLIS
            ) {
                return it.scale
            }
        }
        val scale = ViewportPricing.nationalScale(
            input.stations, input.fuel, now, input.pricing.maxPriceAgeMillis, input.pricing.maxShortageAgeMillis,
        )
        rememberedScale = RememberedScale(input.stations, input.fuel, input.pricing, now, scale)
        return scale
    }

    /** Becomes true once the saved data has been read at startup; only touched from the main thread. */
    private var startupLoadDone = false

    private suspend fun loadOnStartup() {
        // File reading and JSON parsing are blocking: never do them on the main thread.
        val cached = withContext(Dispatchers.IO) { repository.loadCache() }
        if (cached != null) mutableState.update { it.withSnapshot(cached) }

        startupLoadDone = true
        mutableState.update { it.copy(isRefreshing = false) } // downloadIfStale() sets it again if it downloads
        downloadIfStale()
    }

    /** Reads the bird list shipped with the app. If it is unreadable the sheet uses a built-in bird. */
    private suspend fun loadBirds() {
        val birds = withContext(Dispatchers.IO) {
            try {
                getApplication<Application>().assets.open("birds.tsv").bufferedReader(Charsets.UTF_8).use(BirdListParser::parse)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                emptyList()
            }
        }
        mutableState.update { it.copy(birds = birds) }
    }

    /** Forces a download of the names file now (the "Refresh names" button). */
    fun refreshNames() {
        viewModelScope.launch { downloadNames(force = true) }
    }

    /** Shows the saved names at once, then downloads a newer file if the copy is older than the setting. */
    private suspend fun loadNames() {
        val cached = withContext(Dispatchers.IO) { namesRepository.loadCache() }
        if (cached != null) {
            val savedAt = withContext(Dispatchers.IO) { namesRepository.cacheDownloadedAt() }
            mutableState.update { it.copy(stationNames = cached, namesFetchedAtMillis = savedAt).withCorrectedPositions() }
        }
        downloadNames(force = false)
    }

    private suspend fun refreshNamesIfStale() = downloadNames(force = false)

    private var namesDownloadRunning = false

    /** The failure toast is shown once per launch for automatic downloads (the Settings text stays). */
    private var namesFailureToasted = false

    private suspend fun downloadNames(force: Boolean) {
        if (namesDownloadRunning) return
        namesDownloadRunning = true
        try {
            if (!force) {
                val downloadedAt = withContext(Dispatchers.IO) { namesRepository.cacheDownloadedAt() }
                if (!mutableState.value.data.isNamesStale(downloadedAt, System.currentTimeMillis())) return
            }
            mutableState.update { it.copy(isRefreshingNames = true, namesError = null) }
            val names = withContext(Dispatchers.IO) { namesRepository.refresh() }
            val savedAt = withContext(Dispatchers.IO) { namesRepository.cacheDownloadedAt() }
            mutableState.update { it.copy(stationNames = names, namesFetchedAtMillis = savedAt, isRefreshingNames = false).withCorrectedPositions() }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // The previous names (if any) stay in use. Tell the user why, with the real error.
            val reason = listOfNotNull(e.javaClass.simpleName, e.message).joinToString(": ")
            mutableState.update { it.copy(isRefreshingNames = false, namesError = reason) }
            if (force || !namesFailureToasted) {
                namesFailureToasted = true
                Toast.makeText(
                    getApplication<Application>(),
                    getApplication<Application>().getString(R.string.names_download_failed, reason),
                    Toast.LENGTH_LONG,
                ).show()
            }
        } finally {
            namesDownloadRunning = false
        }
    }

    /** Downloads the prices unless the data we hold is younger than the configured interval. */
    private suspend fun downloadIfStale() {
        val current = mutableState.value
        if (current.data.isStale(current.fetchedAtMillis, System.currentTimeMillis())) download()
    }

    /** Like [namesFailureToasted]: automatic downloads tell about a failure once per launch. */
    private var pricesFailureToasted = false

    private suspend fun download(force: Boolean = false) {
        if (downloadRunning) return
        downloadRunning = true
        mutableState.update { it.copy(isRefreshing = true, errorMessage = null) }
        try {
            val snapshot = withContext(Dispatchers.IO) { repository.refresh() }
            mutableState.update { it.withSnapshot(snapshot).copy(isRefreshing = false) }
            recordHistory(snapshot)
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Network down, server error, invalid data... The previous data stays displayed.
            val reason = e.message ?: e.javaClass.simpleName
            mutableState.update { it.copy(isRefreshing = false, errorMessage = reason) }
            if (force || !pricesFailureToasted) {
                pricesFailureToasted = true
                Toast.makeText(
                    getApplication<Application>(),
                    getApplication<Application>().getString(R.string.prices_download_failed, reason),
                    Toast.LENGTH_LONG,
                ).show()
            }
        } finally {
            downloadRunning = false
        }
    }

    private companion object {
        const val KEY_LEGEND_EXPANDED = "legend_expanded"
        const val ROUTE_CACHE_SIZE = 50

        /** The public routing server asks for at most one request per second. */
        const val MIN_ROUTE_REQUEST_SPACING_MILLIS = 1_100L

        /** Prices are quoted to the thousandth of a euro: "1,789". */
        const val PRICE_DECIMALS = 3

        /** The remembered national price scale is rebuilt after this long (one hour). */
        const val SCALE_MAX_AGE_MILLIS = 60L * 60 * 1000
    }
}
