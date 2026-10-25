package fr.opencarbu.domain.pricing

/**
 * The four colours of a dot, from the cheapest to the most expensive quarter of the prices of
 * the whole country (see [NationalPriceScale]).
 */
enum class PriceBand(val argb: Int) {
    /** Lowest prices: up to the first quartile. */
    GREEN(0xFF00FF00.toInt()),

    /** Above the first quartile, up to the median. */
    YELLOW(0xFFFFFF00.toInt()),

    /** Above the median, up to the third quartile. */
    ORANGE(0xFFFF8000.toInt()),

    /** Above the third quartile: the highest prices. */
    RED(0xFFFF0000.toInt()),
}

/**
 * How a price ranks against ALL the stations of the country that sell the same fuel (recent
 * prices only), whatever part of the map is on screen: a dot keeps its colour when the user pans
 * or zooms.
 *
 *  - above the third quartile: red;
 *  - above the median: orange;
 *  - above the first quartile: yellow;
 *  - otherwise: green.
 *
 * A price exactly equal to a quartile belongs to the lower band. Prices have three decimals, so
 * many stations share the same value: if every price is equal, everything is green.
 */
class NationalPriceScale private constructor(
    val firstQuartile: Double,
    val median: Double,
    val thirdQuartile: Double,
    private val range: PriceRange,
) {
    fun bandOf(price: Double): PriceBand = when {
        price > thirdQuartile -> PriceBand.RED
        price > median -> PriceBand.ORANGE
        price > firstQuartile -> PriceBand.YELLOW
        else -> PriceBand.GREEN
    }

    /**
     * Fine position of [price], 0.0 (cheapest) to 1.0 (dearest), among the prices of the country.
     * Not used for the colour (see [bandOf]): it only orders dots whose labels collide.
     */
    fun ratioOf(price: Double): Float = range.ratioOf(price)

    companion object {
        /** The scale for [prices] (any order, not modified), or null when there is none. */
        fun from(prices: DoubleArray): NationalPriceScale? {
            if (prices.isEmpty()) return null
            val sorted = prices.copyOf().also { it.sort() }
            val range = PriceRange.from(sorted) ?: return null
            return NationalPriceScale(
                firstQuartile = PriceRange.percentile(sorted, 0.25),
                median = PriceRange.percentile(sorted, 0.50),
                thirdQuartile = PriceRange.percentile(sorted, 0.75),
                range = range,
            )
        }
    }
}
