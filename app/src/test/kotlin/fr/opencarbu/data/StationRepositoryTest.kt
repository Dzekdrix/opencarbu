package fr.opencarbu.data

import fr.opencarbu.domain.model.Station
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationRepositoryTest {

    /** Pretends to download: writes [content] (one line = one station) or fails. */
    private class FakeDownloader(var content: String = "a\nb\nc", var failure: IOException? = null) : FeedDownloader {
        var calls = 0
        override fun downloadTo(target: File) {
            calls++
            failure?.let { throw it }
            target.writeText(content)
        }
    }

    /** Pretends to parse: one non-blank line = one station; "garbage" is rejected. */
    private object FakeParser : FeedParser {
        override fun parse(file: File): ParsedFeed {
            val text = file.readText()
            if (text.startsWith("garbage")) throw IOException("not a feed")
            val lines = text.lines().filter { it.isNotBlank() }
            val stations = lines.indices.map { Station(it.toLong(), 0.0, 0.0, "", "", "", emptyMap(), false) }
            return ParsedFeed(stations, totalRecords = lines.size + 1) // +1: one unusable record
        }
    }

    private val directory: File = Files.createTempDirectory("opencarbu-test").toFile()
    private val cacheFile = File(directory, "feed.json")
    private val downloader = FakeDownloader()
    private var now = 1_000_000_000L
    private val repository = StationRepository(cacheFile, downloader, FakeParser, clock = { now })

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun noCacheMeansNothingToLoad() {
        assertNull(repository.loadCache())
        assertFalse(repository.isCacheFresh())
    }

    @Test
    fun refreshDownloadsParsesAndCaches() {
        val snapshot = repository.refresh()
        assertEquals(3, snapshot.stations.size)
        assertEquals(now, snapshot.fetchedAtMillis)
        assertEquals(1, snapshot.skippedRecords)
        assertTrue(cacheFile.isFile)
        assertFalse(File(cacheFile.path + ".download").exists()) // no leftover temporary file
    }

    @Test
    fun cachedDataCanBeReloadedWithItsDownloadDate() {
        repository.refresh()
        val reloaded = assertNotNull(repository.loadCache())
        assertEquals(3, reloaded.stations.size)
        assertEquals(now, reloaded.fetchedAtMillis)
    }

    @Test
    fun cacheIsFreshOnlyForAShortTime() {
        repository.refresh()
        now += StationRepository.DEFAULT_MAX_CACHE_AGE_MILLIS - 1
        assertTrue(repository.isCacheFresh())
        now += 1
        assertFalse(repository.isCacheFresh())
    }

    @Test
    fun failedDownloadKeepsThePreviousCache() {
        repository.refresh()
        downloader.failure = IOException("no network")
        assertFailsWith<IOException> { repository.refresh() }
        assertEquals(3, assertNotNull(repository.loadCache()).stations.size)
    }

    @Test
    fun unparseableDownloadDoesNotReplaceTheCache() {
        repository.refresh()
        downloader.content = "garbage (an HTML error page, say)"
        assertFailsWith<IOException> { repository.refresh() }
        assertEquals(3, assertNotNull(repository.loadCache()).stations.size)
        assertFalse(File(cacheFile.path + ".download").exists())
    }

    @Test
    fun feedWithoutAnyUsableStationIsRejected() {
        downloader.content = ""
        assertFailsWith<IOException> { repository.refresh() }
        assertFalse(cacheFile.exists())
    }

    @Test
    fun corruptCacheIsDiscardedInsteadOfCrashing() {
        cacheFile.writeText("garbage")
        assertNull(repository.loadCache())
        assertFalse(cacheFile.exists())
    }
}
