package fr.opencarbu.ui.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.opencarbu.R
import fr.opencarbu.domain.history.PriceHistoryRules
import fr.opencarbu.domain.history.PricePoint
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.ui.labelRes
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** The periods offered under the curve: days, or [ALL] for everything recorded. */
private val PERIOD_DAYS = listOf(30, 90, 365, 0)
private const val ALL = 0
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * The price history of one station and fuel, as a step curve: the price stays the same until the
 * next recorded change. Drawn with the Canvas, no chart library. Only shown when the history is on.
 */
@Composable
fun PriceHistorySection(
    stationId: Long,
    fuel: FuelType,
    load: suspend (stationId: Long, fuel: FuelType, sinceEpochMillis: Long) -> List<PricePoint>,
) {
    var periodIndex by rememberSaveable { mutableStateOf(1) } // 3 months
    var curve by remember { mutableStateOf<List<PricePoint>?>(null) } // null = loading
    // Moments of the recorded changes: the curve also has a start and an end point that are not data.
    var recordedAt by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(stationId, fuel, periodIndex) {
        val now = System.currentTimeMillis()
        val days = PERIOD_DAYS[periodIndex]
        val from = if (days == ALL) 0L else now - days * MILLIS_PER_DAY
        val recorded = load(stationId, fuel, from)
        val start = if (days == ALL) recorded.firstOrNull()?.atEpochMillis ?: now else from
        recordedAt = recorded.mapTo(HashSet()) { it.atEpochMillis }
        curve = PriceHistoryRules.curve(recorded, start, now)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.history_title, stringResource(fuel.labelRes)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val labels = listOf(R.string.history_30d, R.string.history_3m, R.string.history_1y, R.string.history_all)
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = periodIndex == index,
                    onClick = { periodIndex = index },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        val points = curve
        when {
            points == null -> Unit
            points.isEmpty() -> Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Curve(points, recordedAt)
        }
    }
}

@Composable
private fun Curve(points: List<PricePoint>, recordedAt: Set<Long>) {
    val priceFormat = remember { NumberFormat.getInstance().apply { minimumFractionDigits = 3; maximumFractionDigits = 3 } }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val momentFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    // A flat curve gets some room above and below, so it is a line in the middle, not on an edge.
    val lowest = points.minOf { it.milliEuros }
    val highest = points.maxOf { it.milliEuros }
    val low = if (lowest == highest) lowest - FLAT_MARGIN else lowest
    val high = if (lowest == highest) highest + FLAT_MARGIN else highest
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val start = points.first().atEpochMillis
    val span = (points.last().atEpochMillis - start).coerceAtLeast(1L)
    // Position of a point as a fraction of the plot (0..1), shared by the drawing and the bubble.
    fun fractionX(at: Long) = (at - start).toFloat() / span
    fun fractionY(milli: Int) = (high - milli).toFloat() / (high - low)
    val tappable = points.filter { it.atEpochMillis in recordedAt }
    // The recorded price whose bubble is open (a tap on a dot); a new curve starts without one.
    var selected by remember(points) { mutableStateOf<PricePoint?>(null) }

    Row(Modifier.fillMaxWidth().height(CURVE_HEIGHT), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.sheet_price, priceFormat.format(high / 1000.0)), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.sheet_price, priceFormat.format(low / 1000.0)), style = MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(points) {
                    detectTapGestures { tap ->
                        // The nearest recorded price sideways, if the finger is close enough; else the bubble closes.
                        val nearest = tappable.minByOrNull { abs(fractionX(it.atEpochMillis) * size.width - tap.x) }
                        val close = nearest != null &&
                            abs(fractionX(nearest.atEpochMillis) * size.width - tap.x) <= TAP_REACH.toPx()
                        selected = if (close && nearest != selected) nearest else null
                    }
                },
            ) {
                fun x(at: Long) = size.width * fractionX(at)
                fun y(milli: Int) = size.height * fractionY(milli)
                drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f))
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height))
                val path = Path()
                points.forEachIndexed { index, point ->
                    if (index == 0) {
                        path.moveTo(x(point.atEpochMillis), y(point.milliEuros))
                    } else {
                        path.lineTo(x(point.atEpochMillis), y(points[index - 1].milliEuros)) // flat, then the step
                        path.lineTo(x(point.atEpochMillis), y(point.milliEuros))
                    }
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
                // A filled dot on each recorded price (not on the synthetic start and end of the window).
                for (point in tappable) {
                    val center = Offset(x(point.atEpochMillis), y(point.milliEuros))
                    if (point == selected) {
                        drawCircle(lineColor.copy(alpha = 0.25f), radius = SELECTED_HALO.toPx(), center = center)
                        drawCircle(lineColor, radius = SELECTED_RADIUS.toPx(), center = center)
                    } else {
                        drawCircle(lineColor, radius = DOT_RADIUS.toPx(), center = center)
                    }
                }
            }
            selected?.let { point ->
                // The bubble: a small raised card in the theme's own surface colours, above the dot (below it when
                // the dot is near the top), kept inside the plot.
                val fx = fractionX(point.atEpochMillis)
                val fy = fractionY(point.milliEuros)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = 4.dp,
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val gap = BUBBLE_GAP.roundToPx()
                        val left = (fx * constraints.maxWidth - placeable.width / 2f)
                            .coerceIn(0f, (constraints.maxWidth - placeable.width).coerceAtLeast(0).toFloat())
                        val above = fy * constraints.maxHeight - placeable.height - gap
                        val top = if (above >= 0f) above else fy * constraints.maxHeight + gap
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(left.roundToInt(), top.roundToInt())
                        }
                    },
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(
                            stringResource(R.string.sheet_price, priceFormat.format(point.milliEuros / 1000.0)),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(momentFormat.format(Date(point.atEpochMillis)), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = DATE_INDENT), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(dateFormat.format(Date(points.first().atEpochMillis)), style = MaterialTheme.typography.labelSmall)
        Text(dateFormat.format(Date(points.last().atEpochMillis)), style = MaterialTheme.typography.labelSmall)
    }
}

private val CURVE_HEIGHT = 120.dp

/** Radius of the dot drawn on each recorded price, of the selected one, and of the halo around it. */
private val DOT_RADIUS = 3.5.dp
private val SELECTED_RADIUS = 5.5.dp
private val SELECTED_HALO = 10.dp

/** How far sideways a tap may be from a dot and still select it. */
private val TAP_REACH = 72.dp

/** Space between a dot and its bubble. */
private val BUBBLE_GAP = 8.dp

/** Roughly the width of the price labels, so the dates sit under the curve. */
private val DATE_INDENT = 56.dp

/** Thousandths of a euro added above and below a curve that never changes. */
private const val FLAT_MARGIN = 10
