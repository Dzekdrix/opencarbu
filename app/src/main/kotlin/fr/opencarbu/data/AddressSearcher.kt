package fr.opencarbu.data

import fr.opencarbu.domain.search.AddressResponseParser
import fr.opencarbu.domain.search.AddressResult
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Finds places from free text. An interface so the UI logic can be tested without network. */
interface AddressSearcher {
    /**
     * Blocking call: run it off the main thread.
     * @throws IOException if the service cannot be reached or answers with an error.
     */
    fun search(query: String): List<AddressResult>
}

/**
 * Asks the IGN geocoder. Only the typed text is sent (no position, no identifier); the user
 * is told so on the Info page.
 */
class GeoplatformAddressSearcher(
    private val baseUrl: String = FeedConfig.GEOCODER_URL,
    private val client: OkHttpClient = defaultClient(),
) : AddressSearcher {

    override fun search(query: String): List<AddressResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", trimmed)
            .addQueryParameter("limit", MAX_RESULTS.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", FeedConfig.USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} ${response.message}")
            val body = response.body ?: throw IOException("Empty response")
            val root = JsonReader.of(body.source()).readJsonValue()
            return AddressResponseParser.parse(root)
        }
    }

    private companion object {
        const val MAX_RESULTS = 6

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
