package fr.opencarbu.domain.pricing

/**
 * The thresholds that decide how a station is drawn. They are meant to become user settings
 * (settings screen to come); until then the defaults apply.
 *
 * @property maxPriceAgeDays a price not updated for longer than this many days is drawn grey,
 *   as it may be outdated.
 * @property maxShortageAgeDays a "temporary" shortage that began longer ago than this many days
 *   is ignored (the station most likely no longer sells that fuel).
 */
data class PricingSettings(
    val maxPriceAgeDays: Int = DEFAULT_MAX_PRICE_AGE_DAYS,
    val maxShortageAgeDays: Int = DEFAULT_MAX_SHORTAGE_AGE_DAYS,
) {
    init {
        require(maxPriceAgeDays >= 1 && maxShortageAgeDays >= 1) { "Thresholds must be at least one day" }
    }

    val maxPriceAgeMillis: Long get() = maxPriceAgeDays * MILLIS_PER_DAY
    val maxShortageAgeMillis: Long get() = maxShortageAgeDays * MILLIS_PER_DAY

    companion object {
        /** About three months. */
        const val DEFAULT_MAX_PRICE_AGE_DAYS = 90
        const val DEFAULT_MAX_SHORTAGE_AGE_DAYS = 30
        const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000
    }
}
