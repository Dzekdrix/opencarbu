package fr.opencarbu.domain.search

/**
 * Tells whether a search text is a station identifier, as the price feed gives them ("1120005":
 * the postal code, without a leading zero, followed by three digits). A text of only digits, six to
 * nine of them, is taken for one; shorter numbers (a postal code, a house number) stay address
 * searches. A leading zero is accepted ("01120005").
 */
object StationIdQuery {
    private const val MIN_DIGITS = 6
    private const val MAX_DIGITS = 9

    /** The identifier written in [text], or null if the text is not one. */
    fun parse(text: String): Long? {
        val digits = text.trim()
        if (digits.length !in MIN_DIGITS..MAX_DIGITS || !digits.all { it in '0'..'9' }) return null
        return digits.toLong()
    }
}
