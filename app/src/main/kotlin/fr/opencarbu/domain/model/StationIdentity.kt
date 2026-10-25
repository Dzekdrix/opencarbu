package fr.opencarbu.domain.model

/**
 * What a station is called, when known: its brand ("TotalEnergies") and/or its own name
 * ("Relais Total De Tout Vent"), and its postal address as written in that reference file (more
 * reliable than the one in the price feed, which is wrong for some stations). This is not in the price feed; it comes from a separate
 * reference file (see `StationNamesParser`).
 */
data class StationIdentity(
    val brand: String?,
    val name: String?,
    /** House number and street, e.g. "12 Rue de la Gare". */
    val street: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    /**
     * Position of the station's OpenStreetMap object. It replaces the price feed's coordinates,
     * which are off for some stations (see [StationPositions]).
     */
    val position: GeoPoint? = null,
    /** Which OSM object [position] comes from, e.g. "N2401825325" (N = node, W = way, R = relation). */
    val osmObject: String? = null,
) {
    /** True when nothing at all is known. */
    val isEmpty: Boolean
        get() = brand == null && name == null && street == null && postalCode == null && city == null && position == null

    /** This identity, with each missing field filled from [other] (first value found wins). */
    fun mergedWith(other: StationIdentity) = StationIdentity(
        brand ?: other.brand,
        name ?: other.name,
        street ?: other.street,
        postalCode ?: other.postalCode,
        city ?: other.city,
        position ?: other.position,
        if (position != null) osmObject else other.osmObject,
    )

    /**
     * The lines to display, brand first, without repetition: nothing is shown twice when the
     * name equals the brand ("Carrefour" / "Carrefour") or already contains it
     * ("Carrefour" / "Carrefour Market Aix"). Empty when neither is known.
     */
    val lines: List<String>
        get() {
            val b = brand?.trim()?.takeIf { it.isNotEmpty() }
            val n = name?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                b == null -> listOfNotNull(n)
                n == null -> listOf(b)
                n.equals(b, ignoreCase = true) -> listOf(b)
                n.contains(b, ignoreCase = true) -> listOf(n)
                else -> listOf(b, n)
            }
        }
}
