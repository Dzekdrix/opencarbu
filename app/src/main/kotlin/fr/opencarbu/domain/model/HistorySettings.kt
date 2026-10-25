package fr.opencarbu.domain.model

/**
 * The optional price history and the optional daily refresh in the background. Both are off by
 * default: nothing is stored and nothing runs until the user switches them on.
 *
 * @property historyEnabled keep on the phone every price change seen in the downloaded prices.
 * @property retentionMonths how long changes are kept, in months; [FOREVER] (0) keeps everything.
 * @property backgroundRefresh let Android wake the app about once a day to download the prices
 *   (and record their changes) even if the app is not opened.
 * @property backgroundWifiOnly the daily download waits for an unmetered network (usually Wi-Fi).
 */
data class HistorySettings(
    val historyEnabled: Boolean = false,
    val retentionMonths: Int = DEFAULT_RETENTION_MONTHS,
    val backgroundRefresh: Boolean = false,
    val backgroundWifiOnly: Boolean = true,
) {
    init {
        require(retentionMonths in CHOICES_RETENTION_MONTHS) { "Retention must be one of $CHOICES_RETENTION_MONTHS months" }
    }

    /** Oldest moment worth keeping at [nowEpochMillis], or null when everything is kept. */
    fun retentionCutoff(nowEpochMillis: Long): Long? =
        if (retentionMonths == FOREVER) null else nowEpochMillis - retentionMonths * MILLIS_PER_MONTH

    companion object {
        const val FOREVER = 0
        const val DEFAULT_RETENTION_MONTHS = 6

        /** What the settings offer: 3 months to 2 years, then "for ever" (0). */
        val CHOICES_RETENTION_MONTHS: List<Int> = listOf(3, 6, 12, 24, FOREVER)

        private const val MILLIS_PER_MONTH = 30L * 24 * 60 * 60 * 1000
    }
}
