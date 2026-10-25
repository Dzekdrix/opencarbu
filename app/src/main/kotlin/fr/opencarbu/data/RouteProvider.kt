package fr.opencarbu.data

import com.squareup.moshi.JsonReader
import fr.opencarbu.domain.model.FeedTiming
import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.routing.OsrmRequest
import fr.opencarbu.domain.routing.OsrmTableParser
import fr.opencarbu.domain.routing.OsrmTableRequest
import fr.opencarbu.domain.routing.OsrmResponseParser
import fr.opencarbu.domain.routing.RouteResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Computes driving routes. An interface so the UI logic does not depend on one server software. */
interface RouteProvider {
    /**
     * Blocking call: run it off the main thread.
     * @throws IOException if the server cannot be reached, answers with an error or finds no route.
     */
    fun route(from: GeoPoint, to: GeoPoint): RouteResult

    /**
     * Distances and times from [origin] to each of [destinations] in one request (at most
     * [OsrmTableRequest.MAX_COORDINATES] - 1 destinations); null where no route exists.
     * @throws IOException if the server cannot be reached or answers with an error.
     */
    fun table(origin: GeoPoint, destinations: List<GeoPoint>): List<RouteResult?>
}

/**
 * Asks an OSRM-compatible server (the public FOSSGIS one by default). After each answer [onTimed] gets how long
 * the server took to answer and how long the whole request took (the Info page shows the last ones).
 *
 * The HTTP client is shared by every instance: it keeps its connection to the server open between requests, so
 * only the first one pays for the DNS lookup and the TLS handshake. (A client made for each request, as before,
 * pays them every time: the nearest-stations list asks for up to hundreds of distances, one request each.)
 */
class OsrmRouteProvider(
    private val baseUrl: String,
    private val client: OkHttpClient = sharedClient,
    private val onTimed: (FeedTiming) -> Unit = {},
) : RouteProvider {

    override fun route(from: GeoPoint, to: GeoPoint): RouteResult {
        val root = fetch(OsrmRequest.url(baseUrl, from, to))
        return OsrmResponseParser.parse(root) ?: throw IOException(OsrmResponseParser.errorCode(root) ?: "No route")
    }

    override fun table(origin: GeoPoint, destinations: List<GeoPoint>): List<RouteResult?> {
        if (destinations.isEmpty()) return emptyList()
        require(destinations.size < OsrmTableRequest.MAX_COORDINATES) { "Too many destinations for one table" }
        val root = fetch(OsrmTableRequest.url(baseUrl, origin, destinations))
        return OsrmTableParser.parse(root, destinations.size)
            ?: throw IOException(OsrmResponseParser.errorCode(root) ?: "Unusable table answer")
    }

    /** GET [url] and read the JSON answer; OSRM puts its error codes in a JSON body, even with HTTP 400. */
    private fun fetch(url: String): Any? {
        val request = Request.Builder().url(url).header("User-Agent", FeedConfig.USER_AGENT).build()
        val startedAt = System.nanoTime()
        client.newCall(request).execute().use { response ->
            val answeredAt = System.nanoTime()
            val body = response.body ?: throw IOException("Empty response")
            val root = try {
                JsonReader.of(body.source()).readJsonValue()
            } catch (e: Exception) {
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} ${response.message}")
                throw IOException("Unreadable answer")
            }
            if (!response.isSuccessful && OsrmResponseParser.errorCode(root) == null) {
                throw IOException("HTTP ${response.code} ${response.message}")
            }
            val doneAt = System.nanoTime()
            onTimed(FeedTiming((answeredAt - startedAt) / NANOS_PER_MILLI, (doneAt - startedAt) / NANOS_PER_MILLI))
            return root
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                // IPv4 addresses first: this OkHttp version tries the addresses one after the other, each
                // for up to the connect timeout, so an IPv6 address that does not answer would cost 8 s
                // before the working IPv4 one is tried. Only the order changes: IPv6 is still used if needed.
                .dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> =
                        okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
                })
                .build()
        }
    }
}
