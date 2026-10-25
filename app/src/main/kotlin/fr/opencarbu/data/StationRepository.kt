package fr.opencarbu.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Single entry point to the station data: a download of the whole country, kept as one file
 * on disk so the app opens instantly (and works offline) with the last known prices.
 *
 * Why a plain file and not a database?
 * The app always needs all ~10 000 stations in memory to colour the map, never a subset,
 * so a database would add a heavy dependency (and build step) without any benefit.
 * Parsing the cached file takes a fraction of a second.
 *
 * This class is deliberately blocking and Android-free: callers run it on a background
 * thread (the ViewModel does), and it can be unit-tested on a laptop.
 *
 * @param cacheFile where the last good download is kept.
 * @param clock current time in epoch millis (injected for tests).
 * @param maxCacheAgeMillis age after which a refresh is worthwhile; the source is updated
 *   every ~10 minutes, so asking more often would be wasteful.
 */
class StationRepository(
    private val cacheFile: File,
    private val downloader: FeedDownloader,
    private val parser: FeedParser,
    private val clock: () -> Long,
    private val maxCacheAgeMillis: Long = DEFAULT_MAX_CACHE_AGE_MILLIS,
) {
    private val downloadFile = File(cacheFile.path + ".download")

    /**
     * Reads the cached feed, or returns null if there is none or it is unreadable.
     * A corrupt cache is deleted: it must never prevent the app from starting.
     */
    fun loadCache(): FeedSnapshot? {
        if (!cacheFile.isFile) return null
        return try {
            snapshotOf(parser.parse(cacheFile), cacheFile.lastModified())
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Any failure (I/O, malformed JSON, unexpected structure) means "no usable cache".
            cacheFile.delete()
            null
        }
    }

    /** True if the cache exists and is recent enough that downloading again is pointless. */
    fun isCacheFresh(): Boolean =
        cacheFile.isFile && clock() - cacheFile.lastModified() < maxCacheAgeMillis

    /**
     * Downloads the feed, checks it is usable, and only then replaces the cache.
     * On any failure the previous cache is left untouched and the exception is rethrown.
     */
    fun refresh(): FeedSnapshot {
        cacheFile.absoluteFile.parentFile?.mkdirs()
        try {
            downloader.downloadTo(downloadFile)
            // Parse BEFORE replacing the cache: an error page or truncated file must not
            // overwrite good data.
            val parsed = parser.parse(downloadFile)
            if (parsed.stations.isEmpty()) throw IOException("The downloaded feed contains no usable station")

            Files.move(downloadFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            val fetchedAt = clock()
            cacheFile.setLastModified(fetchedAt) // freshness is derived from the file date
            return snapshotOf(parsed, fetchedAt)
        } finally {
            downloadFile.delete() // no-op after a successful move
        }
    }

    private fun snapshotOf(parsed: ParsedFeed, fetchedAtMillis: Long) =
        FeedSnapshot(parsed.stations, fetchedAtMillis, parsed.totalRecords)

    companion object {
        /** 15 minutes: slightly above the source's 10-minute refresh period. */
        const val DEFAULT_MAX_CACHE_AGE_MILLIS: Long = 15L * 60 * 1000
    }
}
