package com.example.bloodpressurerecord.ui.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.SessionChoiceChip
import com.example.bloodpressurerecord.ui.common.navigationBarHeight
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.TabularFeatureSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

private val QuickSearchTerms = listOf("头晕", "头痛", "胸闷", "没睡好", "刚运动", "漏服药")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteSearchScreen(
    viewModel: NoteSearchViewModel,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
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
                    placeholder = { Text(stringResource(R.string.note_search_input)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = state.query.isNotEmpty(),
                            enter = if (reducedMotion) EnterTransition.None else fadeIn(AppMotion.floatMicro) + scaleIn(AppMotion.floatMicro, initialScale = 0.8f),
                            exit = if (reducedMotion) ExitTransition.None else fadeOut(AppMotion.floatMicro) + scaleOut(AppMotion.floatMicro, targetScale = 0.8f)
                        ) {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Icon(Icons.Default.Clear, stringResource(R.string.note_search_clear))
                            }
                        }
                    }
                )
            }
        )
        AnimatedContent(
            targetState = state.content,
            contentKey = { it::class },
            transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
            label = "note_search_content"
        ) { content ->
            when (content) {
                NoteSearchContent.Idle -> {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = AppDimensions.pageHorizontalPadding,
                            vertical = AppSpacing.large
                        ),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                    ) {
                        Text(
                            text = stringResource(R.string.note_search_idle_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                        ) {
                            QuickSearchTerms.forEach { term ->
                                SessionChoiceChip(
                                    text = term,
                                    selected = false,
                                    onClick = {
                                        viewModel.updateQuery(term)
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                    }
                                )
                            }
                        }
                    }
                }
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
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TabularFeatureSettings),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.note_search_average, item.avgSystolic, item.avgDiastolic),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TabularFeatureSettings),
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
