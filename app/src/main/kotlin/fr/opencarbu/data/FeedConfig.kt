package fr.opencarbu.data

/** Where and how the station feed is downloaded. */
object FeedConfig {
    /**
     * "Export" endpoint of the official dataset (Prix des carburants en France – flux
     * instantané v2, Ministry of Economy, Licence Ouverte 2.0). Unlike the paginated
     * "records" endpoint, it returns the whole dataset at once as a JSON array.
     */
    private const val EXPORT_URL =
        "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/" +
            "prix-des-carburants-en-france-flux-instantane-v2/exports/json"

    /**
     * Export URL restricted (`select=`) to the columns [StationMapper] actually reads.
     * This shrinks the download from tens of MB to a few MB.
     */
    val url: String = EXPORT_URL + "?select=" + StationMapper.requestedColumns.joinToString(",")

    /**
     * Reference file of station names and brands (CSV, built from OpenStreetMap, ODbL; published
     * by Chiffrex on data.gouv.fr). The `/datasets/r/<id>` link always redirects to the latest
     * version of the file; OkHttp follows the redirect.
     */
    const val NAMES_URL = "https://www.data.gouv.fr/api/1/datasets/r/fcab3bd4-6c6d-4b73-95d2-cfd5e04ee651"

    /**
     * Address search (IGN Géoplateforme geocoder, based on the Base Adresse Nationale,
     * Licence Ouverte 2.0). Free, no API key.
     */
    const val GEOCODER_URL = "https://data.geopf.fr/geocodage/search/"

    /** Domain name of [url] without a leading "www.", for display: "data.economie.gouv.fr". */
    fun hostOf(url: String): String =
        (java.net.URI(url).host ?: url).removePrefix("www.")

    /** Identifies the app to the server, as good API citizens do. */
    const val USER_AGENT = "OpenCarbu/0.21.4 (open-source Android app)"
}
