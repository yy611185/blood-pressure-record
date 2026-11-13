package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class NoteSearchUiState(
    val query: String,
    val content: NoteSearchContent
)

internal sealed interface NoteSearchContent {
    data object Idle : NoteSearchContent
    data object Loading : NoteSearchContent
    data object Empty : NoteSearchContent
    data object Error : NoteSearchContent
    data class Results(val items: List<NoteSearchItem>) : NoteSearchContent
}

internal data class NoteSearchItem(
    val id: String,
    val measuredAt: Long,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val snippet: NoteSearchSnippet?
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class NoteSearchViewModel(
    private val repository: BloodPressureRepository,
    private val savedStateHandle: SavedStateHandle,
    private val computeContext: CoroutineContext = Dispatchers.Default
) : ViewModel() {
    private val initialQuery = savedStateHandle.get<String>(QueryKey).orEmpty()
    private val request = MutableStateFlow(SearchRequest(initialQuery.trim()))
    private val _uiState = MutableStateFlow(
        NoteSearchUiState(initialQuery, pendingContent(request.value.keyword))
    )
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            request.flatMapLatest { activeRequest ->
                search(activeRequest.keyword).map { activeRequest to it }
            }.collect { (completedRequest, content) ->
                if (request.value === completedRequest) {
                    _uiState.value = _uiState.value.copy(content = content)
                }
            }
        }
    }

    fun updateQuery(query: String) {
        val keyword = query.trim()
        savedStateHandle[QueryKey] = query
        if (keyword == request.value.keyword) {
            _uiState.value = _uiState.value.copy(query = query)
        } else {
            request.value = SearchRequest(keyword)
            _uiState.value = NoteSearchUiState(query, pendingContent(keyword))
        }
    }

    fun retry() {
        if (_uiState.value.content != NoteSearchContent.Error) return
        _uiState.value = _uiState.value.copy(content = NoteSearchContent.Loading)
        request.value = SearchRequest(request.value.keyword)
    }

    private fun search(keyword: String) = flow<NoteSearchContent> {
        if (keyword.isEmpty()) {
            emit(NoteSearchContent.Idle)
            return@flow
        }

        // Wait before starting the query, after flatMapLatest has cancelled the previous one.
        delay(SearchDelayMillis)
        emitAll(repository.observeNoteSearchResults(keyword).map { rows ->
            if (rows.isEmpty()) {
                NoteSearchContent.Empty
            } else {
                val items = withContext(computeContext) {
                    val context = currentCoroutineContext()
                    rows.map { row ->
                        context.ensureActive()
                        NoteSearchItem(
                            id = row.id,
                            measuredAt = row.measuredAt,
                            avgSystolic = row.avgSystolic,
                            avgDiastolic = row.avgDiastolic,
                            snippet = buildNoteSearchSnippet(keyword, row.note, row.symptomNote, row.factorNote)
                        )
                    }
                }
                NoteSearchContent.Results(items)
            }
        })
    }.catch { emit(NoteSearchContent.Error) }

    private class SearchRequest(val keyword: String)

    private companion object {
        const val QueryKey = "noteSearch.query"
        const val SearchDelayMillis = 250L

        fun pendingContent(keyword: String): NoteSearchContent =
            if (keyword.isEmpty()) NoteSearchContent.Idle else NoteSearchContent.Loading
    }
}
