package fr.opencarbu.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.opencarbu.R

/**
 * The "burger" button (three bars) that opens a small menu with the two pages: Info and Settings.
 * The three bars are drawn, so no icon library is needed.
 */
@Composable
fun MenuButton(onPageChosen: (MenuPage) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.menu_open)
    val tint = MaterialTheme.colorScheme.onSurface

    Box(modifier) {
        SmallFloatingActionButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val strokeWidth = 2.dp.toPx()
                for (fraction in listOf(0.2f, 0.5f, 0.8f)) {
                    val y = size.height * fraction
                    drawLine(tint, Offset(0f, y), Offset(size.width, y), strokeWidth, cap = StrokeCap.Round)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_info)) },
                onClick = { expanded = false; onPageChosen(MenuPage.INFO) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_settings)) },
                onClick = { expanded = false; onPageChosen(MenuPage.SETTINGS) },
            )
        }
    }
}
