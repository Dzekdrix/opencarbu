package fr.opencarbu.domain

import fr.opencarbu.domain.model.BoundingBox
import fr.opencarbu.domain.model.FuelPrice
import fr.opencarbu.domain.model.FuelType
import fr.opencarbu.domain.model.Station
import fr.opencarbu.domain.pricing.PriceBand
import fr.opencarbu.domain.pricing.PriceColors
import fr.opencarbu.domain.pricing.PriceStatus
import fr.opencarbu.domain.pricing.ViewportPricing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewportPricingTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val paris = BoundingBox(south = 48.0, west = 2.0, north = 49.0, east = 3.0)

    /**
     * Builds a station selling one fuel.
     * @param price null = no price for the fuel; @param ageDays null = unknown update date.
     * @param shortage true = the fuel is reported as temporarily out, since [shortageAgeDays] days.
     */
    private fun station(
        id: Long,
        lat: Double = 48.5,
        lon: Double = 2.5,
        fuel: FuelType = FuelType.DIESEL,
        price: Double? = 1.80,
        ageDays: Long? = 0,
        shortage: Boolean = false,
        shortageAgeDays: Long? = 0, // null = the feed gives no start date
    ) = Station(
        id = id, latitude = lat, longitude = lon, address = "", postalCode = "", city = "",
        prices = if (price == null) emptyMap() else mapOf(fuel to FuelPrice(price, ageDays?.let { now - it * day })),
        isOpen24h = false,
        temporaryShortages = if (shortage) mapOf(fuel to shortageAgeDays?.let { now - it * day }) else emptyMap(),
    )

    private fun compute(stations: List<Station>, fuel: FuelType = FuelType.DIESEL) =
        ViewportPricing.compute(stations, fuel, paris, now)

    @Test
    fun skipsStationsOutsideViewportOrWithoutTheFuel() {
        val result = compute(
            listOf(
                station(1, price = 1.80),                              // kept
                station(2, price = 1.90),                              // kept
                station(3, lat = 45.0, lon = 5.0, price = 1.50),       // outside the box
                station(4, fuel = FuelType.SP95, price = 1.70),        // other fuel only
            ),
        )
        assertEquals(listOf(1L, 2L), result.map { it.station.id })
    }

    @Test
    fun colourDoesNotDependOnWhatIsVisible() {
        // Four stations across the country: one price per quarter. Only the first two are on screen.
        val all = listOf(
            station(1, price = 1.70),
            station(2, price = 1.90),
            station(3, lat = 45.0, lon = 5.0, price = 1.80),
            station(4, lat = 45.0, lon = 5.0, price = 2.00),
        )
        val visible = compute(all)
        assertEquals(listOf(1L, 2L), visible.map { it.station.id })
        // Against the whole country 1.70 is the cheapest and 1.90 sits in the upper half.
        assertEquals(PriceBand.GREEN, visible[0].band)
        assertEquals(PriceBand.ORANGE, visible[1].band)
        // Against its two visible stations alone, 1.90 would have been red.
        val zoomed = ViewportPricing.compute(all.take(2), FuelType.DIESEL, paris, now)
        assertEquals(PriceBand.RED, zoomed[1].band)
        assertEquals(PriceBand.ORANGE.argb, visible[1].colorArgb)
    }

    @Test
    fun bandsFollowTheQuartilesOfTheNationalPrices() {
        // 1.00 ... 2.00 in eleven equal steps: Q1 = 1.25, median = 1.50, Q3 = 1.75.
        val stations = (0..10).map { station(it + 1L, price = 1.0 + it / 10.0) }
        val bands = compute(stations).map { it.band }
        assertEquals(PriceBand.GREEN, bands[0])   // 1.00
        assertEquals(PriceBand.GREEN, bands[2])   // 1.20 < Q1
        assertEquals(PriceBand.YELLOW, bands[3])  // 1.30
        assertEquals(PriceBand.YELLOW, bands[5])  // 1.50 = median: stays in the lower band
        assertEquals(PriceBand.ORANGE, bands[6])  // 1.60
        assertEquals(PriceBand.ORANGE, bands[7])  // 1.70 < Q3
        assertEquals(PriceBand.RED, bands[8])     // 1.80 > Q3
        assertEquals(PriceBand.RED, bands[10])    // 2.00
    }

    @Test
    fun allPricesEqualMeansEverythingIsGreen() {
        val bands = compute(listOf(station(1), station(2), station(3))).map { it.band }
        assertTrue(bands.all { it == PriceBand.GREEN })
    }

    @Test
    fun oldPriceIsGreyAndDoesNotInfluenceTheScale() {
        val (a, b, old) = compute(
            listOf(
                station(1, price = 1.80),
                station(2, price = 1.90),
                station(3, price = 1.20, ageDays = 100), // much cheaper, but older than 90 days
            ),
        )
        assertEquals(PriceStatus.STALE, old.status)
        assertEquals(PriceColors.UNRELIABLE_ARGB, old.colorArgb)
        assertNull(old.ratio)
        assertEquals(1.20, old.euroPerLitre) // the price is still displayed, just greyed out
        assertFalse(old.showsShortageBadge)
        // The fresh stations still span the full scale: the old 1.20 did not shift it.
        assertEquals(PriceBand.GREEN, a.band)
        assertEquals(PriceBand.RED, b.band)
    }

    @Test
    fun aPriceOfAFewWeeksIsStillFreshByDefault() {
        // Stable prices are common (E85 especially): the default threshold is three months.
        assertEquals(PriceStatus.FRESH, compute(listOf(station(1, ageDays = 60))).single().status)
    }

    @Test
    fun priceExactlyAtTheMaximumAgeIsStillFresh() {
        val ninetyDays = ViewportPricing.DEFAULT_MAX_PRICE_AGE_MILLIS / day
        assertEquals(90L, ninetyDays)
        assertEquals(PriceStatus.FRESH, compute(listOf(station(1, ageDays = ninetyDays))).single().status)
        assertEquals(PriceStatus.STALE, compute(listOf(station(1, ageDays = ninetyDays + 1))).single().status)
    }

    @Test
    fun theAgeThresholdIsConfigurable() {
        val stations = listOf(station(1, ageDays = 20))
        assertEquals(PriceStatus.FRESH, ViewportPricing.compute(stations, FuelType.DIESEL, paris, now).single().status)
        val strict = ViewportPricing.compute(stations, FuelType.DIESEL, paris, now, maxPriceAgeMillis = 7 * day)
        assertEquals(PriceStatus.STALE, strict.single().status)
    }

    @Test
    fun unknownUpdateDateCountsAsStale() {
        assertEquals(PriceStatus.STALE, compute(listOf(station(1, ageDays = null))).single().status)
    }

    @Test
    fun onlyStalePricesMeansEveryDotIsGrey() {
        val result = compute(listOf(station(1, ageDays = 200), station(2, price = 2.0, ageDays = 300)))
        assertTrue(result.all { it.status == PriceStatus.STALE && it.colorArgb == PriceColors.UNRELIABLE_ARGB })
    }

    @Test
    fun temporaryShortageIsGreyWithBadgeAndOutsideTheScale() {
        val (a, b, out) = compute(
            listOf(
                station(1, price = 1.80),
                station(2, price = 1.90),
                station(3, price = 1.10, shortage = true), // priced but out of stock
            ),
        )
        assertEquals(PriceStatus.TEMPORARILY_OUT, out.status)
        assertTrue(out.showsShortageBadge)
        assertEquals(PriceColors.UNRELIABLE_ARGB, out.colorArgb)
        assertEquals(PriceBand.GREEN, a.band)
        assertEquals(PriceBand.RED, b.band)
    }

    @Test
    fun shortageWithoutAnyPriceIsStillDrawn() {
        val out = compute(listOf(station(1, price = null, shortage = true))).single()
        assertEquals(PriceStatus.TEMPORARILY_OUT, out.status)
        assertNull(out.euroPerLitre)
        assertTrue(out.showsShortageBadge)
    }

    @Test
    fun shortageOfAnotherFuelDoesNotMatter() {
        assertTrue(compute(listOf(station(1, fuel = FuelType.E10, price = null, shortage = true))).isEmpty())
    }

    @Test
    fun emptyWhenNothingVisible() {
        assertTrue(compute(emptyList(), FuelType.E10).isEmpty())
    }

    // ---- Old "temporary" shortages ----

    @Test
    fun shortageOlderThanTheThresholdHidesThePricelessStation() {
        val thirty = ViewportPricing.DEFAULT_MAX_SHORTAGE_AGE_MILLIS / day
        // Exactly at the threshold: still drawn. One day more: not drawn at all.
        assertEquals(1, compute(listOf(station(1, price = null, shortage = true, shortageAgeDays = thirty))).size)
        assertTrue(compute(listOf(station(1, price = null, shortage = true, shortageAgeDays = thirty + 1))).isEmpty())
    }

    @Test
    fun shortageWithUnknownStartDateIsKept() {
        val result = compute(listOf(station(1, price = null, shortage = true, shortageAgeDays = null)))
        assertEquals(PriceStatus.TEMPORARILY_OUT, result.single().status)
    }

    @Test
    fun oldShortageFlagIsIgnoredWhenTheStationStillHasAPrice() {
        // Contradictory data (a fuel "out for a year" yet priced today): trust the fresh price.
        val result = compute(listOf(station(1, price = 1.85, shortage = true, shortageAgeDays = 400))).single()
        assertEquals(PriceStatus.FRESH, result.status)
        assertFalse(result.showsShortageBadge)
    }

    @Test
    fun theShortageThresholdIsConfigurable() {
        val stations = listOf(station(1, price = null, shortage = true, shortageAgeDays = 10))
        // Default (30 days): drawn. A user setting of 7 days: hidden. A setting of 90 days: drawn.
        assertEquals(1, ViewportPricing.compute(stations, FuelType.DIESEL, paris, now).size)
        assertTrue(ViewportPricing.compute(stations, FuelType.DIESEL, paris, now, maxShortageAgeMillis = 7 * day).isEmpty())
        assertEquals(1, ViewportPricing.compute(stations, FuelType.DIESEL, paris, now, maxShortageAgeMillis = 90 * day).size)
    }

    @Test
    fun realCaseLeCheylasSp95IsHiddenButItsOtherFuelsAreNot() {
        // Le Cheylas: SP95 "temporarily" out since June 2025, diesel priced and fresh.
        val ageOfShortage = 487L // days between 2025-06-06 and 2026-10-06
        val station = Station(
            id = 38570003, latitude = 48.5, longitude = 2.5, address = "", postalCode = "", city = "Le Cheylas",
            prices = mapOf(FuelType.DIESEL to FuelPrice(2.397, now - day)),
            isOpen24h = true,
            temporaryShortages = mapOf(FuelType.SP95 to now - ageOfShortage * day),
        )
        assertTrue(compute(listOf(station), FuelType.SP95).isEmpty())
        assertEquals(PriceStatus.FRESH, compute(listOf(station), FuelType.DIESEL).single().status)
    }
}
