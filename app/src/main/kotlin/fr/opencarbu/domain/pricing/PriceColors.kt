package fr.opencarbu.domain.pricing

import kotlin.math.roundToInt

/**
 * Maps a price ratio (0.0 cheapest … 1.0 most expensive) to a colour.
 *
 * The colour is interpolated on the HUE axis of the HSV model, from 120° (pure green)
 * to 0° (pure red), at full saturation and value. This passes through yellow and orange,
 * which reads naturally as "cheap → expensive". Interpolating directly between the
 * RGB values of green and red would instead go through a dull brown in the middle.
 *
 * This code is plain Kotlin (no android.graphics.Color) so it can be unit-tested on the JVM.
 * Colours are returned as ARGB ints, the format Android and MapLibre both understand.
 *
 * Text drawn on top of these colours should be black: it has better contrast than
 * white on every colour of this scale (including yellow and pure green).
 */
object PriceColors {
    /** Dot colour when the price cannot be trusted (too old) or the fuel is out of stock. */
    const val UNRELIABLE_ARGB: Int = 0xFFBDBDBD.toInt()

    /** Test aid: dot of a station that is in the reference file but not in the price feed. */
    const val REFERENCE_ONLY_ARGB: Int = 0xFF8E24AA.toInt()

    /** Colour of the small badge flagging a temporary shortage of the selected fuel. */
    const val SHORTAGE_BADGE_ARGB: Int = 0xFFD32F2F.toInt()

    private const val GREEN_HUE_DEGREES = 120f
    private const val RED_HUE_DEGREES = 0f
    private const val OPAQUE = 0xFF shl 24

    /** Colour for [ratio]; values outside 0..1 are clamped. */
    fun argbForRatio(ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val hue = GREEN_HUE_DEGREES + (RED_HUE_DEGREES - GREEN_HUE_DEGREES) * t
        return hueToArgb(hue)
    }

    /**
     * HSV → RGB for saturation = value = 1 and a hue between 0° and 120°.
     * Within that span, the colour moves along one edge of the RGB cube:
     *  - 0°..60°   : red stays at 100%, green rises from 0% to 100% (red → yellow)
     *  - 60°..120° : green stays at 100%, red falls from 100% to 0% (yellow → green)
     */
    private fun hueToArgb(hueDegrees: Float): Int {
        val sector = hueDegrees / 60f // 0.0 .. 2.0
        val red: Float
        val green: Float
        if (sector < 1f) {
            red = 1f
            green = sector
        } else {
            red = 2f - sector
            green = 1f
        }
        return OPAQUE or (toByte(red) shl 16) or (toByte(green) shl 8)
    }

    private fun toByte(channel: Float): Int = (channel * 255f).roundToInt().coerceIn(0, 255)
}
