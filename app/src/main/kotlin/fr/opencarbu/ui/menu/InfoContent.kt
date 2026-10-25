package fr.opencarbu.ui.menu

import android.app.ActivityManager
import android.os.Process
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.opencarbu.R
import fr.opencarbu.data.FeedConfig
import fr.opencarbu.domain.model.FeedTiming
import fr.opencarbu.ui.UiState
import fr.opencarbu.ui.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/** The "Info" page: what data is loaded, where it comes from, and under which licences. */
@Composable
fun InfoContent(state: UiState) {
    SectionTitle(R.string.info_data_title)
    val fetchedAt = state.fetchedAtMillis
    if (state.feedStations.isEmpty() || fetchedAt == null) {
        Text(stringResource(R.string.info_data_none), style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            stringResource(R.string.info_stations_count, NumberFormat.getInstance().format(state.feedStations.size)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.info_data_date, formatDateTime(fetchedAt), relativeAge(fetchedAt)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.info_source_url, FeedConfig.hostOf(FeedConfig.url)), style = MaterialTheme.typography.bodyMedium)
    }
    val referenceOnlyCount = remember(state.stations) { state.stations.count { it.isReferenceOnly } }
    if (referenceOnlyCount > 0) {
        Text(
            stringResource(R.string.info_reference_only_count, NumberFormat.getInstance().format(referenceOnlyCount)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        if (state.stationNames.isEmpty()) stringResource(R.string.info_names_none)
        else stringResource(R.string.info_names_count, NumberFormat.getInstance().format(state.stationNames.size)),
        style = MaterialTheme.typography.bodyMedium,
    )
    state.namesFetchedAtMillis?.let {
        Text(
            stringResource(R.string.info_names_date, formatDateTime(it), relativeAge(it)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.info_source_url, FeedConfig.hostOf(FeedConfig.NAMES_URL)), style = MaterialTheme.typography.bodyMedium)
    }
    state.namesError?.let {
        Text(
            stringResource(R.string.names_download_failed, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    HorizontalDivider()
    SectionTitle(R.string.info_sources_title)
    Source(R.string.info_src_prices_title, R.string.info_src_prices_body, "https://data.economie.gouv.fr")
    Source(R.string.info_src_map_title, R.string.info_src_map_body, "https://openfreemap.org")
    Source(R.string.info_src_osm_title, R.string.info_src_osm_body, "https://www.openstreetmap.org/copyright")
    Source(R.string.info_src_names_title, R.string.info_src_names_body, "https://www.data.gouv.fr/datasets/6a0f63071a36d728807f9026/")
    Source(R.string.info_src_birds_title, R.string.info_src_birds_body, "https://www.wikidata.org")
    Source(R.string.info_src_geocoder_title, R.string.info_src_geocoder_body, "https://geoservices.ign.fr/geocodage")
    Source(R.string.info_src_routing_title, R.string.info_src_routing_body, "https://routing.openstreetmap.de/about.html")
    Source(R.string.info_src_icon_title, R.string.info_src_icon_body, "https://www.svgrepo.com/svg/243740/duck")
    Source(R.string.info_src_engine_title, R.string.info_src_engine_body, "https://maplibre.org")
    Source(R.string.info_src_app_title, R.string.info_src_app_body, "https://www.gnu.org/licenses/agpl-3.0.html")

    HorizontalDivider()
    SectionTitle(R.string.info_perf_title)
    FeedTimingLine(state.pricesTiming, R.string.info_perf_prices, R.string.info_perf_prices_none)
    FeedTimingLine(state.namesTiming, R.string.info_perf_names, R.string.info_perf_names_none)
    FeedTimingLine(state.routingTiming, R.string.info_perf_routing, null)
    MemoryLine()

    HorizontalDivider()
    Text(
        stringResource(R.string.info_version, appVersion()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Prices feed: answer in 1.8 s, download in 3.2 s" for the last successful download, or [noneRes] (nothing if null). */
@Composable
private fun FeedTimingLine(timing: FeedTiming?, @StringRes textRes: Int, @StringRes noneRes: Int?) {
    if (timing == null && noneRes == null) return // optional feature never used: nothing to show
    Text(
        if (timing == null) {
            stringResource(noneRes!!)
        } else {
            stringResource(textRes, durationText(timing.responseMillis), durationText(timing.totalMillis))
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** "350 ms" under a second, else "1.8 s". */
@Composable
private fun durationText(millis: Long): String =
    if (millis < 1000) {
        stringResource(R.string.info_perf_ms, millis)
    } else {
        stringResource(R.string.info_perf_s, NumberFormat.getInstance().apply { maximumFractionDigits = 1 }.format(millis / 1000.0))
    }

/**
 * The memory the app uses, in MB: the process's PSS (proportional set size), what Android's own "memory used by
 * app" screens count, including the map's graphics memory. Read every two seconds, only while this page is shown.
 */
@Composable
private fun MemoryLine() {
    val context = LocalContext.current
    var megabytes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val manager = context.getSystemService(ActivityManager::class.java)
        while (true) {
            megabytes = withContext(Dispatchers.Default) {
                manager?.getProcessMemoryInfo(intArrayOf(Process.myPid()))?.firstOrNull()?.totalPss?.div(1024)
            }
            delay(MEMORY_REFRESH_MILLIS)
        }
    }
    megabytes?.let {
        Text(stringResource(R.string.info_perf_memory, it), style = MaterialTheme.typography.bodyMedium)
    }
}

private const val MEMORY_REFRESH_MILLIS = 2_000L

/** The `versionName` declared in app/build.gradle.kts, read from the installed package. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        try {
            @Suppress("DEPRECATION") // the flags-as-Int overload is the one available on every supported API level
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }
}

/** "5 minutes ago". */
private fun relativeAge(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/** One source: name in bold, licence text below. Tapping it opens [url] in the browser. */
@Composable
private fun Source(@StringRes titleRes: Int, @StringRes bodyRes: Int, url: String) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.clickable { runCatching { uriHandler.openUri(url) } },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium)
        Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}
