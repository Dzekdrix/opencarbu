package fr.opencarbu.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationNamesRepositoryTest {
    private class FakeDownloader(var content: String, var failure: IOException? = null) : FeedDownloader {
        override fun downloadTo(target: File) {
            failure?.let { throw it }
            target.writeText(content)
        }
    }

    private val good = "ref:FR:prix-carburants,brand,name\n123,Avia,Avia\n"
    private val directory: File = Files.createTempDirectory("opencarbu-names").toFile()
    private val cacheFile = File(directory, "names.csv")
    private var now = 1_000_000_000L

    private fun repo(d: FeedDownloader) = StationNamesRepository(cacheFile, d, { now })

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun refreshSavesAndLoadsBack() {
        val r = repo(FakeDownloader(good))
        assertNull(r.loadCache())
        assertEquals(setOf(123L), r.refresh().keys)
        assertEquals(setOf(123L), r.loadCache()?.keys)
    }

    @Test
    fun downloadDateIsTheSaveTime() {
        val r = repo(FakeDownloader(good))
        assertNull(r.cacheDownloadedAt())
        r.refresh()
        assertEquals(now, r.cacheDownloadedAt())
    }

    @Test
    fun badDownloadNeverReplacesGoodCache() {
        repo(FakeDownloader(good)).refresh()
        assertFailsWith<IOException> { repo(FakeDownloader("<html>oops</html>")).refresh() }
        assertFailsWith<IOException> { repo(FakeDownloader(good, IOException("offline"))).refresh() }
        assertEquals(setOf(123L), repo(FakeDownloader(good)).loadCache()?.keys)
        assertFalse(File(cacheFile.path + ".download").exists())
    }

    @Test
    fun corruptCacheIsDeleted() {
        cacheFile.writeText("garbage")
        assertNull(repo(FakeDownloader(good)).loadCache())
        assertFalse(cacheFile.exists())
    }

    @Test
    fun utf16FileIsRead() {
        val downloader = object : FeedDownloader {
            override fun downloadTo(target: File) {
                target.writeBytes(good.toByteArray(Charsets.UTF_16)) // writes a byte order mark
            }
        }
        assertEquals(setOf(123L), repo(downloader).refresh().keys)
    }

    @Test
    fun bzip2CompressedFileIsRead() {
        val downloader = object : FeedDownloader {
            override fun downloadTo(target: File) = target.writeBytes(Bz2Fixtures.NAMES_CSV_BZ2)
        }
        val repository = repo(downloader)
        val names = repository.refresh()
        assertEquals("Carrefour", names[65310001L]?.brand)
        assertEquals("Relais Total De Tout Vent", names[36000016L]?.name)
        assertEquals("TotalEnergies", names[36000016L]?.brand)
        // The saved copy (still compressed) loads back too.
        assertEquals(names, repository.loadCache())
    }

    @Test
    fun gzipCompressedFileIsRead() {
        val downloader = object : FeedDownloader {
            override fun downloadTo(target: File) {
                java.util.zip.GZIPOutputStream(target.outputStream()).use { it.write(good.toByteArray()) }
            }
        }
        assertEquals(setOf(123L), repo(downloader).refresh().keys)
    }
}
