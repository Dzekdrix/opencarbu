package fr.opencarbu.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import fr.opencarbu.domain.model.GeoPoint
import java.util.concurrent.TimeUnit

/**
 * Finds the user's approximate position with Android's own `LocationManager`.
 *
 * Why not Google's location library? It needs Google Play Services, which the open-source
 * app store F-Droid (and phones without Google) do not offer. `LocationManager` works
 * everywhere.
 *
 * Approximate location (a few km) is all the app needs to centre the map, and on Android 12+
 * the user may grant only that. Precise location is also requested so that GPS can serve as a
 * fallback on phones without a network provider (de-Googled phones, some emulators).
 *
 * Usage: check [hasPermission], then call [find]; the result arrives on the main thread.
 */
class LocationFinder(private val context: Context) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** True if the user granted approximate or precise location (either is enough here). */
    fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Looks for the position, then calls [onResult] on the main thread with it, or null if it
     * cannot be found (location switched off, no provider, timeout...).
     * Requires [hasPermission].
     */
    fun find(onResult: (GeoPoint?) -> Unit) {
        if (!hasPermission() || !LocationManagerCompat.isLocationEnabled(manager)) {
            onResult(null)
            return
        }

        // 1. The last position the phone already knows: instant, and plenty for centring a map.
        val lastKnown = lastKnownLocation()
        if (lastKnown != null && ageMillis(lastKnown) <= MAX_ACCEPTED_AGE_MILLIS) {
            onResult(lastKnown.toGeoPoint())
            return
        }

        // 2. Otherwise ask for a fresh position. If that fails, an old position is still better
        //    than none: the map is only centred on it, nothing depends on its precision.
        requestFreshLocation { fresh -> onResult((fresh ?: lastKnown)?.toGeoPoint()) }
    }

    /** Providers worth trying, best suited to a quick coarse fix first. */
    private fun candidateProviders(): List<String> {
        val all = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return all.filter {
            try {
                manager.isProviderEnabled(it)
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }

    /**
     * Asks every available provider for one position; the first answer wins, and the others
     * are cancelled. Gives up after [FRESH_FIX_TIMEOUT_MILLIS]. [onResult] is called exactly
     * once, on the main thread, with the position or null.
     *
     * Several providers are tried because phones differ: many have no "network" provider (no
     * Google location service) and the emulator often only offers GPS. A provider the
     * granted permission does not allow (GPS with approximate location only) is skipped.
     *
     * Plain `LocationManager` calls are used on purpose: the AndroidX helper for this has two
     * overloads that the Kotlin compiler cannot tell apart.
     */
    @SuppressLint("MissingPermission") // find() checks the permission before calling this
    private fun requestFreshLocation(onResult: (Location?) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val listeners = mutableListOf<LocationListener>()

        fun finish(result: Location?) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            listeners.forEach { manager.removeUpdates(it) }
            onResult(result)
        }

        for (provider in candidateProviders()) {
            // All LocationListener methods are overridden: on Android 10 the interface has no
            // default implementations, so a missing one could crash if the system called it.
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) = finish(location)

                @Deprecated("Never called on recent Android versions")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                listeners += listener
            } catch (_: SecurityException) {
                // This provider needs precise location, which was not granted: try the next one.
            } catch (_: IllegalArgumentException) {
                // This phone has no such provider.
            }
        }
        if (listeners.isEmpty()) {
            finish(null)
            return
        }
        handler.postDelayed({ finish(null) }, FRESH_FIX_TIMEOUT_MILLIS)
    }

    /** The most recent position known by any provider the granted permission allows. */
    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? =
        manager.getProviders(true)
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: IllegalArgumentException) {
                    null // this phone has no such provider
                } catch (_: SecurityException) {
                    null // this provider needs precise location, not granted
                }
            }
            .maxByOrNull { it.elapsedRealtimeNanos }

    private fun ageMillis(location: Location): Long =
        TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos)

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

    companion object {
        /** Approximate location is enough; precise location, if the user grants it, opens up GPS. */
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        /** A known position younger than this is used as is, without asking for a new one. */
        val MAX_ACCEPTED_AGE_MILLIS = TimeUnit.HOURS.toMillis(1)

        /** How long to wait for a fresh position before falling back to an older one. */
        const val FRESH_FIX_TIMEOUT_MILLIS = 15_000L
    }
}
