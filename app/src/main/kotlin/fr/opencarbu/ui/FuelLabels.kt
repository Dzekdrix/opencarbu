package fr.opencarbu.ui

import androidx.annotation.StringRes
import fr.opencarbu.R
import fr.opencarbu.domain.model.FuelType

/** Translatable display name of a fuel (kept out of the domain layer on purpose). */
@get:StringRes
val FuelType.labelRes: Int
    get() = when (this) {
        FuelType.DIESEL -> R.string.fuel_diesel
        FuelType.SP95 -> R.string.fuel_sp95
        FuelType.E10 -> R.string.fuel_e10
        FuelType.SP98 -> R.string.fuel_sp98
        FuelType.E85 -> R.string.fuel_e85
        FuelType.LPG -> R.string.fuel_lpg
    }
