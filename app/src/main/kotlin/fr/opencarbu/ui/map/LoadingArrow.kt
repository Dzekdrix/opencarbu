package fr.opencarbu.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The animation has this many images: one turn, 6 degrees apart. */
private const val FRAME_COUNT = 60

/**
 * Time between two images. At least this long, and synchronised with the screen's refresh (see
 * below), so on a 60 Hz screen every refresh shows the next image: a turn takes about a second.
 */
private const val FRAME_NANOS = 16_000_000L

/**
 * Loading animation: a black arrow bent into a circle (drawn like the icons of the map buttons:
 * plain strokes) on a rounded rectangle with the buttons' look, shown as a series of [FRAME_COUNT]
 * images one after the other, each turned a bit further than the previous one.
 *
 * It only draws; it does not intercept touches, so the interface below stays usable.
 *
 * @param size width and height of the whole rounded rectangle.
 */
@Composable
fun LoadingArrow(size: Dp, modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var lastChange = 0L
        while (true) {
            // Waits for the screen's next refresh (instead of a timer that drifts against it), and
            // moves to the next image once FRAME_NANOS have passed.
            withFrameNanos { now ->
                if (now - lastChange >= FRAME_NANOS) {
                    frame = (frame + 1) % FRAME_COUNT
                    lastChange = now
                }
            }
        }
    }
    Surface(
        modifier = modifier.size(size),
        // Same look as the map buttons (surface colour, shadow), with the corners scaled to the size.
        shape = RoundedCornerShape(size * CORNER_FRACTION),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Box(Modifier.padding(size * MARGIN_FRACTION), contentAlignment = Alignment.Center) {
            // `frame` is read while drawing only: each new image redraws the arrow, nothing is recomposed.
            Canvas(Modifier.fillMaxSize()) {
                val stroke = this.size.minDimension * STROKE_FRACTION
                val radius = (this.size.minDimension - stroke) / 2f * RADIUS_FRACTION
                val centre = center
                rotate(degrees = frame * (360f / FRAME_COUNT), pivot = centre) {
                    // An open ring: the arrow runs clockwise along it from the top, 270 degrees.
                    drawArc(
                        color = Color.Black,
                        startAngle = -90f,
                        sweepAngle = ARC_SWEEP_DEGREES,
                        useCenter = false,
                        topLeft = Offset(centre.x - radius, centre.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    // Arrow head at the end of the arc: two strokes meeting at the tip, pointing along the ring.
                    val endAngle = Math.toRadians((-90f + ARC_SWEEP_DEGREES).toDouble())
                    val tip = Offset(
                        centre.x + radius * Math.cos(endAngle).toFloat(),
                        centre.y + radius * Math.sin(endAngle).toFloat(),
                    )
                    // Direction of travel at the tip (clockwise): perpendicular to the radius.
                    val travel = Offset(-Math.sin(endAngle).toFloat(), Math.cos(endAngle).toFloat())
                    val outward = Offset(Math.cos(endAngle).toFloat(), Math.sin(endAngle).toFloat())
                    val head = radius * HEAD_FRACTION
                    for (side in listOf(-1f, 1f)) {
                        val back = Offset(
                            tip.x - travel.x * head + outward.x * head * side,
                            tip.y - travel.y * head + outward.y * head * side,
                        )
                        drawLine(Color.Black, tip, back, stroke, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/** Corner radius of the rounded rectangle, relative to its size (the buttons: 12 dp for 40 dp). */
private const val CORNER_FRACTION = 0.3f

/** Empty margin between the rounded rectangle's edge and the ring. */
private const val MARGIN_FRACTION = 0.1f

private const val STROKE_FRACTION = 0.05f
private const val RADIUS_FRACTION = 0.9f
private const val ARC_SWEEP_DEGREES = 270f
private const val HEAD_FRACTION = 0.4f
