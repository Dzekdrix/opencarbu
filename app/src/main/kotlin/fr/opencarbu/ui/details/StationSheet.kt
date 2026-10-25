package fr.opencarbu.ui.details

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import fr.opencarbu.R
import fr.opencarbu.domain.birds.Bird
import fr.opencarbu.domain.birds.BirdFlight
import fr.opencarbu.domain.birds.BirdListParser
import fr.opencarbu.domain.birds.BirdPhraseBuilder
import fr.opencarbu.domain.details.FuelRow
import fr.opencarbu.domain.details.FuelRowStatus
import fr.opencarbu.domain.details.StationDetailsBuilder
import fr.opencarbu.domain.history.PricePoint
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.NearestSettings
import fr.opencarbu.domain.nearest.JourneyCost
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.model.RoutingSettings
import fr.opencarbu.domain.model.StationIdentity
import fr.opencarbu.domain.routing.RouteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import fr.opencarbu.domain.pricing.PricingSettings
import fr.opencarbu.ui.labelRes
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * The sheet that slides up when a station is tapped, top to bottom:
 *  - the station's brand and name when known (from a separate reference file), then its address;
 *  - a button that opens the station in a map application, to start a route;
 *  - the distance from the user (straight line for now);
 *  - every fuel with its price and the date it was last updated.
 */
@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet is still marked experimental in this Material3 version
@Composable
fun StationSheet(
    station: Station,
    identity: StationIdentity?,
    /** Where the price feed puts the station (the dot may use the OpenStreetMap position instead). */
    feedPosition: GeoPoint?,
    birds: List<Bird>,
    userLocation: GeoPoint?,
    pricing: PricingSettings,
    routing: RoutingSettings,
    onRoadRoute: suspend (GeoPoint, GeoPoint) -> RouteResult,
    /** The fuel whose price curve to show, or null when the price history is off. */
    historyFuel: FuelType?,
    /** The fuel shown on the map, and the vehicle settings: for the journey cost line. */
    fuel: FuelType,
    nearest: NearestSettings,
    onPriceHistory: suspend (Long, FuelType, Long) -> List<PricePoint>,
    /** Called with the y (window coordinates) of the sheet's top edge, each time it changes (e.g. while sliding in). */
    onTopEdgeChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Rebuilt only when the station, position or thresholds change.
    val details = remember(station, userLocation, pricing) {
        StationDetailsBuilder.build(station, userLocation, System.currentTimeMillis(), pricing)
    }
    val nameLines = identity?.lines.orEmpty()
    // The reference file's address wins over the price feed's, which is wrong for some stations.
    val address = identity?.street ?: station.address.ifBlank { station.city }
    val postalAndCity = if (identity?.street != null && (identity.postalCode != null || identity.city != null)) {
        "${identity.postalCode ?: station.postalCode} ${identity.city ?: station.city}".trim()
    } else {
        "${station.postalCode} ${station.city}".trim()
    }
    // Label of the pin in the map app: the station's name if known, else its address.
    val title = nameLines.firstOrNull() ?: address
    val noMapApp = stringResource(R.string.sheet_no_map_app)

    // A different bird each time the sheet opens. The random number is saved, so the bird stays the
    // same when the screen rotates; the sheet leaves the composition when closed, so the next one differs.
    val birdSeed = rememberSaveable { Random.nextInt() }
    val bird = if (birds.isEmpty()) BirdListParser.FALLBACK else birds[Math.floorMod(birdSeed, birds.size)]

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // The content starts below the drag handle; the sheet itself begins that much higher.
        val dragHandleHeightPx = with(LocalDensity.current) { DRAG_HANDLE_HEIGHT.toPx() }
        Column(
            modifier = Modifier
                .onGloballyPositioned { onTopEdgeChanged((it.positionInWindow().y - dragHandleHeightPx).roundToInt()) }
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: the station's title and address.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Brand, then name (when known), above the address.
                nameLines.forEachIndexed { index, line ->
                    Text(
                        line,
                        style = if (index == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (nameLines.isEmpty()) {
                    Text(address, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                } else {
                    Text(address, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    postalAndCity,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (station.isOpen24h) {
                    Text(
                        stringResource(R.string.sheet_open_24h),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Hands the station over to the user's map app, from which the route can be started.
            Button(
                onClick = {
                    if (!MapAppLauncher.open(context, station, title)) {
                        Toast.makeText(context, noMapApp, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sheet_open_in_maps))
            }

            details.straightLineKm?.let { km ->
                DistanceByBird(
                    distanceText = formatDistance(km),
                    durationText = formatDuration(BirdFlight.minutesFor(km, bird)),
                    bird = bird,
                )
            }
            // The road distance, once the routing server has answered (null if it failed), for the journey cost below.
            var roadKm by remember(station.id, userLocation, routing.enabled) { mutableStateOf<Double?>(null) }
            var roadResolved by remember(station.id, userLocation, routing.enabled) { mutableStateOf(false) }
            if (routing.enabled && userLocation != null) {
                RoadDistance(
                    from = userLocation,
                    to = GeoPoint(station.latitude, station.longitude),
                    onRoadRoute = onRoadRoute,
                    onResult = { km -> roadKm = km; roadResolved = true },
                )
            }
            // "1,20€ de trajet + 60,00€ de plein = 61,20€ de coût total": for the fuel shown on the map, with the
            // vehicle settings of the nearest-stations list. Waits for the road distance when it is being asked.
            val journeyKm = if (routing.enabled && userLocation != null && !roadResolved) null else roadKm ?: details.straightLineKm
            val journeyPrice = station.prices[fuel]?.euroPerLitre
            if (journeyKm != null && journeyPrice != null) {
                val cost = JourneyCost.of(journeyKm, journeyPrice, nearest.consumptionLitresPer100Km.toDouble(), nearest.tankLitres)
                val euros = remember { NumberFormat.getInstance().apply { minimumFractionDigits = 2; maximumFractionDigits = 2 } }
                Text(
                    stringResource(
                        R.string.sheet_journey_cost,
                        euros.format(cost.tripCostEuro),
                        euros.format(cost.fillUpCostEuro),
                        euros.format(cost.totalCostEuro),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val hintStyle = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic)
                val consumptionText = remember(nearest.consumptionLitresPer100Km) {
                    NumberFormat.getInstance().apply { maximumFractionDigits = 1 }.format(nearest.consumptionLitresPer100Km.toDouble())
                }
                Text(
                    stringResource(R.string.sheet_journey_cost_hint, consumptionText, nearest.tankLitres),
                    style = hintStyle,
                )
                if (!routing.enabled) {
                    // Without the road distance the trip is measured in a straight line: say how to refine it.
                    Text(stringResource(R.string.sheet_journey_cost_routing_hint), style = hintStyle)
                }
            }

            HorizontalDivider()
            if (station.isReferenceOnly) {
                // Test aid: known from the reference file only, so there is nothing to list.
                Text(
                    stringResource(R.string.sheet_reference_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    stringResource(R.string.sheet_fuels_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                for (row in details.fuels) FuelLine(row)
                if (historyFuel != null) {
                    HorizontalDivider()
                    PriceHistorySection(station.id, historyFuel, onPriceHistory)
                }
            }

            HorizontalDivider()
            PositionSource(station, identity, feedPosition)
        }
    }
}

private sealed interface RoadState {
    data object Loading : RoadState
    data class Done(val route: RouteResult) : RoadState
    data class Failed(val message: String) : RoadState
}

/**
 * "12,4 km by road · 15 min", asked from the routing server when the sheet opens (the feature is
 * optional and off by default). Shows its progress and, if it fails, why; nothing else on the
 * sheet depends on it.
 */
@Composable
private fun RoadDistance(
    from: GeoPoint,
    to: GeoPoint,
    onRoadRoute: suspend (GeoPoint, GeoPoint) -> RouteResult,
    /** Called once the server has answered: the distance in km, or null if it failed. */
    onResult: (Double?) -> Unit,
) {
    var state by remember(from, to) { mutableStateOf<RoadState>(RoadState.Loading) }
    LaunchedEffect(from, to) {
        // Short wait: flicking through several stations must not send a request for each.
        delay(ROAD_REQUEST_DEBOUNCE_MILLIS)
        state = try {
            RoadState.Done(onRoadRoute(from, to)).also { onResult(it.route.distanceKm) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onResult(null)
            RoadState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
    when (val current = state) {
        RoadState.Loading -> Text(
            stringResource(R.string.sheet_road_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is RoadState.Failed -> Text(
            stringResource(R.string.sheet_road_failed, current.message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is RoadState.Done -> Text(
            stringResource(
                R.string.sheet_road_distance,
                formatDistance(current.route.distanceKm),
                formatDuration(current.route.durationMinutes),
            ),
            // Same style as the bird line above it.
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val ROAD_REQUEST_DEBOUNCE_MILLIS = 400L

/**
 * Two small grey lines for debugging misplaced stations: the id shared by the price feed and the
 * reference file, then the coordinates from the feed and, when known, from OpenStreetMap
 * (with the OSM object they come from). The dot is drawn at the OSM position when there is one.
 */
@Composable
private fun PositionSource(station: Station, identity: StationIdentity?, feedPosition: GeoPoint?) {
    fun coordinates(latitude: Double, longitude: Double) = String.format(Locale.ROOT, "%.5f,%.5f", latitude, longitude)
    val feed = feedPosition ?: GeoPoint(station.latitude, station.longitude)
    val osm = identity?.position
    val coordinatesLine = buildString {
        // A station absent from the feed has no feed coordinates.
        if (!station.isReferenceOnly) append(coordinates(feed.latitude, feed.longitude)).append(' ').append(stringResource(R.string.sheet_coords_feed))
        if (osm != null) {
            if (isNotEmpty()) append(' ').append(coordinates(osm.latitude, osm.longitude)).append(' ')
            append(stringResource(R.string.sheet_coords_osm, identity.osmObject ?: "?"))
        }
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Text(stringResource(R.string.sheet_station_id, station.id.toString()), style = MaterialTheme.typography.bodySmall, color = color)
    Text(stringResource(R.string.sheet_coordinates, coordinatesLine), style = MaterialTheme.typography.bodySmall, color = color)
}

/** One fuel: name and price on the first line, the update date (or the shortage) below. */
@Composable
private fun FuelLine(row: FuelRow) {
    val priceFormat = remember {
        NumberFormat.getInstance().apply {
            minimumFractionDigits = 3
            maximumFractionDigits = 3
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(row.fuel.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                row.euroPerLitre?.let { stringResource(R.string.sheet_price, priceFormat.format(it)) } ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        val (text, isWarning) = fuelCaption(row)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The small line under a fuel, and whether it is a warning (old price, shortage). */
@Composable
private fun fuelCaption(row: FuelRow): Pair<String, Boolean> {
    if (row.status == FuelRowStatus.SHORTAGE) {
        val since = row.shortageSinceEpochMillis
        val text = if (since != null) {
            stringResource(R.string.sheet_shortage_since, formatDate(since))
        } else {
            stringResource(R.string.sheet_shortage)
        }
        return text to true
    }
    val updatedAt = row.updatedAtEpochMillis
        ?: return stringResource(R.string.sheet_date_unknown) to true
    val relative = DateUtils.getRelativeTimeSpanString(updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    val text = stringResource(R.string.sheet_updated, relative.toString(), formatDate(updatedAt))
    return text to (row.status == FuelRowStatus.OLD_PRICE)
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

/** "12,4" for 12.4 km; whole kilometres from 100 km on. */
private fun formatDistance(km: Double): String =
    NumberFormat.getInstance().apply {
        maximumFractionDigits = if (km < 100) 1 else 0
    }.format(km)

/**
 * "3,4 km on the back of a <bird> · 5 min" (in French: "à dos de <oiseau>"): the distance is a straight
 * line, so it is told as a ride on a bird, and the duration is how long that bird would fly it (see
 * [BirdFlight]). The bird's name is a link to its Wikipedia page when there is one.
 */
@Composable
private fun DistanceByBird(distanceText: String, durationText: String, bird: Bird) {
    val language = LocalConfiguration.current.locales[0].language
    val phrase = remember(bird, language) { BirdPhraseBuilder.build(bird, language) }
    // The translated sentence has a marker where the bird goes, so each language can word it freely.
    val sentence = stringResource(R.string.sheet_distance_straight, distanceText, BIRD_MARKER)
    val before = sentence.substringBefore(BIRD_MARKER)
    val after = sentence.substringAfter(BIRD_MARKER, missingDelimiterValue = "")
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(sentence, phrase, linkColor, durationText) {
        buildAnnotatedString {
            append(before)
            append(phrase.article)
            val url = phrase.url
            if (url == null) {
                append(phrase.name)
            } else {
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                    append(phrase.name)
                }
            }
            append(after)
            append(" · ")
            append(durationText)
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/** "15 min" under an hour, "2 h 05" from an hour on. */
@Composable
private fun formatDuration(minutes: Int): String =
    if (minutes < 60) {
        stringResource(R.string.settings_unit_minutes, minutes)
    } else {
        stringResource(R.string.settings_unit_hours_minutes, minutes / 60, minutes % 60)
    }

/** Placed in the translated sentence where the bird goes; never shown. */
private const val BIRD_MARKER = "\u0001"

/** Height of Material 3's drag handle area (4 dp bar with 22 dp above and below). */
private val DRAG_HANDLE_HEIGHT = 48.dp
