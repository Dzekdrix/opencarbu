package fr.opencarbu.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StationIdQueryTest {
    @Test
    fun sevenAndEightDigitIdsAreRecognised() {
        assertEquals(1120005L, StationIdQuery.parse("1120005"))
        assertEquals(59000011L, StationIdQuery.parse(" 59000011 "))
    }

    @Test
    fun leadingZeroIsAccepted() {
        assertEquals(1120005L, StationIdQuery.parse("01120005"))
    }

    @Test
    fun postalCodesAndAddressesAreNot() {
        assertNull(StationIdQuery.parse("80000"))
        assertNull(StationIdQuery.parse("8 boulevard du Port"))
        assertNull(StationIdQuery.parse("11200a5"))
        assertNull(StationIdQuery.parse("12345678901"))
        assertNull(StationIdQuery.parse(""))
    }
}
