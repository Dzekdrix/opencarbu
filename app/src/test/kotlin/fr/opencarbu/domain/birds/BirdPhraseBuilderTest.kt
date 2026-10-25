package fr.opencarbu.domain.birds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BirdPhraseBuilderTest {

    private fun fr(name: String, frTitle: String? = null, enTitle: String? = null) =
        BirdPhraseBuilder.build(Bird(name, frenchTitle = frTitle, englishTitle = enTitle), "fr")

    private fun en(name: String) = BirdPhraseBuilder.build(Bird("x", englishName = name), "en")

    @Test
    fun frenchUsesDeBeforeAConsonantAndDApostropheBeforeAVowel() {
        assertEquals("de ", fr("Calopsitte élégante").article)
        assertEquals("calopsitte élégante", fr("Calopsitte élégante").name)
        assertEquals("d’", fr("Aigle royal").article)
        assertEquals("d’", fr("Érismature rousse").article)
        assertEquals("d’", fr("Œdicnème criard").article)
        assertEquals("œdicnème criard", fr("Œdicnème criard").name)
        assertEquals("d’", fr("Ibis sacré").article)
        assertEquals("d’", fr("Yeuse").article)
    }

    @Test
    fun frenchSilentAndAspiratedH() {
        assertEquals("d’", fr("Huîtrier pie").article)
        assertEquals("d’", fr("Hirondelle rustique").article)
        assertEquals("de ", fr("Hibou moyen-duc").article)
        assertEquals("de ", fr("Harfang des neiges").article)
        assertEquals("de ", fr("Héron cendré").article)
        assertEquals("de ", fr("Huppe fasciée").article)
    }

    @Test
    fun capitalisedNamesAreLoweredButAcronymsAreKept() {
        assertEquals("grand Corbeau", fr("Grand Corbeau").name)
        assertEquals("ABC oiseau", fr("ABC oiseau").name)
    }

    @Test
    fun englishAAndAn() {
        assertEquals("a ", en("Cockatiel").article)
        assertEquals("a ", en("Golden eagle").article)
        assertEquals("an ", en("Eagle owl").article)
        assertEquals("an ", en("Osprey").article)
        assertEquals("an ", en("Indian peafowl").article)
        assertEquals("a ", en("Eurasian wren").article)
        assertEquals("a ", en("European robin").article)
        assertEquals("an ", en("Emperor penguin").article)
        assertEquals("a ", en("Ural owl").article)
        assertEquals("an ", en("Umbrellabird").article)
        assertEquals("a ", en("Hoopoe").article)
        assertEquals("a ", en("One-wattled bird").article)
    }

    @Test
    fun englishFallsBackToTheScientificNameThenToTheFrenchOne() {
        assertEquals("Passer domesticus", BirdPhraseBuilder.build(Bird("Moineau", scientificName = "Passer domesticus"), "en").name)
        assertEquals("Moineau", BirdPhraseBuilder.build(Bird("Moineau"), "en").name)
        assertEquals("Moineau", BirdPhraseBuilder.build(Bird("Moineau", englishName = "Sparrow"), "fr").name.replaceFirstChar { it.uppercase() })
    }

    @Test
    fun anyOtherLanguageGetsEnglish() {
        assertEquals("Cockatiel", BirdPhraseBuilder.build(Bird("Calopsitte", englishName = "Cockatiel"), "de").name)
    }

    @Test
    fun linkPointsToThePageOfThePhoneLanguageThenToTheOtherOne() {
        assertEquals("https://fr.wikipedia.org/wiki/Aigle_royal", fr("Aigle royal", frTitle = "Aigle royal").url)
        assertEquals("https://en.wikipedia.org/wiki/Golden_eagle", fr("Aigle royal", enTitle = "Golden eagle").url)
        assertNull(fr("Aigle royal").url)
        val both = Bird("Aigle royal", "Golden eagle", frenchTitle = "Aigle royal", englishTitle = "Golden eagle")
        assertEquals("https://en.wikipedia.org/wiki/Golden_eagle", BirdPhraseBuilder.build(both, "en").url)
        assertEquals("https://fr.wikipedia.org/wiki/Aigle_royal", BirdPhraseBuilder.build(Bird("Aigle royal", frenchTitle = "Aigle royal"), "en").url)
    }

    @Test
    fun leadingApostropheIsIgnoredWhenChoosingTheArticle() {
        assertEquals("d’", fr("'Akiapola'au").article)
        assertEquals("an ", en("'Akikiki").article)
    }

    @Test
    fun everyBirdOfTheShippedListMakesAValidPhraseInBothLanguages() {
        val file = listOf("app/src/main/assets/birds.tsv", "src/main/assets/birds.tsv", "../app/src/main/assets/birds.tsv")
            .map { java.io.File(it) }.firstOrNull { it.isFile } ?: return // not found from this working directory
        val birds = file.bufferedReader(Charsets.UTF_8).use(BirdListParser::parse)
        for (bird in birds) {
            for (language in listOf("fr", "en")) {
                val phrase = BirdPhraseBuilder.build(bird, language)
                assert(phrase.name.isNotBlank()) { "empty name for $bird" }
                assert(phrase.article in setOf("de ", "d’", "a ", "an ")) { "bad article for $bird" }
                phrase.url?.let { assert(it.startsWith("https://") && ' ' !in it) { "bad url $it for $bird" } }
            }
        }
    }

    @Test
    fun titlesAreEncodedForAUrl() {
        assertEquals(
            "https://fr.wikipedia.org/wiki/Martin-p%C3%AAcheur_d%27Europe",
            fr("Martin-pêcheur d'Europe", frTitle = "Martin-pêcheur d'Europe").url,
        )
        assertEquals("https://fr.wikipedia.org/wiki/Merle_%28oiseau%29", fr("Merle", frTitle = "Merle (oiseau)").url)
    }
}
