package fr.opencarbu.domain.pricing

import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.Station

/** What a station's dot shows for the selected fuel. */
enum class PriceStatus {
    /** Recent price: drawn on the green→red scale and used to build that scale. */
    FRESH,

    /** Price older than the allowed age (or of unknown age): grey dot, ignored by the scale. */
    STALE,

    /** The station reports the selected fuel as temporarily out of stock: grey dot + red badge. */
    TEMPORARILY_OUT,

    /** Test aid: a station that only the reference file knows, with no prices at all. */
    REFERENCE_ONLY,
}

/** A station together with everything needed to draw its dot on the map. */
class PricedStation(
    val station: Station,
    /** Price of the selected fuel in €/L, or null if the station reports none (e.g. shortage). */
    val euroPerLitre: Double?,
    val status: PriceStatus,
    /**
     * 0.0 = cheapest of the country, 1.0 = most expensive. Null unless [status] is FRESH. Only
     * orders dots whose labels collide: the colour comes from [band].
     */
    val ratio: Float?,
    /** Dot colour as ARGB: that of [band] if FRESH, grey (or violet) otherwise (see [PriceColors]). */
    val colorArgb: Int,
    /** Quarter of the national prices this price falls in. Null unless [status] is FRESH. */
    val band: PriceBand? = null,
) {
    /** True if the red "shortage" badge must be drawn on the dot. */
    val showsShortageBadge: Boolean get() = status == PriceStatus.TEMPORARILY_OUT
}

/**
 * Core feature of the app: given the visible map area and the selected fuel, decide
 * which stations to draw and with which colour.
 *
 * The colour of a dot is relative to the FRESH prices of ALL the stations of the country (see
 * [NationalPriceScale]): it does not depend on what is on screen. Old prices and shortages are
 * drawn grey and left out of the scale, so they cannot distort it.
 *
 * Beware when choosing the "old price" threshold: many stations keep a stable price for
 * weeks (E85 especially), so a short threshold greys out prices that are still valid.
 * The thresholds come from [PricingSettings] (default: 90 days).
 */
object ViewportPricing {

    /** Default age beyond which a price is STALE (90 days). */
    const val DEFAULT_MAX_PRICE_AGE_MILLIS: Long =
        PricingSettings.DEFAULT_MAX_PRICE_AGE_DAYS * PricingSettings.MILLIS_PER_DAY

    /**
     * "Temporary" shortages that began more than this long ago (30 days) are ignored: a
     * station that has been "temporarily" out of a fuel for months simply does not sell it,
     * and drawing it would clutter the map with red badges.
     */
    const val DEFAULT_MAX_SHORTAGE_AGE_MILLIS: Long =
        PricingSettings.DEFAULT_MAX_SHORTAGE_AGE_DAYS * PricingSettings.MILLIS_PER_DAY

    /**
     * @param stations every known station (the whole country, kept in memory: ~10 000 entries).
     * @param fuel the fuel selected by the user.
     * @param viewport the visible map area; stations outside are skipped.
     * @param nowEpochMillis the current time. Passed in (rather than read from the clock here)
     *   so the function stays deterministic and easy to test.
     * @param maxPriceAgeMillis age beyond which a price is STALE. A price of unknown age is
     *   STALE too: it cannot be vouched for.
     * @param scale the national price scale for [fuel], see [nationalScale]. Built here when null;
     *   callers that draw often should compute it once and pass it in.
     * @param maxShortageAgeMillis a temporary shortage older than this is ignored, as if the
     *   station had not reported it (so, with no price either, the station is not drawn).
     *   A shortage with an unknown start date is kept: it cannot be proven old.
     * @return one [PricedStation] per drawable station, in the input order. A station is
     *   drawable if it is in view and either has a price for [fuel] or reports a recent
     *   temporary shortage of it.
     */
    fun compute(
        stations: List<Station>,
        fuel: FuelType,
        viewport: BoundingBox,
        nowEpochMillis: Long,
        maxPriceAgeMillis: Long = DEFAULT_MAX_PRICE_AGE_MILLIS,
        maxShortageAgeMillis: Long = DEFAULT_MAX_SHORTAGE_AGE_MILLIS,
        scale: NationalPriceScale? = nationalScale(stations, fuel, nowEpochMillis, maxPriceAgeMillis, maxShortageAgeMillis),
    ): List<PricedStation> {
        // Pass 1: keep stations that are in view and concern the selected fuel.
        // A plain linear scan of ~10 000 items takes well under a millisecond;
        // a spatial index would be premature optimisation.
        val inView = ArrayList<Station>()
        for (station in stations) {
            val concernsFuel = station.isReferenceOnly || fuel in station.prices ||
                hasActiveShortage(station, fuel, nowEpochMillis, maxShortageAgeMillis)
            if (concernsFuel && viewport.contains(station.latitude, station.longitude)) {
                inView.add(station)
            }
        }
        if (inView.isEmpty()) return emptyList()

        // Pass 2: classify each station and attach its colour from the national scale.
        return List(inView.size) { index ->
            val station = inView[index]
            val status = statusOf(station, fuel, nowEpochMillis, maxPriceAgeMillis, maxShortageAgeMillis)
            val price = station.prices[fuel]?.euroPerLitre
            val band = if (status == PriceStatus.FRESH && price != null) scale?.bandOf(price) else null
            val ratio = if (band != null && price != null) scale?.ratioOf(price) else null
            val color = when {
                band != null -> band.argb
                status == PriceStatus.REFERENCE_ONLY -> PriceColors.REFERENCE_ONLY_ARGB
                else -> PriceColors.UNRELIABLE_ARGB
            }
            PricedStation(station, price, status, ratio, color, band)
        }
    }

    /**
     * The price scale of the whole country for [fuel]: built from the FRESH prices of every
     * station in [stations] (not only the visible ones). Null when no station has a fresh price.
     */
    fun nationalScale(
        stations: List<Station>,
        fuel: FuelType,
        nowEpochMillis: Long,
        maxPriceAgeMillis: Long = DEFAULT_MAX_PRICE_AGE_MILLIS,
        maxShortageAgeMillis: Long = DEFAULT_MAX_SHORTAGE_AGE_MILLIS,
    ): NationalPriceScale? {
        var prices = DoubleArray(stations.size.coerceAtMost(INITIAL_SCALE_CAPACITY))
        var count = 0
        for (station in stations) {
            if (station.isReferenceOnly || fuel !in station.prices) continue
            if (statusOf(station, fuel, nowEpochMillis, maxPriceAgeMillis, maxShortageAgeMillis) != PriceStatus.FRESH) continue
            if (count == prices.size) prices = prices.copyOf(prices.size * 2 + 1)
            prices[count++] = station.prices.getValue(fuel).euroPerLitre
        }
        return NationalPriceScale.from(prices.copyOf(count))
    }

    private const val INITIAL_SCALE_CAPACITY = 16_384

    /**
     * Precondition: the station has a price for [fuel] OR an active shortage of it
     * (guaranteed by pass 1 of [compute]).
     */
    private fun statusOf(
        station: Station,
        fuel: FuelType,
        now: Long,
        maxPriceAgeMillis: Long,
        maxShortageAgeMillis: Long,
    ): PriceStatus {
        if (station.isReferenceOnly) return PriceStatus.REFERENCE_ONLY
        if (hasActiveShortage(station, fuel, now, maxShortageAgeMillis)) return PriceStatus.TEMPORARILY_OUT
        // No active shortage, so the station must have a price (an old shortage flag is ignored).
        val updatedAt = station.prices.getValue(fuel).updatedAtEpochMillis
        // An unknown update date cannot be vouched for, so it counts as stale.
        return if (updatedAt == null || now - updatedAt > maxPriceAgeMillis) PriceStatus.STALE else PriceStatus.FRESH
    }

    /** True if the station reports a temporary shortage of [fuel] that is recent (or of unknown start). */
    fun hasActiveShortage(station: Station, fuel: FuelType, now: Long, maxAgeMillis: Long): Boolean {
        if (fuel !in station.temporaryShortages) return false
        val startedAt = station.temporaryShortages[fuel] ?: return true // unknown start: cannot call it old
        return now - startedAt <= maxAgeMillis
    }
}
