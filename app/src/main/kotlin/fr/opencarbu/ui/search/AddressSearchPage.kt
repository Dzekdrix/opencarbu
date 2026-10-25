package fr.opencarbu.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.opencarbu.R
import fr.opencarbu.ui.insetsAllSides
import fr.opencarbu.domain.search.AddressResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** What the result area shows. */
private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Found(val query: String, val results: List<AddressResult>) : SearchState
    data class Failed(val message: String) : SearchState
}

private const val MIN_QUERY_LENGTH = 3
private const val DEBOUNCE_MILLIS = 400L

/**
 * Full-screen address search laid over the map: a text field and the proposed places. The
 * search starts by itself shortly after the user stops typing; typing again cancels the request
 * still in flight. Tapping a result calls [onChosen], and so does the keyboard's search key,
 * with the first (best) result. The system Back button closes the page.
 */
@Composable
fun AddressSearchPage(
    onSearch: suspend (String) -> List<AddressResult>,
    onChosen: (AddressResult) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var query by rememberSaveable { mutableStateOf("") }
    // Bumped by the keyboard's search key to skip the typing delay.
    var searchNow by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focus.requestFocus() }

    LaunchedEffect(query, searchNow) {
        val text = query.trim()
        if (text.length < MIN_QUERY_LENGTH) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        if (searchNow == 0) delay(DEBOUNCE_MILLIS)
        state = SearchState.Loading
        val outcome = try {
            SearchState.Found(text, onSearch(text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SearchState.Failed(e.message ?: e.javaClass.simpleName)
        }
        state = outcome
        // Search key pressed: no need to pick by hand, the best match is used.
        if (searchNow > 0 && outcome is SearchState.Found) outcome.results.firstOrNull()?.let(onChosen)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.insetsAllSides().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; searchNow = 0 },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            TextButton(onClick = { query = "" }) { Text(stringResource(R.string.search_clear)) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        val shown = state
                        val text = query.trim()
                        if (shown is SearchState.Found && shown.query == text && shown.results.isNotEmpty()) {
                            // Results for this very text are already on screen: take the first.
                            onChosen(shown.results.first())
                        } else if (text.length >= MIN_QUERY_LENGTH) {
                            searchNow++ // search at once; the first result is then used
                        }
                    }),
                )
                TextButton(onClick = onClose) { Text(stringResource(R.string.search_close)) }
            }
            HorizontalDivider()
            when (val current = state) {
                SearchState.Idle -> Message(
                    if (query.isBlank()) "" else stringResource(R.string.search_min_chars),
                )
                SearchState.Loading -> Message(stringResource(R.string.search_loading))
                is SearchState.Failed -> Message(stringResource(R.string.search_error, current.message))
                is SearchState.Found ->
                    if (current.results.isEmpty()) {
                        Message(stringResource(R.string.search_empty))
                    } else {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(current.results) { result ->
                                Text(
                                    result.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onChosen(result) }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                )
                                HorizontalDivider()
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}
