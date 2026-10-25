package fr.opencarbu.domain.model

/**
 * Fuel types sold in French stations.
 *
 * This enum is deliberately free of any display text or API column name:
 * - user-visible labels live in Android string resources (so they can be translated);
 * - the open-data column names live in the data layer ([fr.opencarbu.data.StationMapper]).
 *
 * NEVER reorder or remove a value: the price history stores each fuel as its position (`ordinal`) in this
 * list. New fuels go at the end.
 */
enum class FuelType {
    /** Diesel ("Gazole"). */
    DIESEL,

    /** Unleaded 95 ("SP95"). */
    SP95,

    /** Unleaded 95 with up to 10% ethanol ("SP95-E10"). */
    E10,

    /** Unleaded 98 ("SP98"). */
    SP98,

    /** Superethanol E85 ("Bioéthanol"). */
    E85,

    /** Liquefied petroleum gas ("GPLc"). */
    LPG,
}
