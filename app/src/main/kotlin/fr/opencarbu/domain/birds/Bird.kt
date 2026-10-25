package fr.opencarbu.domain.birds

/**
 * A bird species, used for the playful "X km on the back of a <bird>" distance on the station
 * sheet. Every bird has a French name; the other fields may be missing.
 *
 * @property frenchTitle title of the French Wikipedia article, null if there is none.
 * @property englishTitle title of the English Wikipedia article, null if there is none.
 * @property massKg see [BirdFlight].
 */
data class Bird(
    val frenchName: String,
    val englishName: String? = null,
    val scientificName: String? = null,
    val frenchTitle: String? = null,
    val englishTitle: String? = null,
    /** Body mass in kilograms when Wikidata knows it; used to estimate the bird's flight speed. */
    val massKg: Double? = null,
)

/**
 * A bird ready to be written in a sentence: [article] ("de ", "d’", "a ", "an ") comes first,
 * then [name], which is the part to turn into a link to [url] (null = no Wikipedia page, plain text).
 */
data class BirdPhrase(
    val article: String,
    val name: String,
    val url: String?,
)
