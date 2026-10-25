package fr.opencarbu.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * The app is drawn edge to edge, behind the system bars. These modifiers keep content clear of what lies over the
 * screen edges: the status and navigation bars AND the camera cut-out, wherever they are (in landscape the
 * 3-button navigation bar and the cut-out are on the sides, and they differ with the way the phone is turned).
 */

/** Clear of the bars and the cut-out on all four sides: for full-screen pages. */
@Composable
fun Modifier.insetsAllSides(): Modifier =
    windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))

/** Clear of them at the top and on both sides: for the buttons at the top of the map. */
@Composable
fun Modifier.insetsTopAndSides(): Modifier = windowInsetsPadding(
    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
)

/** Clear of them at the bottom and on both sides: for the legend panel at the bottom of the map. */
@Composable
fun Modifier.insetsBottomAndSides(): Modifier = windowInsetsPadding(
    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
)
