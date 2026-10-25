package fr.opencarbu.ui.map

import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.MapDisplaySettings
import fr.opencarbu.ui.MapCamera
import android.graphics.PointF
import android.graphics.RectF
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMap.OnCameraMoveStartedListener
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Point

/**
 * Asks the map to put a station in the middle of the part of the map that stays visible above
 * the station sheet.
 *
 * @property sheetTopInWindowPx y of the top edge of the sheet, in window coordinates.
 * @property zoomInTo if the map is zoomed out more than this, it zooms in to it; null keeps the zoom.
 */
data class StationFocus(val position: GeoPoint, val sheetTopInWindowPx: Int, val zoomInTo: Double?)

/**
 * Glue between the MapLibre map and the rest of the app. It owns the map objects, so that
 * the Compose code stays free of map details.
 *
 * Flowing in (each is applied as soon as the map style is loaded, and remembered until then):
 *  - the dots to draw ([setGeoJson]);
 *  - the user's position ([setUserLocation]);
 *  - the opacity of the place names ([setPlaceNameOpacity]);
 *  - the size of the dots and prices ([setStationSizeFactor]);
 *  - the size of the place names ([setPlaceNameSizeFactor]);
 *  - a request to show an area ([showArea]);
 *  - a request to put a station above the sheet ([focusStation]); nothing undoes it when the sheet closes.
 *
 * Flowing out: [onCameraIdle], called when the user stops moving the map, with the visible area,
 * and [onStationTapped], called with the id of the station whose dot the user tapped.
 *
 * Everything here runs on the main thread (MapLibre requires it).
 *
 * @param onCameraIdle receives the visible area and the camera position.
 * @param onStationTapped receives the id of the tapped station.
 */
class StationMapController(
    private val onCameraIdle: (BoundingBox, MapCamera) -> Unit,
    private val onStationTapped: (Long) -> Unit,
) {
    private var map: MapLibreMap? = null
    private var mapView: MapView? = null

    /** Null until the style (background map) has finished loading. */
    private var style: Style? = null

    /** Latest dots requested; kept so they can be applied once the style is ready. */
    private var geoJson: String = StationGeoJson.EMPTY
    private var appliedGeoJson: String? = null

    private var userLocation: GeoPoint? = null
    private var appliedUserGeoJson: String? = null

    private var placeNameOpacity: Float = MapDisplaySettings.DEFAULT_PLACE_NAME_OPACITY
    private var appliedPlaceNameOpacity: Float? = null

    private var stationSizeFactor: Float = MapDisplaySettings.DEFAULT_STATION_SIZE_FACTOR
    private var appliedStationSizeFactor: Float? = null

    private var placeNameSizeFactor: Float = MapDisplaySettings.DEFAULT_PLACE_NAME_SIZE_FACTOR
    private var appliedPlaceNameSizeFactor: Float? = null

    /**
     * The area last handed to the app. After a station focus that only slides the map (no zoom change),
     * the same area is handed again: the strip uncovered at the bottom lies under the station sheet,
     * so rebuilding the dots for it would only cost time while the sheet opens. [areaIsStale] says
     * the map really shows a different area; [refreshArea] brings the app up to date once the sheet is gone.
     */
    private var lastReportedArea: BoundingBox? = null
    private var keepReportedArea = false
    private var areaIsStale = false

    /** Size of the navigation bar the map draws behind, so the attribution button stays clear of it. */
    private var rightInsetPx = 0
    private var bottomInsetPx = 0
    private var baseAttributionMarginRight: Int? = null
    private var baseAttributionMarginBottom: Int? = null

    /** An area to bring into view, waiting for the map to be ready. */
    private var pendingArea: BoundingBox? = null

    /** Starts loading the map in [mapView] and puts the camera at [initialCamera]. */
    fun attach(mapView: MapView, initialCamera: MapCamera) {
        this.mapView = mapView
        mapView.getMapAsync { map ->
            this.map = map
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(initialCamera.latitude, initialCamera.longitude))
                .zoom(initialCamera.zoom)
                .build()

            // Rotation and tilt are not useful here, and a rotated map would make the
            // "visible area" a rotated rectangle instead of the simple box the app uses.
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.isCompassEnabled = false
            // The attribution button (OpenStreetMap, OpenFreeMap...) stays enabled: it is
            // required by the licence of the map data. Only the logo is hidden.
            map.uiSettings.isLogoEnabled = false
            applyBarInsets()

            map.addOnCameraIdleListener { reportCamera() }
            // The user taking hold of the map means the real area is wanted again.
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == OnCameraMoveStartedListener.REASON_API_GESTURE) keepReportedArea = false
            }
            val tapRadiusPx = TAP_RADIUS_DP * mapView.resources.displayMetrics.density
            map.addOnMapClickListener { tapped -> handleTap(map, tapped, tapRadiusPx) }
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { loadedStyle ->
                style = loadedStyle
                StationLayers.install(loadedStyle, placeNameOpacity, stationSizeFactor, placeNameSizeFactor)
                appliedPlaceNameSizeFactor = placeNameSizeFactor
                appliedPlaceNameOpacity = placeNameOpacity
                appliedStationSizeFactor = stationSizeFactor
                appliedGeoJson = null
                appliedUserGeoJson = null
                applyGeoJson()
                applyUserLocation()
                applyPendingArea()
                reportCamera() // the camera may not move again, but the app needs the first area
            }
        }
    }

    /**
     * The map is drawn behind the navigation bar: moves the attribution button away from the bar, which is at the
     * bottom (portrait) or on one side (landscape, 3-button navigation).
     */
    fun setBarInsets(rightPx: Int, bottomPx: Int) {
        rightInsetPx = rightPx
        bottomInsetPx = bottomPx
        applyBarInsets()
    }

    private fun applyBarInsets() {
        val ui = map?.uiSettings ?: return
        val baseRight = baseAttributionMarginRight ?: ui.attributionMarginRight.also { baseAttributionMarginRight = it }
        val baseBottom = baseAttributionMarginBottom ?: ui.attributionMarginBottom.also { baseAttributionMarginBottom = it }
        ui.setAttributionMargins(ui.attributionMarginLeft, ui.attributionMarginTop, baseRight + rightInsetPx, baseBottom + bottomInsetPx)
    }

    /** Sets the dots to display. Cheap to call repeatedly with the same text. */
    fun setGeoJson(newGeoJson: String) {
        geoJson = newGeoJson
        applyGeoJson()
    }

    /** Sets the "you are here" marker; null removes it. Cheap to call repeatedly. */
    fun setUserLocation(location: GeoPoint?) {
        userLocation = location
        applyUserLocation()
    }

    /** Sets the opacity of the place names (0.0 to 1.0). Cheap to call repeatedly. */
    fun setPlaceNameOpacity(opacity: Float) {
        placeNameOpacity = opacity
        val style = style ?: return // installed with this value once the style loads
        if (opacity == appliedPlaceNameOpacity) return
        StationLayers.setPlaceNameOpacity(style, opacity)
        appliedPlaceNameOpacity = opacity
    }

    /** Sets the size factor of dots, badges and prices (1.0 = standard). Cheap to call repeatedly. */
    fun setStationSizeFactor(factor: Float) {
        stationSizeFactor = factor
        val style = style ?: return // installed with this value once the style loads
        if (factor == appliedStationSizeFactor) return
        StationLayers.setStationSizeFactor(style, factor)
        appliedStationSizeFactor = factor
    }

    /** Sets the size factor of the place names (1.0 = standard). Cheap to call repeatedly. */
    fun setPlaceNameSizeFactor(factor: Float) {
        placeNameSizeFactor = factor
        val style = style ?: return // installed with this value once the style loads
        if (factor == appliedPlaceNameSizeFactor) return
        StationLayers.setPlaceNameSizeFactor(style, factor)
        appliedPlaceNameSizeFactor = factor
    }

    /** Moves the camera so that the whole [area] is visible (with a short animation). */
    fun showArea(area: BoundingBox) {
        pendingArea = area
        applyPendingArea()
    }

    /**
     * Moves the camera so that [focus]'s station sits in the middle of the map part above the sheet
     * (between the top of the map and the top of the sheet), keeping the zoom unless [StationFocus.zoomInTo]
     * asks for more.
     */
    fun focusStation(focus: StationFocus) {
        val map = map ?: return
        val view = mapView ?: return
        if (style == null || view.height <= 0) return
        val mapTop = IntArray(2).also { view.getLocationInWindow(it) }[1]
        val visibleHeight = (focus.sheetTopInWindowPx - mapTop).coerceIn(1, view.height)
        // The station must appear `shift` pixels above the centre of the map.
        val shift = view.height / 2.0 - visibleHeight / 2.0

        val currentZoom = map.cameraPosition.zoom
        val zoom = maxOf(currentZoom, focus.zoomInTo ?: currentZoom)
        // Degrees of latitude per screen pixel, measured on the map itself (two points 200 px apart
        // around the centre) so that it is right whatever the screen density; one zoom level changes it
        // by a factor of two. The camera centre goes `shift` pixels' worth of latitude south of the station.
        val projection = map.projection
        val centreX = view.width / 2f
        val centreY = view.height / 2f
        val above = projection.fromScreenLocation(PointF(centreX, centreY - SCALE_SAMPLE_PX / 2))
        val below = projection.fromScreenLocation(PointF(centreX, centreY + SCALE_SAMPLE_PX / 2))
        val degreesPerPixel = (above.latitude - below.latitude) / SCALE_SAMPLE_PX *
            Math.pow(2.0, currentZoom - zoom)
        val latitudeShift = shift * degreesPerPixel

        val target = CameraPosition.Builder()
            .target(LatLng(focus.position.latitude - latitudeShift, focus.position.longitude))
            .zoom(zoom)
            .build()
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(target),
            FOCUS_ANIMATION_MILLIS,
            object : MapLibreMap.CancelableCallback {
                override fun onCancel() { keepReportedArea = false } // interrupted: the real area is wanted
                override fun onFinish() {}
            },
        )
        // After the call: starting an animation cancels the previous one, whose onCancel clears the flag.
        // Only a small slide qualifies: same zoom, and a station already on screen in an area the app really
        // has. A station chosen from the nearest list can be far away: the dots must then follow the map.
        val known = lastReportedArea
        keepReportedArea = zoom <= currentZoom && !areaIsStale && known != null &&
            known.contains(focus.position.latitude, focus.position.longitude)
    }

    /** Hands the app the area really shown, if the last report was kept back (see [lastReportedArea]). */
    fun refreshArea() {
        if (!areaIsStale) return
        keepReportedArea = false
        reportCamera()
    }

    private fun applyGeoJson() {
        val style = style ?: return
        if (geoJson == appliedGeoJson) return // `==` on the same String instance returns at once
        StationLayers.setData(style, geoJson)
        appliedGeoJson = geoJson
    }

    private fun applyUserLocation() {
        val style = style ?: return
        val userGeoJson = StationGeoJson.userPoint(userLocation)
        if (userGeoJson == appliedUserGeoJson) return
        StationLayers.setUserLocation(style, userGeoJson)
        appliedUserGeoJson = userGeoJson
    }

    /**
     * Only once the style is loaded: by then the map has its on-screen size, which the zoom
     * computation needs. Before that, the request simply waits in [pendingArea].
     */
    private fun applyPendingArea() {
        val map = map ?: return
        if (style == null) return
        val area = pendingArea ?: return
        pendingArea = null
        val bounds = LatLngBounds.Builder()
            .include(LatLng(area.south, area.west))
            .include(LatLng(area.north, area.east))
            .build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0), CAMERA_ANIMATION_MILLIS)
    }

    /**
     * Finds the station dot under a tap. Fingers are imprecise, so every dot within
     * [radiusPx] of the tap is a candidate, and the one nearest to the tap wins.
     * @return true if a station was hit (the tap is then consumed).
     */
    private fun handleTap(map: MapLibreMap, tapped: LatLng, radiusPx: Float): Boolean {
        val centre = map.projection.toScreenLocation(tapped)
        val area = RectF(centre.x - radiusPx, centre.y - radiusPx, centre.x + radiusPx, centre.y + radiusPx)
        val nearest = map.queryRenderedFeatures(area, StationLayers.DOTS_LAYER_ID)
            .mapNotNull { feature ->
                val point = feature.geometry() as? Point ?: return@mapNotNull null
                val id = feature.getNumberProperty(StationGeoJson.PROP_ID)?.toLong() ?: return@mapNotNull null
                id to LatLng(point.latitude(), point.longitude()).distanceTo(tapped)
            }
            .minByOrNull { it.second }
            ?: return false
        onStationTapped(nearest.first)
        return true
    }

    private fun reportCamera() {
        val map = map ?: return
        val bounds = map.projection.visibleRegion.latLngBounds
        val target = map.cameraPosition.target ?: return
        val kept = lastReportedArea
        val area = if (keepReportedArea && kept != null) {
            areaIsStale = true
            kept
        } else {
            areaIsStale = false
            BoundingBox(
                south = bounds.latitudeSouth,
                west = bounds.longitudeWest,
                north = bounds.latitudeNorth,
                east = bounds.longitudeEast,
            )
        }
        keepReportedArea = false
        lastReportedArea = area
        onCameraIdle(
            area,
            MapCamera(target.latitude, target.longitude, map.cameraPosition.zoom),
        )
    }

    companion object {
        /**
         * Background map: OpenFreeMap "Liberty", a free vector style needing no API key.
         * To change the map provider, change this one URL (and check the fonts and the
         * place-name source named in [StationLayers]).
         */
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

        private const val CAMERA_ANIMATION_MILLIS = 700

        /** Shorter than the other moves: fewer frames to draw while the station sheet opens. */
        private const val FOCUS_ANIMATION_MILLIS = 400

        /** Distance between the two screen points used to measure the map's scale. */
        private const val SCALE_SAMPLE_PX = 200f

        /** A tap within this distance of a dot (in dp, about a fingertip) selects it. */
        private const val TAP_RADIUS_DP = 20f
    }
}
