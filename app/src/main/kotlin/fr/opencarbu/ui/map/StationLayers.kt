package fr.opencarbu.ui.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.any
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.product
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.circleTranslate
import org.maplibre.android.style.layers.PropertyFactory.circleTranslateAnchor
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The map layers that draw the stations. Everything is drawn by the GPU from one GeoJSON
 * source, so thousands of dots stay smooth (no Android view per station).
 *
 * Layers, bottom to top:
 *  1. the coloured dots (colour read from each feature's `color` property);
 *  2. the blue "you are here" marker;
 *  3. a small red badge on stations with a temporary shortage;
 *  4. the price text on top of the dots, only from zoom [LABEL_MIN_ZOOM];
 *  5. the names of cities, towns and villages, semi-transparent, on top of everything (taken
 *     from the background map's own data), at every zoom. The background map's own versions of
 *     these names are removed (see [removeBaseMapPlaceNames]) because the dots would hide them.
 *
 * Sizes are tuned with zoom "stops": `stop(zoom, value)` means "at this zoom, this value",
 * with a smooth transition in between. Adjust them here if the map looks too crowded.
 */
object StationLayers {
    private const val SOURCE_ID = "stations"
    /** Public: the map controller queries this layer to find which dot the user tapped. */
    const val DOTS_LAYER_ID = "stations-dots"
    private const val BADGE_LAYER_ID = "stations-badge"
    private const val LABEL_LAYER_ID = "stations-labels"
    private const val USER_SOURCE_ID = "user-location"
    private const val USER_LAYER_ID = "user-location-marker"
    private const val PLACE_NAMES_LAYER_ID = "place-names"

    /**
     * The place names come from the data source of the background map style, not from ours.
     * "openmaptiles" is the name OpenFreeMap's styles give it, and "place" is the layer of that
     * data holding cities, towns and villages. If the map style is ever changed, check both.
     */
    private const val BASE_MAP_SOURCE_ID = "openmaptiles"
    private const val PLACE_SOURCE_LAYER = "place"

    /**
     * Ids of the background map's own labels to keep even though they come from the "place" data:
     * countries and regions ("states"), which are not covered by our layer. Any other label of that
     * data is removed. These are substrings of OpenFreeMap Liberty's layer ids.
     */
    private val KEPT_BASE_LABEL_ID_PARTS = listOf("country", "state")

    private const val USER_BLUE = "#1A73E8"

    /** From this zoom, prices are written on the dots. */
    private const val LABEL_MIN_ZOOM = 8f

    /** The shortage badge is only drawn from this zoom, once the dots are big enough for it. */
    private const val BADGE_MIN_ZOOM = 8f

    private const val SHORTAGE_RED = "#D32F2F"

    /**
     * Must be a font served by the map style's glyph server. OpenFreeMap's styles provide
     * "Noto Sans Regular", "Noto Sans Bold" and "Noto Sans Italic". If the map style is
     * ever changed, check that this font still exists there, or the prices will not appear.
     */
    private val LABEL_FONT = arrayOf("Noto Sans Bold")

    /** Font of the place names (same rule as [LABEL_FONT]). */
    private val PLACE_NAME_FONT = arrayOf("Noto Sans Regular")

    /**
     * Bigger places get bigger and more opaque names. The background map data tags each place
     * with a `class` (city, town, village); this builds an expression giving one number per class.
     * Anything else gets [other].
     */
    private fun byPlaceClass(city: Float, town: Float, village: Float, other: Float = village): Expression =
        match(
            get("class"),
            literal("city"), literal(city),
            literal("town"), literal(town),
            literal("village"), literal(village),
            literal(other),
        )

    /** Size of a name relative to the zoom curve below: cities bigger, villages smaller. */
    private val PLACE_SIZE_FACTOR = byPlaceClass(city = 1.6f, town = 1.0f, village = 0.85f)

    /** Opacity of a name relative to the user's opacity setting: villages fainter than cities. */
    private fun placeOpacity(base: Float): Expression =
        product(literal(base), byPlaceClass(city = 1.0f, town = 0.80f, village = 0.7f))

    /**
     * One stop of the name-size curve. The class factor sits inside each stop because MapLibre
     * only allows `zoom()` at the top level of an `interpolate`, not nested in a product.
     */
    private fun placeSizeStop(atZoom: Float, size: Float, userFactor: Float) =
        stop(atZoom, product(literal(size * userFactor), PLACE_SIZE_FACTOR))

    /** Size curve of the place names, scaled by the user's factor (1.0 = the stops written here). */
    private fun placeTextSize(userFactor: Float) = interpolate(
        linear(), zoom(),
        placeSizeStop(4f, 20f, userFactor), placeSizeStop(8f, 16f, userFactor), placeSizeStop(9f, 10f, userFactor),
        placeSizeStop(10f, 12f, userFactor), placeSizeStop(15f, 23f, userFactor),
    )

    /**
     * The size curves of the station layers, each multiplied by the user's size factor
     * (see MapDisplaySettings.stationSizeFactor). At factor 1.0 they are exactly the stops written
     * here, so these numbers remain the place to tune the default look.
     */
    private fun dotRadius(factor: Float) = interpolate(
        linear(), zoom(),
        stop(4f, 1f * factor), stop(8f, 14f * factor), stop(10f, 20f * factor),
        stop(12f, 22f * factor), stop(14f, 24f * factor),
    )

    private fun badgeRadius(factor: Float) =
        interpolate(linear(), zoom(), stop(10f, 4f * factor), stop(14f, 7f * factor))

    /** Offset of the badge from its dot's centre: it follows the dot's size, see [dotRadius]. */
    private fun badgeOffset(factor: Float) = arrayOf(11f * factor, -11f * factor)

    private fun priceTextSize(factor: Float) = interpolate(
        linear(), zoom(),
        stop(8f, 10f * factor), stop(10f, 14f * factor), stop(12f, 16f * factor), stop(14f, 18f * factor),
    )

    /**
     * Adds the sources and layers to a freshly loaded [style].
     * @param placeNameOpacity opacity of the place names, 0.0 to 1.0 (see MapDisplaySettings).
     * @param stationSizeFactor multiplier for the size of dots, badges and prices.
     * @param placeNameSizeFactor multiplier for the size of the place names.
     */
    fun install(style: Style, placeNameOpacity: Float, stationSizeFactor: Float, placeNameSizeFactor: Float) {
        removeBaseMapPlaceNames(style)
        style.addSource(GeoJsonSource(SOURCE_ID))

        style.addLayer(
            CircleLayer(DOTS_LAYER_ID, SOURCE_ID).withProperties(
                // `toColor` makes the "#RRGGBB" text of the feature a real colour.
                circleColor(toColor(get(StationGeoJson.PROP_COLOR))),
                circleRadius(dotRadius(stationSizeFactor)),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(interpolate(linear(), zoom(), stop(5f, 0f), stop(10f, 1.5f))),
            ),
        )

        // "You are here": a blue dot with a white ring, above the stations.
        style.addSource(GeoJsonSource(USER_SOURCE_ID))
        style.addLayer(
            CircleLayer(USER_LAYER_ID, USER_SOURCE_ID).withProperties(
                circleColor(USER_BLUE),
                circleRadius(8f),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(3f),
            ),
        )

        style.addLayer(
            CircleLayer(BADGE_LAYER_ID, SOURCE_ID).withProperties(
                circleColor(SHORTAGE_RED),
                circleRadius(badgeRadius(stationSizeFactor)),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(1.5f),
                // Shift the badge to the top-right corner of its dot, in screen pixels
                // (the VIEWPORT anchor keeps the offset constant whatever the zoom).
                circleTranslate(badgeOffset(stationSizeFactor)),
                circleTranslateAnchor(Property.CIRCLE_TRANSLATE_ANCHOR_VIEWPORT),
            ).apply {
                setFilter(eq(get(StationGeoJson.PROP_BADGE), literal(1)))
                minZoom = BADGE_MIN_ZOOM
            },
        )

        style.addLayer(
            SymbolLayer(LABEL_LAYER_ID, SOURCE_ID).withProperties(
                textField(get(StationGeoJson.PROP_LABEL)),
                textFont(LABEL_FONT),
                textSize(priceTextSize(stationSizeFactor)),
                textColor("#000000"),
                // Labels that would overlap are hidden, the lowest `sort` (cheapest) wins.
                // The dots themselves are never hidden.
                textAllowOverlap(false),
                symbolSortKey(get(StationGeoJson.PROP_SORT)),
            ).apply { minZoom = LABEL_MIN_ZOOM },
        )

        // Place names, topmost. The opacity applies to the text and its white outline together.
        style.addLayer(
            SymbolLayer(PLACE_NAMES_LAYER_ID, BASE_MAP_SOURCE_ID).withProperties(
                textField(get("name")),
                textFont(PLACE_NAME_FONT),
                textSize(placeTextSize(placeNameSizeFactor)),
                // When two names collide, the more important place (lower key) is drawn.
                symbolSortKey(byPlaceClass(city = 0f, town = 1f, village = 2f)),
                textColor("#222222"),
                textHaloColor("#FFFFFF"),
                textHaloWidth(1.5f),
                textOpacity(placeOpacity(placeNameOpacity)),
                // Two names that would overlap are not both drawn. But a name never hides a
                // price: with `ignorePlacement` the names take no room in the collision check
                // that decides which price labels are shown.
                textAllowOverlap(false),
                textIgnorePlacement(true),
            ).apply {
                setSourceLayer(PLACE_SOURCE_LAYER)
                setFilter(
                    any(
                        eq(get("class"), literal("city")),
                        eq(get("class"), literal("town")),
                        eq(get("class"), literal("village")),
                    ),
                )
            },
        )
    }

    /**
     * Removes the background map's labels of cities, towns, villages and other small places, so
     * that only our own semi-transparent names (drawn above the dots) remain. Country and region
     * names are kept. Called before our layers are added, so it cannot remove them.
     */
    private fun removeBaseMapPlaceNames(style: Style) {
        // `style.layers` is a copy, so removing while looping is safe.
        for (layer in style.layers) {
            if (layer !is SymbolLayer || layer.sourceLayer != PLACE_SOURCE_LAYER) continue
            if (KEPT_BASE_LABEL_ID_PARTS.any { layer.id.contains(it) }) continue
            style.removeLayer(layer)
        }
    }

    /** Moves the "you are here" marker; null removes it. */
    fun setUserLocation(style: Style, geoJson: String) {
        style.getSourceAs<GeoJsonSource>(USER_SOURCE_ID)?.setGeoJson(geoJson)
    }

    /** Changes the opacity of the place names, 0.0 to 1.0 (the opacity of cities; smaller places are fainter). */
    fun setPlaceNameOpacity(style: Style, opacity: Float) {
        style.getLayerAs<SymbolLayer>(PLACE_NAMES_LAYER_ID)?.setProperties(textOpacity(placeOpacity(opacity)))
    }

    /** Resizes the place names (1.0 = standard size). */
    fun setPlaceNameSizeFactor(style: Style, factor: Float) {
        style.getLayerAs<SymbolLayer>(PLACE_NAMES_LAYER_ID)?.setProperties(textSize(placeTextSize(factor)))
    }

    /** Resizes the dots, badges and prices (1.0 = standard size). */
    fun setStationSizeFactor(style: Style, factor: Float) {
        style.getLayerAs<CircleLayer>(DOTS_LAYER_ID)?.setProperties(circleRadius(dotRadius(factor)))
        style.getLayerAs<CircleLayer>(BADGE_LAYER_ID)?.setProperties(
            circleRadius(badgeRadius(factor)),
            circleTranslate(badgeOffset(factor)),
        )
        style.getLayerAs<SymbolLayer>(LABEL_LAYER_ID)?.setProperties(textSize(priceTextSize(factor)))
    }

    /** Replaces the dots on the map. Does nothing if the source is not there (style not loaded). */
    fun setData(style: Style, geoJson: String) {
        style.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(geoJson)
    }
}
