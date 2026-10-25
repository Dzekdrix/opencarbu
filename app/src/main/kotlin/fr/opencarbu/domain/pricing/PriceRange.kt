package fr.opencarbu.domain.pricing

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The price interval used to turn a price into a position on the green→red scale.
 *
 * Why percentiles instead of plain min/max?
 * A single station with an absurdly low or high price (data entry error, motorway
 * station...) would otherwise squash every other station into one colour. By default
 * the range spans the 5th to the 95th percentile; prices outside are clamped to 0 or 1.
 *
 * With few stations (< [MIN_SAMPLES_FOR_TRIMMING]) trimming 5% makes no sense,
 * so the exact min and max are used instead.
 */
class PriceRange private constructor(
    /** Price mapped to ratio 0.0 (greenest). */
    val low: Double,
    /** Price mapped to ratio 1.0 (reddest). */
    val high: Double,
) {

    /**
     * Position of [price] inside this range, from 0.0 (cheapest) to 1.0 (most expensive).
     * Values outside the range are clamped. If every price is identical the range has
     * no width and 0.0 (green) is returned: nothing is "expensive" when all are equal.
     */
    fun ratioOf(price: Double): Float {
        val span = high - low
        if (span <= 0.0) return 0f
        return ((price - low) / span).coerceIn(0.0, 1.0).toFloat()
    }

    companion object {
        /** Below this number of prices, the exact min/max are used instead of percentiles. */
        const val MIN_SAMPLES_FOR_TRIMMING = 20
        const val DEFAULT_LOW_PERCENTILE = 0.05
        const val DEFAULT_HIGH_PERCENTILE = 0.95

        /**
         * Builds the range for a set of prices, or returns null if [prices] is empty.
         * The input array is not modified.
         *
         * @param lowPercentile fraction in [0, 1) mapped to the greenest colour.
         * @param highPercentile fraction in (0, 1] mapped to the reddest colour.
         */
        fun from(
            prices: DoubleArray,
            lowPercentile: Double = DEFAULT_LOW_PERCENTILE,
            highPercentile: Double = DEFAULT_HIGH_PERCENTILE,
        ): PriceRange? {
            require(lowPercentile in 0.0..1.0 && highPercentile in 0.0..1.0 && lowPercentile < highPercentile) {
                "Percentiles must satisfy 0 <= low < high <= 1"
            }
            if (prices.isEmpty()) return null

            val sorted = prices.copyOf().also { it.sort() }
            if (sorted.size < MIN_SAMPLES_FOR_TRIMMING) {
                return PriceRange(sorted.first(), sorted.last())
            }
            return PriceRange(
                low = percentile(sorted, lowPercentile),
                high = percentile(sorted, highPercentile),
            )
        }

        /**
         * Percentile of an already sorted, non-empty array, using linear interpolation
         * between the two nearest values (same method as NumPy's default).
         */
        internal fun percentile(sorted: DoubleArray, fraction: Double): Double {
            val position = fraction * (sorted.size - 1)
            val lowerIndex = floor(position).toInt()
            val upperIndex = ceil(position).toInt()
            val weightOfUpper = position - lowerIndex
            return sorted[lowerIndex] * (1.0 - weightOfUpper) + sorted[upperIndex] * weightOfUpper
        }
    }
}
