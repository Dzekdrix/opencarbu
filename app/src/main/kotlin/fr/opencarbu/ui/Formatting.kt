package fr.opencarbu.ui

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/** "12 Oct 2026, 18:51" in the phone's language: the date style used across the app. */
fun formatDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

/** "12,4" for 12.4 km; whole kilometres from 100 km on. */
fun formatKm(km: Double): String =
    NumberFormat.getInstance().apply { maximumFractionDigits = if (km < 100) 1 else 0 }.format(km)
