package fr.opencarbu.data

import fr.opencarbu.domain.model.Station
import java.io.File

/**
 * Downloads the station feed. Kept as an interface so [StationRepository] can be unit-tested
 * without any network (see [OkHttpFeedDownloader] for the real implementation).
 */
interface FeedDownloader {
    /**
     * Writes the complete feed to [target], replacing its content.
     * @throws java.io.IOException if the download fails for any reason.
     */
    fun downloadTo(target: File)
}

/** Result of reading a feed file. */
class ParsedFeed(
    /** Stations with a usable id and position. */
    val stations: List<Station>,
    /** Number of records found in the file, including the ones that could not be used. */
    val totalRecords: Int,
)

/** Reads a feed file into stations. Interface for the same testability reason as [FeedDownloader]. */
interface FeedParser {
    /** @throws Exception if the file is not a valid feed (the caller treats this as "unusable"). */
    fun parse(file: File): ParsedFeed
}

/** A feed read from disk or network, with the metadata needed to display and diagnose it. */
class FeedSnapshot(
    val stations: List<Station>,
    /** When the data was downloaded (epoch millis). */
    val fetchedAtMillis: Long,
    val totalRecords: Int,
) {
    /** Records dropped because they had no usable id or position. */
    val skippedRecords: Int get() = totalRecords - stations.size
}
