package fr.opencarbu.domain.birds

import java.net.URLEncoder
import java.text.Normalizer

/**
 * Writes a [Bird] the way the phone's language needs it: the right name, the right little word in
 * front of it ("de" or "d’" in French, "a" or "an" in English) and the Wikipedia page in that language.
 *
 * Pure Kotlin, so it is unit-tested on a laptop.
 */
object BirdPhraseBuilder {

    /** @param languageCode the phone's language ("fr", "en"...); French gets French, anything else English. */
    fun build(bird: Bird, languageCode: String): BirdPhrase =
        if (languageCode.equals("fr", ignoreCase = true)) french(bird) else english(bird)

    private fun french(bird: Bird): BirdPhrase {
        val name = lowercaseFirst(bird.frenchName.trim())
        val article = if (startsWithVowelSoundInFrench(name)) "d’" else "de "
        // The French article first; the English one is the second choice.
        val url = wikipediaUrl("fr", bird.frenchTitle) ?: wikipediaUrl("en", bird.englishTitle)
        return BirdPhrase(article, name, url)
    }

    private fun english(bird: Bird): BirdPhrase {
        // No English name: the scientific name is more neutral than a French word in an English sentence.
        val name = (bird.englishName ?: bird.scientificName ?: bird.frenchName).trim()
        val url = wikipediaUrl("en", bird.englishTitle) ?: wikipediaUrl("fr", bird.frenchTitle)
        return BirdPhrase(if (startsWithVowelSoundInEnglish(name)) "an " else "a ", name, url)
    }

    /** `https://fr.wikipedia.org/wiki/Aigle_royal`, or null when the bird has no page in that language. */
    internal fun wikipediaUrl(language: String, title: String?): String? {
        val clean = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Wikipedia writes spaces as underscores; everything else unusual is percent-encoded.
        val encoded = URLEncoder.encode(clean.replace(' ', '_'), "UTF-8").replace("+", "%20")
        return "https://$language.wikipedia.org/wiki/$encoded"
    }

    /** "Calopsitte élégante" -> "calopsitte élégante", but "ABC bird" is left alone. */
    private fun lowercaseFirst(text: String): String {
        if (text.length > 1 && text[0].isUpperCase() && !text[1].isUpperCase()) {
            return text[0].lowercaseChar() + text.substring(1)
        }
        return text
    }

    /** Birds whose name begins with an aspirated "h": no elision ("de hibou", not "d’hibou"). */
    private val ASPIRATED_H = listOf("harfang", "harle", "harpie", "héron", "hibou", "hocco", "hoazin", "hoatzin", "houbara", "huppe", "hoplo")

    private fun startsWithVowelSoundInFrench(name: String): Boolean {
        // Quotes or apostrophes at the very start (Hawaiian names such as 'Oma'o) are not sounds.
        val lower = name.lowercase().dropWhile { !it.isLetter() }
        if (lower.startsWith("œ") || lower.startsWith("æ")) return true
        // Strip accents: "é" -> "e", "î" -> "i"...
        val first = Normalizer.normalize(lower.take(1), Normalizer.Form.NFD).firstOrNull() ?: return false
        return when (first) {
            'a', 'e', 'i', 'o', 'u', 'y' -> true
            'h' -> ASPIRATED_H.none { lower.startsWith(it) }
            else -> false
        }
    }

    private fun startsWithVowelSoundInEnglish(name: String): Boolean {
        val lower = name.lowercase().dropWhile { !it.isLetter() }
        if (lower.isEmpty()) return false
        return when (lower[0]) {
            'a', 'i' -> true
            'o' -> !lower.startsWith("one")
            // "an eagle" but "a European" / "a Eurasian".
            'e' -> !lower.startsWith("eu") && !lower.startsWith("ew")
            // "an umbrella" (u + consonant + consonant) but "a Ural", "a Usambara" (u + consonant + vowel).
            'u' -> lower.length < 3 || !(lower[1] !in "aeiou" && lower[2] in "aeiou")
            else -> false
        }
    }
}
