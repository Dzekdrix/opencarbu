package fr.opencarbu.ui.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import fr.opencarbu.domain.model.Station

/**
 * Opens a station in the user's own map application.
 *
 * It uses the standard Android `geo:` link instead of a given app's private link, so it works
 * with any map app the user prefers: OsmAnd, Organic Maps, Google Maps, Waze... Android asks
 * which one to use when several are installed. Those apps show the place with a button to
 * start a route to it.
 */
object MapAppLauncher {

    /**
     * @param label the name shown on the pin (the address, as the feed has no station name).
     * @return false if no application on the phone can show a map.
     */
    fun open(context: Context, station: Station, label: String): Boolean {
        // geo:0,0?q=lat,lon(label) drops a labelled pin at the exact position. The label is
        // percent-encoded by Uri.encode, as the format requires.
        val uri = Uri.parse("geo:0,0?q=${station.latitude},${station.longitude}(${Uri.encode(label)})")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
