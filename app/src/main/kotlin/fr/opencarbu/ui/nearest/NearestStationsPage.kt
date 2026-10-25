package fr.opencarbu.ui.nearest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.opencarbu.R
import fr.opencarbu.ui.insetsAllSides
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.NearestSettings
import fr.opencarbu.domain.model.RoutingSettings
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.model.StationIdentity
import fr.opencarbu.domain.nearest.NearestEntry
import fr.opencarbu.domain.nearest.NearestStations
import fr.opencarbu.domain.routing.RouteResult
import fr.opencarbu.ui.formatKm
import fr.opencarbu.ui.labelRes
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat

/** Where the distances of the list come from. */
private sealed interface DistanceSource {
    data object Straight : DistanceSource
    data object Loading : DistanceSource
    data object Road : DistanceSource
    /** Road distances arrive batch after batch: [done] stations of [total] so far. */
    data class Progress(val done: Int, val total: Int) : DistanceSource
    /** A later batch failed: the stations already answered keep their road distance. */
    data class Interrupted(val message: String) : DistanceSource
    data class Failed(val message: String) : DistanceSource
}

/**
 * Full-screen list of the stations around the user, nearest first: name, city, distance, price of
 * the selected fuel and, last, what the trip there costs in fuel. The distances are by road when
 * the road-distance option is on (the list first appears with straight-line distances, then is
 * updated when the server has answered), else in a straight line. Tapping a row calls [onChosen].
 */
@Composable
fun NearestStationsPage(
    stations: List<Station>,
    names: Map<Long, StationIdentity>,
    fuel: FuelType,
    origin: GeoPoint,
    settings: NearestSettings,
    routing: RoutingSettings,
    onRoadDistances: suspend (GeoPoint, List<Pair<Long, GeoPoint>>) -> List<RouteResult?>,
    cachedRoadDistances: (GeoPoint, List<Long>) -> List<RouteResult?>?,
    onChosen: (Station) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val consumption = settings.consumptionLitresPer100Km.toDouble()
    val tankLitres = settings.tankLitres
    val candidates = remember(stations, fuel, origin, settings.radiusKm) {
        NearestStations.candidates(stations, fuel, origin, settings.radiusKm.toDouble())
    }
    // Road distances already known for about this position (within 100 m): shown at once, no request.
    val cached = remember(candidates, routing.enabled, routing.serverUrl) {
        if (routing.enabled && candidates.isNotEmpty()) {
            cachedRoadDistances(origin, candidates.map { it.station.id })
        } else {
            null
        }
    }
    var entries by remember(candidates, consumption, tankLitres, cached) {
        mutableStateOf(NearestStations.rank(candidates, cached, consumption, tankLitres))
    }
    var source by remember(candidates, cached) {
        mutableStateOf<DistanceSource>(
            when {
                cached != null -> DistanceSource.Road
                routing.enabled && candidates.isNotEmpty() -> DistanceSource.Loading
                else -> DistanceSource.Straight
            },
        )
    }

    LaunchedEffect(candidates, consumption, tankLitres, routing.enabled, routing.serverUrl) {
        if (cached != null) return@LaunchedEffect // nothing to ask
        entries = NearestStations.rank(candidates, null, consumption, tankLitres)
        if (!routing.enabled || candidates.isEmpty()) {
            source = DistanceSource.Straight
            return@LaunchedEffect
        }
        source = DistanceSource.Loading
        // Batch after batch, nearest first: the rows update as each answer comes in (the ViewModel spaces the
        // requests, about one a second), so the user can read the first ones while the others arrive.
        val roads = ArrayList<RouteResult?>()
        try {
            for (batch in NearestStations.roadBatches(candidates)) {
                roads.addAll(onRoadDistances(origin, batch.map { it.station.id to it.position }))
                entries = NearestStations.rank(candidates, roads.toList(), consumption, tankLitres)
                source = if (roads.size < candidates.size) DistanceSource.Progress(roads.size, candidates.size) else DistanceSource.Road
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            source = if (roads.isEmpty()) DistanceSource.Failed(reason) else DistanceSource.Interrupted(reason)
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.insetsAllSides()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.nearest_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.nearest_subtitle, stringResource(fuel.labelRes), settings.radiusKm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.nearest_tank_volume, tankLitres),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val consumptionText = remember(consumption) {
                        NumberFormat.getInstance().apply { maximumFractionDigits = 1 }.format(consumption)
                    }
                    Text(
                        stringResource(R.string.nearest_consumption, consumptionText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.search_close)) }
            }
            HorizontalDivider()
            val notice = when (val current = source) {
                DistanceSource.Loading -> stringResource(R.string.nearest_loading_roads)
                is DistanceSource.Failed -> stringResource(R.string.nearest_roads_failed, current.message)
                DistanceSource.Straight ->
                    if (candidates.isEmpty()) null else stringResource(R.string.nearest_straight_notice)
                is DistanceSource.Progress -> stringResource(R.string.nearest_roads_progress, current.done, current.total)
                is DistanceSource.Interrupted -> stringResource(R.string.nearest_roads_interrupted, current.message)
                DistanceSource.Road -> null
            }
            notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.nearest_empty, stringResource(fuel.labelRes), settings.radiusKm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                val priceFormat = remember {
                    NumberFormat.getInstance().apply { minimumFractionDigits = 3; maximumFractionDigits = 3 }
                }
                val costFormat = remember {
                    NumberFormat.getInstance().apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(entries, key = { it.station.id }) { entry ->
                        NearestRow(
                            entry = entry,
                            identity = names[entry.station.id],
                            priceText = stringResource(R.string.sheet_price, priceFormat.format(entry.euroPerLitre)),
                            fillCostText = stringResource(R.string.nearest_fill_cost, costFormat.format(entry.fillUpCostEuro)),
                            tripCostText = stringResource(R.string.nearest_trip_cost, costFormat.format(entry.tripCostEuro)),
                            totalCostText = stringResource(R.string.nearest_total_cost, costFormat.format(entry.totalCostEuro)),
                            onClick = { onChosen(entry.station) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** One station: name, city and distance on the left; price, fill-up, trip and total cost on the right. */
@Composable
private fun NearestRow(
    entry: NearestEntry,
    identity: StationIdentity?,
    priceText: String,
    fillCostText: String,
    tripCostText: String,
    totalCostText: String,
    onClick: () -> Unit,
) {
    val station = entry.station
    val name = identity?.lines?.joinToString(" ")?.takeIf { it.isNotBlank() } ?: station.address
    val city = identity?.city?.takeIf { it.isNotBlank() } ?: station.city
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                city,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                stringResource(
                    if (entry.isRoad) R.string.nearest_distance_road else R.string.nearest_distance_straight,
                    formatKm(entry.distanceKm),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Capped so the cost lines never squeeze the name.
        Column(Modifier.widthIn(max = 170.dp), horizontalAlignment = Alignment.End) {
            Text(priceText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            for (line in listOf(fillCostText, tripCostText)) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
            Text(
                totalCostText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}
