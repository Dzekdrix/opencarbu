package fr.opencarbu.ui.menu

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.opencarbu.R
import fr.opencarbu.domain.model.DataSettings
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.HistorySettings
import fr.opencarbu.domain.model.MapDisplaySettings
import fr.opencarbu.domain.model.NearestSettings
import fr.opencarbu.domain.model.RoutingSettings
import fr.opencarbu.ui.UiState
import fr.opencarbu.ui.formatDateTime
import fr.opencarbu.ui.labelRes
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The "Settings" page. Each slider shows its value while being dragged and applies it when the
 * finger is lifted, so the map is not recomputed for every pixel of the drag.
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow is still marked experimental in this Compose version
@Composable
fun SettingsContent(state: UiState, actions: MenuActions) {
    val context = LocalContext.current

    // ---- Default fuel ----
    SectionTitle(R.string.settings_fuel_title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = state.defaultFuel == null,
            onClick = { actions.onDefaultFuelChanged(null) },
            label = { Text(stringResource(R.string.settings_fuel_last)) },
        )
        for (fuel in FuelType.entries) {
            FilterChip(
                selected = state.defaultFuel == fuel,
                onClick = { actions.onDefaultFuelChanged(fuel) },
                label = { Text(stringResource(fuel.labelRes)) },
            )
        }
    }

    // ---- Prices ----
    HorizontalDivider()
    SectionTitle(R.string.settings_prices_title)
    SliderSetting(
        title = stringResource(R.string.settings_price_age),
        help = stringResource(R.string.settings_price_age_help),
        value = state.pricing.maxPriceAgeDays.toFloat(),
        range = PRICE_AGE_DAYS_RANGE,
        valueText = { context.getString(R.string.settings_unit_days, it.roundToInt()) },
        onCommit = { actions.onPricingChanged(state.pricing.copy(maxPriceAgeDays = it.roundToInt())) },
    )
    SliderSetting(
        title = stringResource(R.string.settings_shortage_age),
        help = stringResource(R.string.settings_shortage_age_help),
        value = state.pricing.maxShortageAgeDays.toFloat(),
        range = SHORTAGE_AGE_DAYS_RANGE,
        valueText = { context.getString(R.string.settings_unit_days, it.roundToInt()) },
        onCommit = { actions.onPricingChanged(state.pricing.copy(maxShortageAgeDays = it.roundToInt())) },
    )

    // ---- Nearest stations ----
    HorizontalDivider()
    SectionTitle(R.string.settings_nearest_title)
    SliderSetting(
        title = stringResource(R.string.settings_nearest_radius),
        help = stringResource(R.string.settings_nearest_radius_help),
        value = state.nearest.radiusKm.toFloat(),
        range = NearestSettings.MIN_RADIUS_KM.toFloat()..NearestSettings.MAX_RADIUS_KM.toFloat(),
        steps = (NearestSettings.MAX_RADIUS_KM - NearestSettings.MIN_RADIUS_KM) / 5 - 1, // one tick per 5 km
        valueText = { context.getString(R.string.settings_unit_km, roundedNearestRadius(it)) },
        onCommit = { actions.onNearestChanged(state.nearest.copy(radiusKm = roundedNearestRadius(it))) },
    )
    SliderSetting(
        title = stringResource(R.string.settings_nearest_consumption),
        help = stringResource(R.string.settings_nearest_consumption_help),
        value = state.nearest.consumptionLitresPer100Km,
        range = NearestSettings.MIN_CONSUMPTION..NearestSettings.MAX_CONSUMPTION,
        // No tick marks: with a step of 0.1 L there would be 180 of them. The value is snapped to 0.1 instead.
        valueText = { context.getString(R.string.settings_unit_consumption, roundedConsumption(it)) },
        onCommit = { actions.onNearestChanged(state.nearest.copy(consumptionLitresPer100Km = roundedConsumption(it))) },
    )
    SliderSetting(
        title = stringResource(R.string.settings_nearest_tank),
        help = stringResource(R.string.settings_nearest_tank_help),
        value = state.nearest.tankLitres.toFloat(),
        range = NearestSettings.MIN_TANK_LITRES.toFloat()..NearestSettings.MAX_TANK_LITRES.toFloat(),
        // No tick marks (115 of them would be unreadable): the value is snapped to whole litres.
        valueText = { context.getString(R.string.settings_unit_litres, roundedTank(it)) },
        onCommit = { actions.onNearestChanged(state.nearest.copy(tankLitres = roundedTank(it))) },
    )

    // ---- Map ----
    HorizontalDivider()
    SectionTitle(R.string.settings_map_title)
    SliderSetting(
        title = stringResource(R.string.settings_size),
        help = stringResource(R.string.settings_size_help),
        value = state.display.stationSizeFactor,
        range = MapDisplaySettings.MIN_STATION_SIZE_FACTOR..MapDisplaySettings.MAX_STATION_SIZE_FACTOR,
        valueText = { "×" + NumberFormat.getInstance().apply { maximumFractionDigits = 2 }.format(roundedSizeFactor(it)) },
        onCommit = { actions.onDisplayChanged(state.display.copy(stationSizeFactor = roundedSizeFactor(it))) },
    )
    SliderSetting(
        title = stringResource(R.string.settings_place_size),
        help = stringResource(R.string.settings_size_help),
        value = state.display.placeNameSizeFactor,
        range = MapDisplaySettings.MIN_PLACE_NAME_SIZE_FACTOR..MapDisplaySettings.MAX_PLACE_NAME_SIZE_FACTOR,
        valueText = { "×" + NumberFormat.getInstance().apply { maximumFractionDigits = 2 }.format(roundedSizeFactor(it)) },
        onCommit = { actions.onDisplayChanged(state.display.copy(placeNameSizeFactor = roundedSizeFactor(it))) },
    )
    SliderSetting(
        title = stringResource(R.string.settings_place_opacity),
        help = null,
        value = state.display.placeNameOpacity,
        range = 0f..1f,
        valueText = { context.getString(R.string.settings_unit_percent, (it * 100).roundToInt()) },
        onCommit = { actions.onDisplayChanged(state.display.copy(placeNameOpacity = (it * 100).roundToInt() / 100f)) },
    )
    SliderSetting(
        title = stringResource(R.string.settings_radius),
        help = stringResource(R.string.settings_radius_help),
        value = state.display.startRadiusKm.toFloat(),
        range = MapDisplaySettings.MIN_START_RADIUS_KM.toFloat()..MapDisplaySettings.MAX_START_RADIUS_KM.toFloat(),
        valueText = { context.getString(R.string.settings_unit_km, roundedRadius(it)) },
        onCommit = { actions.onDisplayChanged(state.display.copy(startRadiusKm = roundedRadius(it))) },
    )

    // (still the Map section: how the map behaves when a station is tapped)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_center_station), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_center_station_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.display.centerOnStation,
            onCheckedChange = { actions.onDisplayChanged(state.display.copy(centerOnStation = it)) },
        )
    }

    // ---- Data ----
    HorizontalDivider()
    SectionTitle(R.string.settings_data_title)
    // The interval is picked from a list of round values (1 min ... 48 h): the slider moves
    // between the positions of that list, not between raw minutes.
    val choices = DataSettings.CHOICES_MINUTES
    SliderSetting(
        title = stringResource(R.string.settings_refresh_interval),
        help = stringResource(R.string.settings_refresh_help),
        value = nearestChoiceIndex(choices, state.data.refreshIntervalMinutes).toFloat(),
        range = 0f..(choices.size - 1).toFloat(),
        steps = choices.size - 2, // one tick per choice
        valueText = { formatMinutes(context, choices[it.roundToInt().coerceIn(0, choices.size - 1)]) },
        onCommit = {
            val minutes = choices[it.roundToInt().coerceIn(0, choices.size - 1)]
            actions.onDataChanged(state.data.copy(refreshIntervalMinutes = minutes))
        },
    )
    Button(onClick = actions.onRefresh, enabled = !state.isRefreshing, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.isRefreshing) R.string.status_refreshing else R.string.settings_refresh))
    }
    LastUpdate(state.fetchedAtMillis)
    state.errorMessage?.let {
        Text(
            stringResource(R.string.status_error, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    // Station names and brands: same idea, in days.
    val nameChoices = DataSettings.CHOICES_NAMES_DAYS
    SliderSetting(
        title = stringResource(R.string.settings_names_interval),
        help = stringResource(R.string.settings_names_interval_help),
        value = nearestChoiceIndex(nameChoices, state.data.namesRefreshIntervalDays).toFloat(),
        range = 0f..(nameChoices.size - 1).toFloat(),
        steps = nameChoices.size - 2,
        valueText = {
            context.getString(R.string.settings_unit_days, nameChoices[it.roundToInt().coerceIn(0, nameChoices.size - 1)])
        },
        onCommit = {
            val days = nameChoices[it.roundToInt().coerceIn(0, nameChoices.size - 1)]
            actions.onDataChanged(state.data.copy(namesRefreshIntervalDays = days))
        },
    )
    Button(onClick = actions.onRefreshNames, enabled = !state.isRefreshingNames, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.isRefreshingNames) R.string.status_refreshing else R.string.settings_names_refresh))
    }
    LastUpdate(state.namesFetchedAtMillis)
    state.namesError?.let {
        Text(
            stringResource(R.string.names_download_failed, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    // Test option: compare the reference file's stations with the price feed's.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_reference_only), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_reference_only_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.data.showReferenceOnlyStations,
            onCheckedChange = { actions.onDataChanged(state.data.copy(showReferenceOnlyStations = it)) },
        )
    }

    // ---- Price history (off by default: nothing is stored until it is switched on) ----
    HorizontalDivider()
    SectionTitle(R.string.settings_history_title)
    SwitchSetting(
        title = stringResource(R.string.settings_history_enable),
        help = stringResource(R.string.settings_history_help),
        checked = state.history.historyEnabled,
        onChange = { actions.onHistoryChanged(state.history.copy(historyEnabled = it)) },
    )
    if (state.history.historyEnabled) {
        Text(stringResource(R.string.settings_history_retention), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (months in HistorySettings.CHOICES_RETENTION_MONTHS) {
                FilterChip(
                    selected = state.history.retentionMonths == months,
                    onClick = { actions.onHistoryChanged(state.history.copy(retentionMonths = months)) },
                    label = { Text(stringResource(retentionLabel(months))) },
                )
            }
        }
    }
    Text(
        stringResource(R.string.settings_history_size, Formatter.formatShortFileSize(context, state.historyBytes)),
        style = MaterialTheme.typography.bodyMedium,
    )
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) actions.onHistoryExport(uri)
    }
    // Pickers often label a .csv file by another type, so every file is offered.
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.onHistoryImport(uri)
    }
    Text(
        stringResource(R.string.settings_history_transfer_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = { exportFile.launch("opencarbu-price-history-${java.time.LocalDate.now()}.csv") },
        enabled = state.historyBytes > 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_history_export))
    }
    OutlinedButton(onClick = { importFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_history_import))
    }
    ClearHistoryButton(enabled = state.historyBytes > 0, onConfirmed = actions.onHistoryClear)

    // ---- Daily refresh in the background (off by default) ----
    HorizontalDivider()
    SectionTitle(R.string.settings_background_title)
    SwitchSetting(
        title = stringResource(R.string.settings_background_enable),
        help = stringResource(R.string.settings_background_help),
        checked = state.history.backgroundRefresh,
        onChange = { actions.onHistoryChanged(state.history.copy(backgroundRefresh = it)) },
    )
    state.backgroundLastRefreshMillis?.let { millis ->
        // "Prices last refreshed in the background on 17/10/2026 at 8h01": red when the daily job is overdue
        // (more than a day) while the option is on, a sign that Android is not running it.
        val moment = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        val timePattern = stringResource(R.string.settings_background_time_pattern)
        val overdue = state.history.backgroundRefresh && System.currentTimeMillis() - millis > ONE_DAY_MILLIS
        Text(
            stringResource(
                R.string.settings_background_last_refresh,
                java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).format(moment),
                java.time.format.DateTimeFormatter.ofPattern(timePattern).format(moment),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
    if (state.history.backgroundRefresh) {
        SwitchSetting(
            title = stringResource(R.string.settings_background_wifi),
            help = stringResource(R.string.settings_background_wifi_help),
            checked = state.history.backgroundWifiOnly,
            onChange = { actions.onHistoryChanged(state.history.copy(backgroundWifiOnly = it)) },
        )
    }

    // ---- Road distance (optional, off by default: it sends positions to a server) ----
    HorizontalDivider()
    SectionTitle(R.string.settings_routing_title)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_routing_enable), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_routing_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.routing.enabled,
            onCheckedChange = { actions.onRoutingChanged(state.routing.copy(enabled = it)) },
        )
    }
    RoutingServerField(state.routing.serverUrl) { actions.onRoutingChanged(state.routing.copy(serverUrl = it)) }

    HorizontalDivider()
    ResetAllButton(onConfirmed = actions.onResetAll)
}

/**
 * Text field for the routing server address. What is typed is kept as is while editing; the
 * setting is only saved when it is a valid http(s) address, otherwise an error is shown.
 */
@Composable
private fun RoutingServerField(savedUrl: String, onValid: (String) -> Unit) {
    var text by remember { mutableStateOf(savedUrl) }
    // The saved value changed from outside (e.g. "restore defaults"): show it. While typing, the
    // saved value is the normalized text, so this never fights the user.
    LaunchedEffect(savedUrl) { if (RoutingSettings.normalize(text) != savedUrl) text = savedUrl }
    val valid = RoutingSettings.isValidUrl(text)
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (RoutingSettings.isValidUrl(it)) onValid(RoutingSettings.normalize(it))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_routing_url)) },
        singleLine = true,
        isError = !valid,
        supportingText = {
            Text(stringResource(if (valid) R.string.settings_routing_url_help else R.string.settings_routing_url_invalid))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    TextButton(
        onClick = { text = RoutingSettings.DEFAULT_SERVER_URL; onValid(RoutingSettings.DEFAULT_SERVER_URL) },
        enabled = text != RoutingSettings.DEFAULT_SERVER_URL,
    ) {
        Text(stringResource(R.string.settings_routing_url_default))
    }
}

/** A titled on/off switch with an explanation under the title. */
@Composable
private fun SwitchSetting(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@StringRes
private fun retentionLabel(months: Int): Int = when (months) {
    3 -> R.string.settings_retention_3m
    6 -> R.string.settings_retention_6m
    12 -> R.string.settings_retention_1y
    24 -> R.string.settings_retention_2y
    else -> R.string.settings_retention_forever
}

/** Button that, after a confirmation, deletes every recorded price. */
@Composable
private fun ClearHistoryButton(enabled: Boolean, onConfirmed: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { asking = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_history_clear))
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.settings_history_clear_title)) },
            text = { Text(stringResource(R.string.settings_history_clear_body)) },
            confirmButton = {
                TextButton(onClick = { asking = false; onConfirmed() }) {
                    Text(stringResource(R.string.settings_history_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text(stringResource(R.string.settings_reset_cancel)) }
            },
        )
    }
}

/** Button that, after a confirmation, puts every setting back to its default value. */
@Composable
private fun ResetAllButton(onConfirmed: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { asking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_reset))
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(onClick = { asking = false; onConfirmed() }) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) {
                    Text(stringResource(R.string.settings_reset_cancel))
                }
            },
        )
    }
}

/** Index of the choice closest to [minutes], to place the slider for a saved value. */
private fun nearestChoiceIndex(choices: List<Int>, minutes: Int): Int =
    choices.indices.minByOrNull { kotlin.math.abs(choices[it] - minutes) } ?: 0

/** "5 min", "1 h", "1 h 30", "48 h". */
private fun formatMinutes(context: android.content.Context, minutes: Int): String = when {
    minutes < 60 -> context.getString(R.string.settings_unit_minutes, minutes)
    minutes % 60 == 0 -> context.getString(R.string.settings_unit_hours, minutes / 60)
    else -> context.getString(R.string.settings_unit_hours_minutes, minutes / 60, minutes % 60)
}

/** The background refresh is overdue (shown in red) when it is older than this. */
private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

private val PRICE_AGE_DAYS_RANGE = 7f..365f
private val SHORTAGE_AGE_DAYS_RANGE = 1f..180f

/** Size factors snap to steps of 0.05, so the saved value stays readable. */
private fun roundedSizeFactor(raw: Float): Float = (raw * 20).roundToInt() / 20f

/** Radii snap to steps of 5 km and stay inside the allowed range. */
private fun roundedRadius(raw: Float): Int =
    ((raw / 5).roundToInt() * 5).coerceIn(MapDisplaySettings.MIN_START_RADIUS_KM, MapDisplaySettings.MAX_START_RADIUS_KM)

/** Nearest-stations radius, snapped to steps of 5 km inside the allowed range. */
private fun roundedNearestRadius(raw: Float): Int =
    ((raw / 5).roundToInt() * 5).coerceIn(NearestSettings.MIN_RADIUS_KM, NearestSettings.MAX_RADIUS_KM)

/** Tank size snapped to whole litres inside the allowed range. */
private fun roundedTank(raw: Float): Int =
    raw.roundToInt().coerceIn(NearestSettings.MIN_TANK_LITRES, NearestSettings.MAX_TANK_LITRES)

/** Consumption snapped to steps of 0.1 L/100 km inside the allowed range. */
private fun roundedConsumption(raw: Float): Float =
    ((raw * 10).roundToInt() / 10f).coerceIn(NearestSettings.MIN_CONSUMPTION, NearestSettings.MAX_CONSUMPTION)

/** "Last update: <date>" (or "never") under a refresh button. */
@Composable
private fun LastUpdate(epochMillis: Long?) {
    Text(
        stringResource(R.string.settings_last_update, epochMillis?.let(::formatDateTime) ?: stringResource(R.string.settings_last_update_never)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/**
 * A titled slider with its current value on the right.
 * @param valueText how to write a value ("90 d"); called with the value being dragged.
 * @param onCommit called once, with the final value, when the finger is lifted.
 */
@Composable
private fun SliderSetting(
    title: String,
    help: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    onCommit: (Float) -> Unit,
    steps: Int = 0,
) {
    // The value being dragged. Restarts from the saved value whenever that one changes.
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText(draft), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft) },
            valueRange = range,
            steps = steps,
        )
        help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
