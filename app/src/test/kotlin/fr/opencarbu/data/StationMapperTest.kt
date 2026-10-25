package fr.opencarbu.data

import fr.opencarbu.domain.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationMapperTest {

    /** A realistic, fully valid record. Tests start from it and break one thing at a time. */
    private fun record(vararg overrides: Pair<String, Any?>): RawRecord = mapOf<String, Any?>(
        "id" to 13001001,
        "adresse" to "1 rue de la Paix",
        "cp" to "13001",
        "ville" to "Marseille",
        "geom" to mapOf("lat" to 43.2965, "lon" to 5.3698),
        "horaires_automate_24_24" to "Oui",
        "gazole_prix" to 1.789,
        "gazole_maj" to "2026-10-08T07:12:00+00:00",
        "e10_prix" to "1,659",
    ) + overrides

    @Test
    fun mapsAFullRecord() {
        val station = assertNotNull(StationMapper.map(record()))
        assertEquals(13001001L, station.id)
        assertEquals("Marseille", station.city)
        assertEquals(43.2965, station.latitude)
        assertEquals(5.3698, station.longitude)
        assertTrue(station.isOpen24h)
        assertEquals(1.789, station.prices.getValue(FuelType.DIESEL).euroPerLitre)
        assertEquals(1791443520000L, station.prices.getValue(FuelType.DIESEL).updatedAtEpochMillis)
    }

    @Test
    fun acceptsFrenchDecimalCommaInPrices() {
        val station = assertNotNull(StationMapper.map(record()))
        assertEquals(1.659, station.prices.getValue(FuelType.E10).euroPerLitre)
        assertNull(station.prices.getValue(FuelType.E10).updatedAtEpochMillis) // no *_maj column
    }

    @Test
    fun fuelsWithoutPriceAreAbsent() {
        val station = assertNotNull(StationMapper.map(record()))
        assertFalse(FuelType.SP98 in station.prices)
    }

    @Test
    fun implausiblePricesAreIgnored() {
        val station = assertNotNull(StationMapper.map(record("gazole_prix" to 1789.0, "e10_prix" to 0.0)))
        assertTrue(station.prices.isEmpty())
    }

    @Test
    fun fallsBackToLatitudeLongitudeColumns() {
        val station = assertNotNull(
            StationMapper.map(record("geom" to null, "latitude" to 43.2965, "longitude" to 5.3698)),
        )
        assertEquals(43.2965, station.latitude)
    }

    @Test
    fun rescalesCoordinatesExpressedInMicroDegrees() {
        val station = assertNotNull(
            StationMapper.map(record("geom" to null, "latitude" to "4329650", "longitude" to "536980")),
        )
        assertEquals(43.2965, station.latitude, 1e-9)
        assertEquals(5.3698, station.longitude, 1e-9)
    }

    @Test
    fun dropsRecordsWithoutUsablePosition() {
        assertNull(StationMapper.map(record("geom" to null)))
        assertNull(StationMapper.map(record("geom" to mapOf("lat" to 0.0, "lon" to 0.0))))
        assertNull(StationMapper.map(record("geom" to mapOf("lat" to 123.0, "lon" to 5.0, "x" to 1))))
    }

    @Test
    fun dropsRecordsWithoutId() {
        assertNull(StationMapper.map(record("id" to null)))
    }

    @Test
    fun malformedUpdateDateBecomesNull() {
        val station = assertNotNull(StationMapper.map(record("gazole_maj" to "yesterday")))
        assertNull(station.prices.getValue(FuelType.DIESEL).updatedAtEpochMillis)
    }

    // ---- Real records, copied from the official feed on 2026-10-08 (irrelevant columns omitted) ----

    private val realStationWithLpg: RawRecord = mapOf(
        "id" to 40500002, "latitude" to "4377300", "longitude" to "-56700",
        "cp" to "40500", "adresse" to "Route de Mont de Marsan", "ville" to "Saint-Sever",
        "geom" to mapOf("lon" to -0.567, "lat" to 43.773),
        "gazole_prix" to 2.319, "gazole_maj" to "2026-10-07T08:54:39+00:00",
        "sp95_prix" to null, "sp95_maj" to null,
        "e85_prix" to null, "e85_maj" to null,
        "gplc_prix" to 1.185, "gplc_maj" to "2026-10-07T08:55:39+00:00",
        "e10_prix" to 2.149, "e10_maj" to "2026-10-07T08:54:39+00:00",
        "sp98_prix" to 2.298, "sp98_maj" to "2026-09-30T15:04:41+00:00",
        "horaires_automate_24_24" to "Oui",
    )

    private val realStationWithE85: RawRecord = mapOf(
        "id" to 89100001, "latitude" to "4818300", "longitude" to "330900",
        "cp" to "89100", "adresse" to "84 ROUTE DE MAILLOT", "ville" to "Sens",
        "geom" to mapOf("lon" to 3.309, "lat" to 48.183),
        "gazole_prix" to 2.389, "gazole_maj" to "2026-10-06T14:40:28+00:00",
        "sp95_prix" to null, "sp95_maj" to null,
        "e85_prix" to 0.909, "e85_maj" to "2026-10-05T12:16:34+00:00",
        "gplc_prix" to null, "gplc_maj" to null,
        "e10_prix" to 2.209, "e10_maj" to "2026-09-28T11:45:27+00:00",
        "sp98_prix" to 2.289, "sp98_maj" to "2026-09-28T11:42:00+00:00",
        "horaires_automate_24_24" to "Non",
    )

    @Test
    fun mapsRealRecordsFromTheOfficialFeed() {
        val saintSever = assertNotNull(StationMapper.map(realStationWithLpg))
        assertEquals(40500002L, saintSever.id)
        assertEquals("Saint-Sever", saintSever.city)
        assertEquals("40500", saintSever.postalCode)
        assertEquals(43.773, saintSever.latitude)
        assertEquals(-0.567, saintSever.longitude)
        assertTrue(saintSever.isOpen24h)
        // Only the four fuels with a price are present; null prices (SP95, E85) are absent.
        assertEquals(setOf(FuelType.DIESEL, FuelType.LPG, FuelType.E10, FuelType.SP98), saintSever.prices.keys)
        assertEquals(2.319, saintSever.prices.getValue(FuelType.DIESEL).euroPerLitre)
        assertEquals(1791363279000L, saintSever.prices.getValue(FuelType.DIESEL).updatedAtEpochMillis)

        val sens = assertNotNull(StationMapper.map(realStationWithE85))
        assertFalse(sens.isOpen24h)
        assertEquals(setOf(FuelType.DIESEL, FuelType.E85, FuelType.E10, FuelType.SP98), sens.prices.keys)
        assertEquals(0.909, sens.prices.getValue(FuelType.E85).euroPerLitre)
    }

    @Test
    fun realStringCoordinatesInMicroDegreesGiveTheSamePositionAsGeom() {
        // Same two stations, but without `geom`: the fallback must reproduce the geom values,
        // including a NEGATIVE longitude (west of Greenwich).
        val withoutGeom = realStationWithLpg - "geom"
        val station = assertNotNull(StationMapper.map(withoutGeom))
        assertEquals(43.773, station.latitude, 1e-9)
        assertEquals(-0.567, station.longitude, 1e-9)
    }

    // ---- Real records with shortages, copied from the official feed on 2026-10-08 ----

    /** Every fuel is out of stock (4 temporarily, 2 for good) and no price is published at all. */
    private val realStationAllFuelsOut: RawRecord = mapOf(
        "id" to 58240003, "latitude" to "4675351.71497", "longitude" to "314069.121289",
        "cp" to "58240", "adresse" to "ROUTE NATIONALE 7 - LES BOULAISES", "ville" to "Chantenay-Saint-Imbert",
        "geom" to mapOf("lon" to 3.1406912128899998, "lat" to 46.7535171497),
        "gazole_prix" to null, "gazole_maj" to null, "e10_prix" to null, "sp98_prix" to null,
        "gazole_rupture_type" to "temporaire", "e85_rupture_type" to "temporaire",
        "e10_rupture_type" to "temporaire", "sp98_rupture_type" to "temporaire",
        "sp95_rupture_type" to "definitive", "gplc_rupture_type" to "definitive",
        "gazole_rupture_debut" to "2026-09-03T20:51:41+00:00", "e85_rupture_debut" to "2026-09-03T20:51:41+00:00",
        "e10_rupture_debut" to "2026-09-03T20:51:41+00:00", "sp98_rupture_debut" to "2026-09-03T20:51:41+00:00",
        "carburants_rupture_temporaire" to "Gazole;E85;E10;SP98",
        "horaires_automate_24_24" to "Non",
    )

    /** Two fresh prices, SP98 temporarily out (its price is null), SP95/E85/GPLc gone for good. */
    private val realStationOneFuelOut: RawRecord = mapOf(
        "id" to 60350003, "latitude" to "4940400", "longitude" to "300800",
        "cp" to "60350", "adresse" to "ROUTE DE SOISSONS- RN 31", "ville" to "Cuise-la-Motte",
        "geom" to mapOf("lon" to 3.008, "lat" to 49.404),
        "gazole_prix" to 2.25, "gazole_maj" to "2026-10-08T00:01:00+00:00",
        "e10_prix" to 1.99, "e10_maj" to "2026-10-08T00:01:00+00:00",
        "sp98_prix" to null, "sp98_maj" to null, "sp98_rupture_type" to "temporaire",
        "sp95_rupture_type" to "definitive", "e85_rupture_type" to "definitive", "gplc_rupture_type" to "definitive",
        "sp98_rupture_debut" to "2026-10-05T18:04:28+00:00",
        "carburants_rupture_temporaire" to "SP98",
        "horaires_automate_24_24" to "Oui",
    )

    /** Its SP95 has been "temporarily" out since June 2025: over a year. */
    private val realStationOldShortage: RawRecord = mapOf(
        "id" to 38570003, "latitude" to "4538600", "longitude" to "599800",
        "cp" to "38570", "adresse" to "LIEU DIT LE ROMPAY RD523", "ville" to "Le Cheylas",
        "geom" to mapOf("lon" to 5.998, "lat" to 45.386),
        "gazole_prix" to 2.397, "gazole_maj" to "2026-10-05T17:08:45+00:00",
        "e10_prix" to 2.228, "e10_maj" to "2026-10-05T17:08:45+00:00",
        "sp98_prix" to 2.276, "sp98_maj" to "2026-09-25T09:32:08+00:00",
        "sp95_prix" to null, "sp95_rupture_type" to "temporaire", "sp95_rupture_debut" to "2025-06-06T12:04:17+00:00",
        "e85_rupture_type" to "definitive", "gplc_rupture_type" to "definitive",
        "carburants_rupture_temporaire" to "SP95",
        "horaires_automate_24_24" to "Oui",
    )

    @Test
    fun priceIsAbsentDuringATemporaryShortage() {
        // Observed in the real feed: a fuel in shortage has NO price (null), so the station
        // must be drawn from the shortage information alone.
        val station = assertNotNull(StationMapper.map(realStationOneFuelOut))
        assertEquals(setOf(FuelType.DIESEL, FuelType.E10), station.prices.keys)
        assertEquals(setOf(FuelType.SP98), station.temporaryShortages.keys)
    }

    @Test
    fun stationWithoutAnyPriceButWithShortagesIsKept() {
        val station = assertNotNull(StationMapper.map(realStationAllFuelsOut))
        assertTrue(station.prices.isEmpty())
        assertEquals(setOf(FuelType.DIESEL, FuelType.E85, FuelType.E10, FuelType.SP98), station.temporaryShortages.keys)
        assertEquals(46.7535171497, station.latitude)
    }

    @Test
    fun definitiveShortagesAreNotReported() {
        // SP95 and GPLc are "definitive" at the first station: simply no longer sold.
        val station = assertNotNull(StationMapper.map(realStationAllFuelsOut))
        assertFalse(FuelType.SP95 in station.temporaryShortages)
        assertFalse(FuelType.LPG in station.temporaryShortages)
    }

    @Test
    fun decimalMicroDegreeStringsAreRescaled() {
        // Some stations have fractional micro-degrees: "4675351.71497" is 46.7535171497°.
        val station = assertNotNull(StationMapper.map(realStationAllFuelsOut - "geom"))
        assertEquals(46.7535171497, station.latitude, 1e-9)
        assertEquals(3.14069121289, station.longitude, 1e-9)
    }

    @Test
    fun readsTemporaryShortageFromPerFuelType() {
        val station = assertNotNull(StationMapper.map(record("e10_rupture_type" to "temporaire", "gazole_rupture_type" to null)))
        assertEquals(setOf(FuelType.E10), station.temporaryShortages.keys)
    }

    @Test
    fun readsTemporaryShortagesFromSummaryList() {
        // The real feed uses a ";"-separated text, but a list is accepted too in case it changes.
        val station = assertNotNull(StationMapper.map(record("carburants_rupture_temporaire" to listOf("SP98", "E85"))))
        assertEquals(setOf(FuelType.SP98, FuelType.E85), station.temporaryShortages.keys)
    }

    @Test
    fun unknownFuelLabelsInShortageListAreIgnored() {
        val station = assertNotNull(StationMapper.map(record("carburants_rupture_temporaire" to "Hydrogène;E10")))
        assertEquals(setOf(FuelType.E10), station.temporaryShortages.keys)
    }

    @Test
    fun requestedColumnsCoverEverythingTheMapperReads() {
        val columns = StationMapper.requestedColumns
        assertEquals(columns.size, columns.toSet().size) // no duplicates
        for (expected in listOf("id", "geom", "gazole_prix", "e10_maj", "sp98_rupture_type", "carburants_rupture_temporaire")) {
            assertTrue(expected in columns, "missing column $expected")
        }
    }

    @Test
    fun readsTheStartDateOfEachShortage() {
        val oneOut = assertNotNull(StationMapper.map(realStationOneFuelOut))
        assertEquals(1791223468000L, oneOut.temporaryShortages.getValue(FuelType.SP98))

        val allOut = assertNotNull(StationMapper.map(realStationAllFuelsOut))
        assertEquals(1788468701000L, allOut.temporaryShortages.getValue(FuelType.DIESEL))

        val old = assertNotNull(StationMapper.map(realStationOldShortage))
        assertEquals(1749211457000L, old.temporaryShortages.getValue(FuelType.SP95))
    }

    @Test
    fun shortageWithoutStartDateKeepsANullDate() {
        val station = assertNotNull(StationMapper.map(record("carburants_rupture_temporaire" to "E10")))
        assertTrue(FuelType.E10 in station.temporaryShortages)
        assertNull(station.temporaryShortages.getValue(FuelType.E10))
    }
}
