package fr.opencarbu.ui.map

import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.pricing.PriceStatus
import fr.opencarbu.domain.pricing.PricedStation

/**
 * Converts the stations to draw into GeoJSON text, the format the map engine reads.
 *
 * Why write the JSON by hand instead of building map-library objects?
 *  - it is plain Kotlin, so it is unit-tested on a laptop;
 *  - one text block is handed to the map in one call, and the map parses it on its own
 *    thread: no thousands of Java objects on the app side;
 *  - every value written here is a number or a short string we produce ourselves.
 *
 * Each station becomes one point feature with these properties, used by the map styling
 * (see [StationLayers]):
 *  - `label`: price text shown on the dot ("" when the station has no price);
 *  - `color`: dot colour as `#RRGGBB`;
 *  - `badge`: 1 if the red shortage badge must be drawn, else 0;
 *  - `sort`: priority when two labels collide, lowest wins (cheapest first, grey last);
 *  - `id`: the station id, to identify a station when the user taps it (later feature).
 */
object StationGeoJson {
    const val PROP_ID = "id"
    const val PROP_LABEL = "label"
    const val PROP_COLOR = "color"
    const val PROP_BADGE = "badge"
    const val PROP_SORT = "sort"

    /** A valid, empty collection: what the map shows before any data is available. */
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /** Sort key of dots that are not on the price scale (grey ones): after every priced dot. */
    private const val UNRANKED_SORT_KEY = 2f

    /** 5 decimals of a degree is about 1 metre: plenty, and it keeps the text short. */
    private const val COORDINATE_SCALE = 100_000.0

    private const val HEX_DIGITS = "0123456789ABCDEF"

    /** Rough size of one feature, to size the text buffer once instead of growing it. */
    private const val ESTIMATED_CHARS_PER_FEATURE = 170

    /** GeoJSON for the "you are here" marker: one point, or an empty collection if unknown. */
    fun userPoint(location: GeoPoint?): String {
        if (location == null) return EMPTY
        return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":""" +
            """{"type":"Point","coordinates":[${round(location.longitude)},${round(location.latitude)}]},""" +
            """"properties":{}}]}"""
    }

    /**
     * @param stations the dots to draw.
     * @param formatPrice turns a price in €/L into the label text (the caller decides the
     *   locale: "2,319" in France).
     */
    fun build(stations: List<PricedStation>, formatPrice: (Double) -> String): String {
        if (stations.isEmpty()) return EMPTY

        // Drawing order = order in the file. Draw the worst first so that the cheapest dots
        // end up on top when they overlap. (The sort is stable: equal dots keep their order.)
        val ordered = stations.sortedByDescending { sortKeyOf(it) }

        val json = StringBuilder(ordered.size * ESTIMATED_CHARS_PER_FEATURE + 64)
        json.append("""{"type":"FeatureCollection","features":[""")
        for ((index, priced) in ordered.withIndex()) {
            if (index > 0) json.append(',')
            appendFeature(json, priced, formatPrice)
        }
        json.append("]}")
        return json.toString()
    }

    private fun sortKeyOf(priced: PricedStation): Float = priced.ratio ?: UNRANKED_SORT_KEY

    private fun appendFeature(json: StringBuilder, priced: PricedStation, formatPrice: (Double) -> String) {
        val station = priced.station
        // GeoJSON order is [longitude, latitude] (x, y), the opposite of everyday speech.
        json.append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[""")
        json.append(round(station.longitude)).append(',').append(round(station.latitude))
        json.append("""]},"properties":{"$PROP_ID":""").append(station.id)

        json.append(""","$PROP_LABEL":""")
        // A station known only from the reference file has no price: a question mark instead.
        val label = if (priced.status == PriceStatus.REFERENCE_ONLY) "?" else priced.euroPerLitre?.let(formatPrice).orEmpty()
        appendJsonString(json, label)

        json.append(""","$PROP_COLOR":"""")
        appendHexColor(json, priced.colorArgb)

        json.append("""","$PROP_BADGE":""").append(if (priced.showsShortageBadge) 1 else 0)
        json.append(""","$PROP_SORT":""").append(sortKeyOf(priced))
        json.append("}}")
    }

    private fun round(degrees: Double): Double = Math.round(degrees * COORDINATE_SCALE) / COORDINATE_SCALE

    /** `#RRGGBB` (the alpha channel is dropped: the map styling applies its own opacity). */
    private fun appendHexColor(json: StringBuilder, argb: Int) {
        json.append('#')
        for (shift in 20 downTo 0 step 4) json.append(HEX_DIGITS[(argb shr shift) and 0xF])
    }

    /** A JSON string literal, with the characters that JSON forbids escaped. */
    private fun appendJsonString(json: StringBuilder, text: String) {
        json.append('"')
        for (char in text) {
            when {
                char == '"' -> json.append("\\\"")
                char == '\\' -> json.append("\\\\")
                char < ' ' -> json.append("\\u").append(String.format("%04x", char.code))
                else -> json.append(char)
            }
        }
        json.append('"')
    }
}
