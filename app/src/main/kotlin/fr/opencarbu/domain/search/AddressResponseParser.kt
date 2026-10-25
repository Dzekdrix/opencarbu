package fr.opencarbu.domain.search

import fr.opencarbu.domain.model.GeoPoint

/**
 * Turns the geocoder's answer (a GeoJSON FeatureCollection) into [AddressResult]s.
 *
 * It works on the plain structure produced by a JSON reader (`Map`, `List`, `Double`, `String`)
 * rather than on text, so it has no dependency on a JSON library and is unit-tested directly.
 * Features that lack a label or a valid position are skipped instead of failing the whole answer.
 */
object AddressResponseParser {

    fun parse(root: Any?): List<AddressResult> {
        val features = (root as? Map<*, *>)?.get("features") as? List<*> ?: return emptyList()
        return features.mapNotNull(::parseFeature)
    }

    private fun parseFeature(feature: Any?): AddressResult? {
        val map = feature as? Map<*, *> ?: return null
        val properties = map["properties"] as? Map<*, *> ?: return null
        val label = (properties["label"] as? String)?.trim().orEmpty()
        if (label.isEmpty()) return null

        val coordinates = (map["geometry"] as? Map<*, *>)?.get("coordinates") as? List<*> ?: return null
        // GeoJSON order is [longitude, latitude].
        val longitude = (coordinates.getOrNull(0) as? Number)?.toDouble() ?: return null
        val latitude = (coordinates.getOrNull(1) as? Number)?.toDouble() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        return AddressResult(label = label, point = GeoPoint(latitude, longitude))
    }
}
