package fr.opencarbu.ui

import fr.opencarbu.domain.model.FeedTiming
import fr.opencarbu.data.FeedSnapshot
import fr.opencarbu.domain.birds.Bird
import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.DataSettings
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.HistorySettings
import fr.opencarbu.domain.model.MapDisplaySettings
import fr.opencarbu.domain.model.NearestSettings
import fr.opencarbu.domain.model.RoutingSettings
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.model.StationPositions
import fr.opencarbu.domain.model.StationIdentity
import fr.opencarbu.domain.pricing.PricingSettings
import fr.opencarbu.ui.location.LocateProblem

/** Everything the screen needs to draw itself. Immutable: the ViewModel publishes a new copy on each change. */
data class UiState(
    val stations: List<Station> = emptyList(),
    /** The stations exactly as the price feed gave them; [stations] is this list with corrected positions. */
    val feedStations: List<Station> = emptyList(),
    /** When the displayed data was downloaded (epoch millis), or null if there is none yet. */
    val fetchedAtMillis: Long? = null,
    /** Records of the feed that could not be used (no id or position). Diagnostic only. */
    val skippedRecords: Int = 0,
    /** True while a download is in progress. */
    val isRefreshing: Boolean = true,
    /** Message of the last failed download, or null. Cached data, if any, stays displayed. */
    val errorMessage: String? = null,
    /** The fuel whose prices the map shows. */
    val selectedFuel: FuelType = FuelType.DIESEL,
    /** Fuel to select at startup (setting), or null for "the last fuel used". */
    val defaultFuel: FuelType? = null,
    /** Age thresholds for prices and shortages (user-configurable once the settings screen exists). */
    val pricing: PricingSettings = PricingSettings(),
    /** The part of the map on screen, or null until the map has reported it. */
    val visibleArea: BoundingBox? = null,
    /** Last known camera, so the map reopens where the user left it (e.g. after a rotation). */
    val camera: MapCamera = MapCamera.FRANCE,
    /** Map appearance settings (place-name opacity...). */
    val display: MapDisplaySettings = MapDisplaySettings(),
    /** Where the user is, or null while unknown. */
    val userLocation: GeoPoint? = null,
    /** An area the map must fit on screen; the map clears it (onAreaShown) once done. */
    val areaToShow: BoundingBox? = null,
    /** Why the last "find me" request failed, or null. */
    val locateProblem: LocateProblem? = null,
    /** How the station data is kept up to date. */
    val routing: RoutingSettings = RoutingSettings(),
    val nearest: NearestSettings = NearestSettings(),
    val data: DataSettings = DataSettings(),
    /** Price history and daily background refresh (both off until the user switches them on). */
    val history: HistorySettings = HistorySettings(),
    /** Space the price history takes on the phone, in bytes. */
    val historyBytes: Long = 0L,
    /** How long the last download of the prices and of the names file took, null before the first one. */
    val pricesTiming: FeedTiming? = null,
    val namesTiming: FeedTiming? = null,
    /** The last request to the routing server (road distances), null if none yet. */
    val routingTiming: FeedTiming? = null,
    /** When the background job last ran to the end (epoch millis), null if never. */
    val backgroundLastRefreshMillis: Long? = null,
    /** The station whose sheet is open, or null. */
    val selectedStationId: Long? = null,
    /**
     * Whether the map legend is open. Open on the very first launch so new users see what the colours
     * mean; after that it stays as the user last left it (saved).
     */
    val legendExpanded: Boolean = true,
    /** Names and brands by station id; empty until the reference file is loaded (or if unavailable). */
    val stationNames: Map<Long, StationIdentity> = emptyMap(),
    /** Birds for the playful distance on the station sheet; empty until the list is read. */
    val birds: List<Bird> = emptyList(),
    /** When the names file was last downloaded (epoch millis), or null if there is none. */
    val namesFetchedAtMillis: Long? = null,
    /** Why the last download of the names file failed, or null. */
    val namesError: String? = null,
    /** True while the names file is being downloaded. */
    val isRefreshingNames: Boolean = false,
    /** True once the automatic search at startup has been launched (a rotation must not redo it). */
    val startupLocateDone: Boolean = false,
) {
    /** Copy of this state showing [snapshot]. */
    fun withSnapshot(snapshot: FeedSnapshot): UiState = copy(
        feedStations = snapshot.stations,
        fetchedAtMillis = snapshot.fetchedAtMillis,
        skippedRecords = snapshot.skippedRecords,
    ).withCorrectedPositions()

    /** Recomputes [stations] from [feedStations] and the OpenStreetMap positions in [stationNames]. */
    fun withCorrectedPositions(): UiState = copy(stations = StationPositions.apply(feedStations, stationNames, data.showReferenceOnlyStations))
}
