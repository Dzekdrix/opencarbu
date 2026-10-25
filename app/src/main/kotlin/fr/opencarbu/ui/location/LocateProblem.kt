package fr.opencarbu.ui.location

/** Why the user's position could not be found. Shown as a short message under the map. */
enum class LocateProblem {
    /** The user refused the location permission. */
    PERMISSION_DENIED,

    /** Location is switched off on the phone, or no position could be obtained. */
    UNAVAILABLE,
}
