package fr.opencarbu.data

import fr.opencarbu.domain.pricing.PricingSettings

/**
 * How [PricingSettings] are written to, and read back from, the app's saved settings.
 * Each threshold is a whole number of days under its own key.
 */
object PricingSettingsPreference {
    const val KEY_MAX_PRICE_AGE_DAYS = "max_price_age_days"
    const val KEY_MAX_SHORTAGE_AGE_DAYS = "max_shortage_age_days"

    /**
     * Settings for the saved values. A missing value (null), or one below one day, falls back
     * to the default of that threshold, so a corrupt entry can never break the map.
     */
    fun fromStored(maxPriceAgeDays: Int?, maxShortageAgeDays: Int?): PricingSettings = PricingSettings(
        maxPriceAgeDays = maxPriceAgeDays?.takeIf { it >= 1 } ?: PricingSettings.DEFAULT_MAX_PRICE_AGE_DAYS,
        maxShortageAgeDays = maxShortageAgeDays?.takeIf { it >= 1 } ?: PricingSettings.DEFAULT_MAX_SHORTAGE_AGE_DAYS,
    )
}
