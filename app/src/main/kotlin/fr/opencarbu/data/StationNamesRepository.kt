package fr.opencarbu.data

import fr.opencarbu.domain.model.StationIdentity
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Keeps the names-and-brands file on the phone, like [StationRepository] does for prices.
 *
 * Names hardly ever change, so the file is downloaded again only when the copy is older than
 * the user's setting (see `DataSettings.namesRefreshIntervalDays`; 30 days by default). A failure
 * leaves the previous copy in place; the caller decides how to tell the user.
 *
 * Blocking and Android-free: run it on a background thread.
 */
class StationNamesRepository(
    private val cacheFile: File,
    private val downloader: FeedDownloader,
    private val clock: () -> Long,
) {
    private val downloadFile = File(cacheFile.path + ".download")

    /** The saved names, or null if there are none or the file is unreadable (it is then deleted). */
    fun loadCache(): Map<Long, StationIdentity>? {
        if (!cacheFile.isFile) return null
        return try {
            StationNamesParser.parse(cacheFile)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            cacheFile.delete()
            null
        }
    }

    /** When the saved copy was downloaded (epoch millis), or null if there is none. */
    fun cacheDownloadedAt(): Long? = if (cacheFile.isFile) cacheFile.lastModified() else null

    /**
     * Downloads the file, checks it, and only then replaces the saved copy.
     * @throws IOException (or another exception) on failure; the previous copy is kept.
     */
    fun refresh(): Map<Long, StationIdentity> {
        cacheFile.absoluteFile.parentFile?.mkdirs()
        try {
            downloader.downloadTo(downloadFile)
            val parsed = StationNamesParser.parse(downloadFile)
            if (parsed.isEmpty()) throw IOException("The downloaded names file contains no usable entry")
            Files.move(downloadFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            cacheFile.setLastModified(clock())
            return parsed
        } finally {
            downloadFile.delete()
        }
    }
}
