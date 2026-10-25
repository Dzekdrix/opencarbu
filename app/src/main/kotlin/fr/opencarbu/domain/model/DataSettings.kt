package fr.opencarbu.domain.model

/**
 * How the app keeps its station data up to date.
 *
 * @property refreshIntervalMinutes when the app is opened, the prices are downloaded again only
 *   if the data on the phone is older than this many minutes. The source itself is updated about
 *   every 10 minutes, so asking more often is pointless; asking less often saves mobile data.
 * @property namesRefreshIntervalDays the same for the reference file of station names and brands,
 *   which changes far less often than prices.
 * @property showReferenceOnlyStations test option: also draw the stations that the reference file
 *   lists (with a position) but the price feed does not, as violet "?" dots without prices.
 */
data class DataSettings(
    val refreshIntervalMinutes: Int = DEFAULT_REFRESH_INTERVAL_MINUTES,
    val namesRefreshIntervalDays: Int = DEFAULT_NAMES_REFRESH_INTERVAL_DAYS,
    val showReferenceOnlyStations: Boolean = DEFAULT_SHOW_REFERENCE_ONLY_STATIONS,
) {
    init {
        require(refreshIntervalMinutes in MIN_REFRESH_INTERVAL_MINUTES..MAX_REFRESH_INTERVAL_MINUTES) {
            "Interval must be between $MIN_REFRESH_INTERVAL_MINUTES and $MAX_REFRESH_INTERVAL_MINUTES minutes"
        }
        require(namesRefreshIntervalDays in MIN_NAMES_REFRESH_INTERVAL_DAYS..MAX_NAMES_REFRESH_INTERVAL_DAYS) {
            "Names interval must be between $MIN_NAMES_REFRESH_INTERVAL_DAYS and $MAX_NAMES_REFRESH_INTERVAL_DAYS days"
        }
    }

    /**
     * True if the data downloaded at [fetchedAtMillis] (null = no data yet) is old enough to be
     * downloaded again at [nowEpochMillis]. A date in the future (the phone's clock was changed)
     * counts as stale rather than keeping old data forever.
     */
    fun isStale(fetchedAtMillis: Long?, nowEpochMillis: Long): Boolean {
        if (fetchedAtMillis == null) return true
        val age = nowEpochMillis - fetchedAtMillis
        return age < 0 || age >= refreshIntervalMinutes * MILLIS_PER_MINUTE
    }

    /** True if the names file saved at [fetchedAtMillis] (null = none) must be downloaded again. */
    fun isNamesStale(fetchedAtMillis: Long?, nowEpochMillis: Long): Boolean {
        if (fetchedAtMillis == null) return true
        val age = nowEpochMillis - fetchedAtMillis
        return age < 0 || age >= namesRefreshIntervalDays * MILLIS_PER_DAY
    }

    companion object {
        /** Off: this comparison is a test aid that users can switch on in the settings. */
        const val DEFAULT_SHOW_REFERENCE_ONLY_STATIONS = false

        const val DEFAULT_NAMES_REFRESH_INTERVAL_DAYS = 30
        const val MIN_NAMES_REFRESH_INTERVAL_DAYS = 1
        const val MAX_NAMES_REFRESH_INTERVAL_DAYS = 365

        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

        /** The values the settings slider offers for the names file, in days. */
        val CHOICES_NAMES_DAYS: List<Int> = listOf(1, 2, 3, 7, 14, 30, 60, 90, 180, 365)

        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 480
        const val MIN_REFRESH_INTERVAL_MINUTES = 1
        const val MAX_REFRESH_INTERVAL_MINUTES = 48 * 60

        private const val MILLIS_PER_MINUTE = 60_000L

        /** The values the settings slider offers, from 1 minute to 48 hours. */
        val CHOICES_MINUTES: List<Int> = listOf(
            1, 2, 5, 10, 15, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1080, 1440, 2160, 2880,
        )
    }
}
