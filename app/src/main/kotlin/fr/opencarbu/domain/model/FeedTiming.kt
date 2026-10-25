package fr.opencarbu.domain.model

/**
 * How long the last successful download of a feed took, in milliseconds.
 * @property responseMillis from sending the request to receiving the server's answer (its headers): the
 *   server's own response time, which includes the time it takes to build the export.
 * @property totalMillis from sending the request to the last byte written to disk.
 */
data class FeedTiming(val responseMillis: Long, val totalMillis: Long) {
    companion object {
        /** The saved values, or null if one is missing or they make no sense (response longer than the whole). */
        fun fromStored(responseMillis: Long?, totalMillis: Long?): FeedTiming? {
            if (responseMillis == null || totalMillis == null) return null
            if (responseMillis < 0 || totalMillis < responseMillis) return null
            return FeedTiming(responseMillis, totalMillis)
        }
    }
}
