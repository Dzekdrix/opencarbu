package fr.opencarbu.domain

import fr.opencarbu.domain.model.StationIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class StationIdentityTest {
    @Test
    fun brandThenName() =
        assertEquals(listOf("TotalEnergies", "Relais du Pont"), StationIdentity("TotalEnergies", "Relais du Pont").lines)

    @Test
    fun sameBrandAndNameAreShownOnce() =
        assertEquals(listOf("Carrefour"), StationIdentity("Carrefour", "carrefour").lines)

    @Test
    fun nameContainingTheBrandReplacesIt() =
        assertEquals(listOf("Carrefour Market Aix"), StationIdentity("Carrefour", "Carrefour Market Aix").lines)

    @Test
    fun missingPartsAreSkipped() {
        assertEquals(listOf("Avia"), StationIdentity("Avia", null).lines)
        assertEquals(listOf("Relais"), StationIdentity(null, " Relais ").lines)
        assertEquals(emptyList(), StationIdentity(" ", null).lines)
    }

    @Test
    fun mergeKeepsFirstValueAndFillsGaps() {
        val merged = StationIdentity("Avia", null, null, "80000", null)
            .mergedWith(StationIdentity("Esso", "Relais", "Rue X", "75000", "Paris"))
        assertEquals(StationIdentity("Avia", "Relais", "Rue X", "80000", "Paris"), merged)
        assertEquals(true, StationIdentity(null, null).isEmpty)
    }
}
