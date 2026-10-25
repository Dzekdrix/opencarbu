package fr.opencarbu.data

import fr.opencarbu.domain.model.FuelType

/**
 * How the selected fuel is written to, and read back from, the app's saved settings.
 *
 * The saved text is the enum constant's name ("DIESEL", "E10"...). It never changes with the
 * app language, which is why it is used rather than the displayed label. If a future version
 * renames or removes a fuel, an unknown saved name simply falls back to the default.
 */
object FuelPreference {
    /** Fuel shown on first launch, or when the saved value is unusable. */
    val DEFAULT = FuelType.DIESEL

    /** Name of the saved-settings entry holding the selected fuel. */
    const val KEY = "selected_fuel"

    /** Name of the saved-settings entry holding the "default fuel" setting. */
    const val KEY_DEFAULT = "default_fuel"

    /** Saved in [KEY_DEFAULT] when the user wants the app to reopen on the last fuel used. */
    private const val LAST_USED = "last_used"

    /** Text to save for the default-fuel setting; null means "the last fuel used". */
    fun defaultToStored(fuel: FuelType?): String = fuel?.name ?: LAST_USED

    /** The default fuel for a saved text; null ("last used") if there is none or it is unknown. */
    fun defaultFromStored(stored: String?): FuelType? = FuelType.entries.firstOrNull { it.name == stored }

    /**
     * The fuel to select when the app starts: the user's [defaultFuel] if they chose one,
     * otherwise the fuel they used last time ([lastUsedStored], see [KEY]).
     */
    fun startupFuel(defaultFuel: FuelType?, lastUsedStored: String?): FuelType =
        defaultFuel ?: fromStored(lastUsedStored)

    /** Text to save for [fuel]. */
    fun toStored(fuel: FuelType): String = fuel.name

    /** The fuel for a saved text; [DEFAULT] if there is none or it is not a known fuel. */
    fun fromStored(stored: String?): FuelType =
        FuelType.entries.firstOrNull { it.name == stored } ?: DEFAULT
}
