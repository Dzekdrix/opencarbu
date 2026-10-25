package fr.opencarbu.domain.birds

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BirdListParserTest {

    private fun parse(text: String) = BirdListParser.parse(text.reader().buffered())

    @Test
    fun readsFiveColumnsAndSkipsCommentsAndBlankLines() {
        val birds = parse(
            "# fr\ten\n\n" +
                "Aigle royal\tGolden eagle\tAquila chrysaetos\tAigle royal\tGolden eagle\n" +
                "Ombrette africaine\tHamerkop\tScopus umbretta\t\t\n",
        )
        assertEquals(2, birds.size)
        assertEquals(Bird("Aigle royal", "Golden eagle", "Aquila chrysaetos", "Aigle royal", "Golden eagle"), birds[0])
        assertEquals(Bird("Ombrette africaine", "Hamerkop", "Scopus umbretta", null, null), birds[1])
    }

    @Test
    fun sixthColumnIsTheMassInGrams() {
        val birds = parse("Colibri\tHummingbird\tTrochilidae\t\t\t3.5\nCygne\tSwan\tCygnus\t\t\t10000\nX\t\t\t\t\tabc\nY\t\t\t\t\t-4\n")
        assertEquals(0.0035, birds[0].massKg!!, 1e-12)
        assertEquals(10.0, birds[1].massKg!!, 1e-12)
        assertNull(birds[2].massKg)
        assertNull(birds[3].massKg)
    }

    @Test
    fun shortLinesAreAcceptedAndLinesWithoutFrenchNameIgnored() {
        val birds = parse("Kakapo\n\tCockatiel\tNymphicus\nPie bavarde\tMagpie\n")
        assertEquals(listOf(Bird("Kakapo"), Bird("Pie bavarde", "Magpie")), birds)
        assertNull(birds[0].englishName)
    }

    @Test
    fun theShippedListIsValidAndHasNoDuplicates() {
        val file = listOf("app/src/main/assets/birds.tsv", "src/main/assets/birds.tsv", "../app/src/main/assets/birds.tsv")
            .map(::File).firstOrNull { it.isFile } ?: return // not found from this working directory: nothing to check
        val birds = file.bufferedReader(Charsets.UTF_8).use(BirdListParser::parse)
        assertTrue(birds.size >= 50, "the list should not be tiny")
        assertEquals(birds.size, birds.map { it.frenchName }.toSet().size, "French names must be unique")
        for (bird in birds) assertTrue(bird.frenchName == bird.frenchName.trim() && !bird.frenchName.contains('\t'))
    }
}
