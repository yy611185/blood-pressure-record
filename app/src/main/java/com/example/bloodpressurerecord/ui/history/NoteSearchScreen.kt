package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.navigationBarHeight
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

@Composable
internal fun NoteSearchScreen(
    viewModel: NoteSearchViewModel,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var didAutoFocus by rememberSaveable { mutableStateOf(false) }
    val listState = key(state.query.trim()) { rememberLazyListState() }
    val bottomInset = maxOf(
        navigationBarHeight(),
        WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    )

    LaunchedEffect(lifecycle, focusRequester) {
        if (!didAutoFocus) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            focusRequester.requestFocus()
            keyboard?.show()
            didAutoFocus = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarTopPadding(), bottom = bottomInset)
    ) {
        AppTopBar(
            title = stringResource(R.string.note_search_title),
            onBack = {
                keyboard?.hide()
                onBack()
            },
            titleContent = {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text(stringResource(R.string.note_search_input)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = if (state.query.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Icon(Icons.Default.Clear, stringResource(R.string.note_search_clear))
                            }
                        }
                    } else {
                        null
                    }
                )
            }
        )
        when (val content = state.content) {
            NoteSearchContent.Idle -> SearchMessage(stringResource(R.string.note_search_title))
            NoteSearchContent.Loading -> SearchMessage(stringResource(R.string.note_search_loading))
            NoteSearchContent.Empty -> SearchMessage(stringResource(R.string.note_search_empty))
            NoteSearchContent.Error -> {
                DataCard(
                    modifier = Modifier.padding(
                        horizontal = AppDimensions.pageHorizontalPadding,
                        vertical = AppSpacing.medium
                    )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Text(
                            stringResource(R.string.note_search_error),
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.note_search_retry))
                        }
                    }
                }
            }
            is NoteSearchContent.Results -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppDimensions.pageHorizontalPadding,
                        top = AppSpacing.medium,
                        end = AppDimensions.pageHorizontalPadding,
                        bottom = AppDimensions.pageBottomGap
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
                ) {
                    items(content.items, key = { it.id }) { item ->
                        NoteSearchResultCard(item) {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onOpenSession(item.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(
            horizontal = AppDimensions.pageHorizontalPadding,
            vertical = AppSpacing.large
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val SearchDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
private fun NoteSearchResultCard(item: NoteSearchItem, onClick: () -> Unit) {
    val zoneId = ZoneId.systemDefault()
    val measuredAt = remember(item.measuredAt, zoneId) {
        Instant.ofEpochMilli(item.measuredAt).atZone(zoneId).format(SearchDateFormat)
    }
    DataCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(
                text = measuredAt,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.note_search_average, item.avgSystolic, item.avgDiastolic),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = NumberFontFamily
            )
            item.snippet?.let { snippet ->
                val highlight = SpanStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    background = MaterialTheme.colorScheme.primaryContainer,
                    fontWeight = FontWeight.Bold
                )
                val annotatedSnippet = remember(snippet, highlight) {
                    buildAnnotatedString {
                        append(snippet.text)
                        for (match in snippet.matches) {
                            addStyle(highlight, match.first, match.last + 1)
                        }
                    }
                }
                Text(
                    text = annotatedSnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
