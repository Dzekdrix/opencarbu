package fr.opencarbu.domain.birds

import java.io.BufferedReader

/**
 * Reads the bird list shipped with the app (`assets/birds.tsv`): one bird per line, up to six
 * columns separated by tabs: French name, English name, scientific name, French Wikipedia
 * title, English Wikipedia title, body mass in grams. Empty or missing column = unknown. Lines starting with `#` are comments.
 * A line without a French name is ignored.
 *
 * The file is produced by `tools/birds/build_birds.py` from Wikidata.
 */
object BirdListParser {

    fun parse(reader: BufferedReader): List<Bird> {
        val birds = ArrayList<Bird>()
        reader.forEachLine { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachLine
            val columns = line.split('\t')
            fun column(index: Int): String? = columns.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
            val french = column(0) ?: return@forEachLine
            // Sixth column (optional): body mass in grams.
            val massKg = column(5)?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }?.div(1000.0)
            birds += Bird(french, column(1), column(2), column(3), column(4), massKg)
        }
        return birds
    }

    /** Shown if the list could not be read, so that the sentence never breaks. */
    val FALLBACK = Bird(
        frenchName = "Calopsitte élégante",
        englishName = "Cockatiel",
        scientificName = "Nymphicus hollandicus",
        frenchTitle = "Calopsitte élégante",
        englishTitle = "Cockatiel",
    )
}
