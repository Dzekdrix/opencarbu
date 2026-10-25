package fr.opencarbu.domain.model

/**
 * Optional "distance by road" feature. It sends the user's position and the station's position
 * to a routing server, so it is OFF until the user switches it on.
 *
 * @property enabled whether road distances are requested at all.
 * @property serverUrl base address of an OSRM-compatible server, without the `/route/v1/...` part.
 */
data class RoutingSettings(
    val enabled: Boolean = false,
    val serverUrl: String = DEFAULT_SERVER_URL,
) {
    companion object {
        /**
         * Public community instance run by FOSSGIS (best effort, no guarantee, at most one request
         * per second, valid User-Agent required, see https://routing.openstreetmap.de/about.html).
         */
        const val DEFAULT_SERVER_URL = "https://routing.openstreetmap.de/routed-car"

        private val URL_PATTERN = Regex("^https?://[^\\s/?#]+(/[^\\s?#]*)?$")

        /** The text without surrounding spaces or trailing slashes (what is stored and used). */
        fun normalize(url: String): String = url.trim().trimEnd('/')

        /** True if [url] looks like an http(s) address without query string or fragment. */
        fun isValidUrl(url: String): Boolean = URL_PATTERN.matches(normalize(url))
    }
}
