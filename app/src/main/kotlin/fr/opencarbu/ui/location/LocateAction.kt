package fr.opencarbu.ui.location

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import fr.opencarbu.domain.model.GeoPoint

/**
 * Builds the "find the user" action, handling the permission request on the way.
 *
 * The returned function does this, in order:
 *  1. if the permission is missing, asks for it (the system shows its dialog);
 *  2. looks for the position;
 *  3. calls [onLocated] with it, or [onProblem] if that is impossible.
 *
 * The function takes one argument, `reportProblems`: pass false for the automatic search at
 * startup, so that a refusal does not display an error the user did not ask for.
 *
 * @param onLocated called with the user's position.
 * @param onProblem called when the position could not be found, with the reason.
 */
@Composable
fun rememberLocateAction(
    onLocated: (GeoPoint) -> Unit,
    onProblem: (LocateProblem) -> Unit,
): (reportProblems: Boolean) -> Unit {
    val context = LocalContext.current
    val finder = remember { LocationFinder(context.applicationContext) }

    // The callbacks below outlive a recomposition: always call the latest version.
    val currentOnLocated by rememberUpdatedState(onLocated)
    val currentOnProblem by rememberUpdatedState(onProblem)

    // Remembers, across the permission dialog, whether failures must be reported.
    val reportProblemsAfterDialog = remember { booleanArrayOf(false) }

    fun search(reportProblems: Boolean) {
        finder.find { location ->
            if (location != null) currentOnLocated(location)
            else if (reportProblems) currentOnProblem(LocateProblem.UNAVAILABLE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) search(reportProblemsAfterDialog[0])
        else if (reportProblemsAfterDialog[0]) currentOnProblem(LocateProblem.PERMISSION_DENIED)
    }

    return remember(finder, permissionLauncher) {
        { reportProblems ->
            if (finder.hasPermission()) {
                search(reportProblems)
            } else {
                reportProblemsAfterDialog[0] = reportProblems
                permissionLauncher.launch(LocationFinder.PERMISSIONS)
            }
        }
    }
}
