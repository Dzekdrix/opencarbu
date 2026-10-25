package fr.opencarbu.data

import fr.opencarbu.domain.model.FeedTiming
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads the feed with OkHttp. The response is streamed straight to disk, never held
 * in memory. OkHttp asks the server for gzip and decompresses it transparently.
 *
 * After each successful download [onTimed] gets how long the server took to answer and how long the
 * whole download took (the Info page shows the last ones).
 */
class OkHttpFeedDownloader(
    private val url: String = FeedConfig.url,
    private val client: OkHttpClient = defaultClient(),
    private val onTimed: (FeedTiming) -> Unit = {},
) : FeedDownloader {

    override fun downloadTo(target: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", FeedConfig.USER_AGENT)
            .build()

        val startedAt = System.nanoTime()
        client.newCall(request).execute().use { response ->
            val answeredAt = System.nanoTime()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} ${response.message}")
            }
            val body = response.body ?: throw IOException("Empty response")
            target.sink().buffer().use { sink -> sink.writeAll(body.source()) }
            val doneAt = System.nanoTime()
            onTimed(FeedTiming((answeredAt - startedAt) / NANOS_PER_MILLI, (doneAt - startedAt) / NANOS_PER_MILLI))
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /** Generous read timeout: the server builds the export on the fly. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
