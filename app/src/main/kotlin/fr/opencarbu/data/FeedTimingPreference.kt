package fr.opencarbu.data

import android.content.SharedPreferences
import fr.opencarbu.domain.model.FeedTiming

/** The feeds whose last download time is kept (Info page, "Performance" section). */
enum class TimedFeed(val keyPrefix: String) {
    PRICES("timing_prices"),
    NAMES("timing_names"),
    ROUTING("timing_routing"),
}

/** How the last download time of each feed is written to, and read back from, the saved settings. */
object FeedTimingPreference {
    private const val RESPONSE = "_response_ms"
    private const val TOTAL = "_total_ms"

    fun read(prefs: SharedPreferences, feed: TimedFeed): FeedTiming? = FeedTiming.fromStored(
        responseMillis = if (prefs.contains(feed.keyPrefix + RESPONSE)) prefs.getLong(feed.keyPrefix + RESPONSE, 0L) else null,
        totalMillis = if (prefs.contains(feed.keyPrefix + TOTAL)) prefs.getLong(feed.keyPrefix + TOTAL, 0L) else null,
    )

    fun write(prefs: SharedPreferences, feed: TimedFeed, timing: FeedTiming) {
        prefs.edit()
            .putLong(feed.keyPrefix + RESPONSE, timing.responseMillis)
            .putLong(feed.keyPrefix + TOTAL, timing.totalMillis)
            .apply()
    }
}
