package fr.opencarbu.data

import fr.opencarbu.domain.model.StationIdentity
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationNamesParserTest {

    private fun parse(csv: String) = StationNamesParser.parse(csv.reader().buffered())

    private val header = "osmose,osm_id,osm_type,ref:FR:prix-carburants,amenity,brand,name,source,note"

    @Test
    fun joinsOnThePriceRefAndNotOnTheOsmId() {
        val names = parse(
            "$header\n" +
                "osm+opendata,25214820,N,65310001,fuel,Carrefour,Carrefour,src,Laloubère\n" +
                "osm+opendata,25214191,N,36000016,fuel,TotalEnergies,Relais Total De Tout Vent,src,Châteauroux\n",
        )
        assertEquals(StationIdentity("Carrefour", "Carrefour"), names[65310001L])
        assertEquals(StationIdentity("TotalEnergies", "Relais Total De Tout Vent"), names[36000016L])
        assertNull(names[25214820L])
    }

    @Test
    fun quotedFieldsMayContainCommasQuotesAndLineBreaks() {
        val names = parse(
            "$header\n" +
                "o,1,N,111,fuel,\"Leclerc, Express\",\"Station \"\"du\nport\"\"\",\"Ministère de l'Economie, 2020\",x\n",
        )
        assertEquals(StationIdentity("Leclerc, Express", "Station \"du\nport\""), names[111L])
    }

    @Test
    fun rowsWithoutAnyNameOrWithoutRefAreSkipped() {
        val names = parse(
            "$header\n" +
                "o,1,N,222,fuel,,,src,x\n" +
                "o,2,N,,fuel,Avia,Avia,src,x\n" +
                "o,3,N,abc,fuel,Avia,Avia,src,x\n" +
                "o,4,N,333,fuel,Avia,,src,x\n",
        )
        assertEquals(mapOf(333L to StationIdentity("Avia", null)), names)
    }

    @Test
    fun shortRowsAndWindowsLineBreaksAreTolerated() {
        val names = parse("$header\r\no,1,N,444,fuel,Esso\r\no,2,N,555,fuel,Avia,Avia,src,x\r\n")
        assertEquals(StationIdentity("Esso", null), names[444L])
        assertEquals(StationIdentity("Avia", "Avia"), names[555L])
    }

    @Test
    fun severalIdsInOneCellAreAllRegistered() {
        val names = parse("$header\no,1,N,666;777,fuel,Avia,Avia,src,x\n")
        assertEquals(setOf(666L, 777L), names.keys)
    }

    @Test
    fun duplicateIdKeepsFirstValueOfEachField() {
        val names = parse("$header\no,1,N,888,fuel,Avia,,s,x\no,2,N,888,fuel,Other,Relais,s,x\n")
        assertEquals(StationIdentity("Avia", "Relais"), names[888L])
    }

    @Test
    fun semicolonDelimitedAndBomFilesWork() {
        val names = parse("﻿osm_id;ref:FR:prix-carburants;brand;name\n1;999;Avia;Avia Nord\n")
        assertEquals(StationIdentity("Avia", "Avia Nord"), names[999L])
    }

    @Test
    fun doubleEncodedTextIsRepairedAndCorrectTextIsLeftAlone() {
        val names = parse(
            "$header\n" +
                "o,1,N,101,fuel,Casino,ChÃ¢teau d'eau,s,x\n" + // "Château" encoded twice
                "o,2,N,102,fuel,Casino,Ã‰toile,s,x\n" + // "É" encoded twice (needs windows-1252)
                "o,3,N,103,fuel,Casino,Château,s,x\n",
        )
        assertEquals("Château d'eau", names[101L]?.name)
        assertEquals("Étoile", names[102L]?.name)
        assertEquals("Château", names[103L]?.name)
        assertEquals("Ã seul", StationNamesParser.repairMojibake("Ã seul")) // cannot be repaired: unchanged
    }

    @Test
    fun tabSeparatedAndDifferentCaseHeadersWork() {
        val names = parse("OSM_ID\tRef:FR:Prix-Carburants\tBrand\tName\n1\t321\tAvia\tAvia Sud\n")
        assertEquals(StationIdentity("Avia", "Avia Sud"), names[321L])
    }

    @Test
    fun rejectionMessageQuotesWhatWasReceived() {
        val e = assertFailsWith<IOException> { parse("<!DOCTYPE html><html><body>Not a csv</body></html>\nline2\n") }
        assertTrue("DOCTYPE" in e.message.orEmpty(), e.message)
        val e2 = assertFailsWith<IOException> { parse("a,b,c\n1,2,3\n") }
        assertTrue("3 column(s)" in e2.message.orEmpty() && "a | b | c" in e2.message.orEmpty(), e2.message)
    }

    @Test
    fun aFileWithoutTheExpectedColumnsIsRejected() {
        assertFailsWith<IOException> { parse("a,b,c\n1,2,3\n") }
        assertFailsWith<IOException> { parse("") }
        assertTrue(runCatching { parse("<html>error page</html>") }.isFailure)
    }

    @Test
    fun readsTheAddressAndJoinsHouseNumberAndStreet() {
        val names = parse(
            "ref:FR:prix-carburants,brand,name,addr:housenumber,addr:street,addr:postcode,addr:city\n" +
                "10,Avia,,12,Rue de la Gare,80000,Amiens\n" +
                "11,Esso,,,Route de Paris,75001,Paris\n" +
                "12,,,7,,,\n" +
                "13,,,,Chemin Vert,,\n",
        )
        assertEquals(StationIdentity("Avia", null, "12 Rue de la Gare", "80000", "Amiens"), names[10L])
        assertEquals(StationIdentity("Esso", null, "Route de Paris", "75001", "Paris"), names[11L])
        // A lone house number is useless: the row carries nothing.
        assertNull(names[12L])
        // An address alone is kept, without brand or name.
        assertEquals(StationIdentity(null, null, "Chemin Vert"), names[13L])
    }

    @Test
    fun fileWithOnlyAddressColumnsIsAccepted() {
        val names = parse("ref:FR:prix-carburants,addr:street\n5,Rue X\n")
        assertEquals("Rue X", names[5L]?.street)
    }

    @Test
    fun readsTheOsmPositionAndRejectsImpossibleOnes() {
        val names = parse(
            "osm_id,lon,lat,ref:FR:prix-carburants,brand\n" +
                "1,2.5,48.5,20,Avia\n" +
                "2,,48.5,21,Esso\n" +
                "3,0,0,22,Shell\n" +
                "4,2.5,95,23,Bp\n" +
                "5,abc,48.5,24,Q8\n",
        )
        assertEquals(fr.opencarbu.domain.model.GeoPoint(48.5, 2.5), names[20L]?.position)
        assertNull(names[21L]?.osmObject)
        assertNull(names[21L]?.position)
        assertNull(names[22L]?.position)
        assertNull(names[23L]?.position)
        assertNull(names[24L]?.position)
    }

    @Test
    fun remembersWhichOsmObjectGaveThePosition() {
        val names = parse("osm_id,osm_type,lon,lat,ref:FR:prix-carburants\n2401825325,N,4.4,43.6,30800002\n")
        assertEquals("N2401825325", names[30800002L]?.osmObject)
    }
}
