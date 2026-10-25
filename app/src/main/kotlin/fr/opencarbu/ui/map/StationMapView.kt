package fr.opencarbu.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.ui.MapCamera
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

/**
 * The MapLibre map, wrapped for Compose.
 *
 * MapLibre is a classic Android `View`, so it is embedded with `AndroidView`. A MapView must
 * be told about every step of the screen's life (start, pause, destroy...): that is what the
 * lifecycle observer below does. Forgetting it leaks memory or leaves a blank map.
 *
 * @param initialCamera where the camera starts; later changes of this value are ignored
 *   (the user moves the map, not the app).
 * @param geoJson the dots to draw, see [StationGeoJson].
 * @param userLocation where the user is, or null if unknown (draws the blue marker).
 * @param placeNameOpacity opacity of the town and village names drawn over the dots.
 * @param stationSizeFactor size multiplier for the dots and prices (1.0 = standard).
 * @param placeNameSizeFactor size multiplier for the town and village names (1.0 = standard).
 * @param areaToShow an area the camera must bring into view, or null. Once it has been
 *   handed to the map, [onAreaShown] is called so the app can forget the request.
 * @param stationFocus a station to put in the middle of the map part above the station sheet, or null.
 * @param lifecycle the lifecycle of the hosting activity.
 * @param onCameraIdle called when the user stops moving the map.
 * @param onStationTapped called with the id of the station whose dot was tapped.
 */
@Composable
fun StationMapView(
    initialCamera: MapCamera,
    geoJson: String,
    userLocation: GeoPoint?,
    placeNameOpacity: Float,
    stationSizeFactor: Float,
    placeNameSizeFactor: Float,
    areaToShow: BoundingBox?,
    onAreaShown: () -> Unit,
    stationFocus: StationFocus?,
    lifecycle: Lifecycle,
    onCameraIdle: (BoundingBox, MapCamera) -> Unit,
    onStationTapped: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // MapLibre must be initialised before the first MapView is created (safe to call twice).
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    // The callbacks are captured once: the ViewModel methods they point to never change.
    val controller = remember { StationMapController(onCameraIdle, onStationTapped) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer) // replays the events already happened (create, start...)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mapView) { controller.attach(mapView, initialCamera) }

    // The map is drawn behind the navigation bar (at the bottom, or on one side in landscape): keep MapLibre's
    // attribution button (required by the data licences) clear of it.
    val density = LocalDensity.current
    val barRight = WindowInsets.navigationBars.getRight(density, LocalLayoutDirection.current)
    val barBottom = WindowInsets.navigationBars.getBottom(density)
    LaunchedEffect(barRight, barBottom) { controller.setBarInsets(barRight, barBottom) }

    // Runs again each time a new area is requested (the request is cleared right after).
    LaunchedEffect(areaToShow) {
        if (areaToShow != null) {
            controller.showArea(areaToShow)
            onAreaShown()
        }
    }

    // Runs again whenever the station, or the place where the sheet's top edge settles, changes.
    LaunchedEffect(stationFocus) {
        if (stationFocus != null) {
            controller.focusStation(stationFocus)
        } else {
            // No station in focus (sheet closed, or first composition): once the sheet has slid away,
            // bring the dots up to date for the whole area if the focus move had kept them back.
            delay(AREA_REFRESH_DELAY_MILLIS)
            controller.refreshArea()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        // Runs after each recomposition; the controller ignores it if the dots did not change.
        update = {
            controller.setGeoJson(geoJson)
            controller.setUserLocation(userLocation)
            controller.setPlaceNameOpacity(placeNameOpacity)
            controller.setStationSizeFactor(stationSizeFactor)
            controller.setPlaceNameSizeFactor(placeNameSizeFactor)
        },
    )
}

/** How long after the sheet is gone the dots are brought up to date for the area really shown. */
private const val AREA_REFRESH_DELAY_MILLIS = 450L
