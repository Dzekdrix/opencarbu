package fr.opencarbu.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app's Material theme.
 *
 * On Android 12+ the colours follow the system accent ("Material You": derived from the user's
 * wallpaper or chosen colour), so buttons, selected chips, links and sliders match the phone.
 * Older versions have no such colours and use the standard Material palette.
 *
 * The light scheme is always used, on purpose: the base map and the price dots are designed
 * for a light background. (A dark theme would need a dark map style as well.)
 *
 * Price colours on the map (green to red, grey) are not part of the theme: they carry meaning
 * and must not change with the user's accent.
 */
@Composable
fun OpenCarbuTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(LocalContext.current)
    } else {
        lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
