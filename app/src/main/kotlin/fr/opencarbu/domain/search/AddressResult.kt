package fr.opencarbu.domain.search

import fr.opencarbu.domain.model.GeoPoint

/** Radius of the area shown around a chosen search result, whatever the kind of place. */
const val SEARCH_RADIUS_KM = 10.0

/** One place proposed by the address search. */
data class AddressResult(
    /** Full human-readable address, e.g. "8 Boulevard du Port 80000 Amiens". */
    val label: String,
    val point: GeoPoint,
    /** Set when the result is a station found by its identifier: choosing it opens that station's sheet. */
    val stationId: Long? = null,
)
