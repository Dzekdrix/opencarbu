package fr.opencarbu.domain.model

/**
 * Corrects station positions with the ones of the reference file (OpenStreetMap), because the
 * coordinates declared in the official price feed are imprecise or wrong for some stations.
 * Optionally also adds the stations that only the reference file knows.
 */
object StationPositions {

    /**
     * [stations] with the position of [identities] where one is known; the other stations are
     * returned untouched. The same list instance is returned when nothing changes, so callers
     * can cheaply detect "no change".
     *
     * @param includeReferenceOnly when true, every entry of [identities] that has a position but
     *   no station in [stations] is added as a station without prices ([Station.isReferenceOnly]),
     *   with the address of the reference file. This is a test aid to compare the two sources.
     */
    fun apply(
        stations: List<Station>,
        identities: Map<Long, StationIdentity>,
        includeReferenceOnly: Boolean = false,
    ): List<Station> {
        if (identities.isEmpty()) return stations
        var changed = false
        val result = ArrayList<Station>(stations.size)
        for (station in stations) {
            val position = identities[station.id]?.position
            if (position != null && (position.latitude != station.latitude || position.longitude != station.longitude)) {
                result.add(station.copy(latitude = position.latitude, longitude = position.longitude))
                changed = true
            } else {
                result.add(station)
            }
        }
        if (includeReferenceOnly) {
            val known = HashSet<Long>(stations.size * 2)
            for (station in stations) known.add(station.id)
            for ((id, identity) in identities) {
                val position = identity.position ?: continue
                if (id in known) continue
                result.add(
                    Station(
                        id = id,
                        latitude = position.latitude,
                        longitude = position.longitude,
                        address = identity.street.orEmpty(),
                        postalCode = identity.postalCode.orEmpty(),
                        city = identity.city.orEmpty(),
                        prices = emptyMap(),
                        isOpen24h = false,
                        isReferenceOnly = true,
                    ),
                )
                changed = true
            }
        }
        return if (changed) result else stations
    }
}
