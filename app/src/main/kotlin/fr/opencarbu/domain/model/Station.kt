package fr.opencarbu.domain.model

/**
 * The price of one fuel at one station.
 *
 * @property euroPerLitre Price in euros per litre (e.g. 1.789).
 * @property updatedAtEpochMillis When the station last reported this price, or null if unknown.
 *   Useful to warn users about stale prices.
 */
data class FuelPrice(
    val euroPerLitre: Double,
    val updatedAtEpochMillis: Long?,
)

/**
 * A fuel station and the prices it currently reports.
 *
 * Only fuels that have a valid price are present in [prices]; a missing key means
 * "not sold here, out of stock, or price invalid". Temporary shortages are the one
 * case reported separately, in [temporaryShortages], so they can be shown on the map.
 */
data class Station(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val postalCode: String,
    val city: String,
    val prices: Map<FuelType, FuelPrice>,
    val isOpen24h: Boolean,
    /**
     * Fuels this station reports as TEMPORARILY out of stock (as opposed to permanently no
     * longer sold), each mapped to the moment the shortage began (epoch millis), or null if
     * the feed does not say. In practice a fuel in shortage carries no price.
     *
     * Beware: "temporary" is declared by the stations themselves and some never clear it
     * (shortages of over a year exist), hence the start date: see
     * [fr.opencarbu.domain.pricing.ViewportPricing.DEFAULT_MAX_SHORTAGE_AGE_MILLIS].
     */
    val temporaryShortages: Map<FuelType, Long?> = emptyMap(),
    /**
     * True for a station that only the reference file (OpenStreetMap) knows: the price feed has
     * no entry for it, so it has no prices. Only built when the test option is on, see
     * [StationPositions].
     */
    val isReferenceOnly: Boolean = false,
)
