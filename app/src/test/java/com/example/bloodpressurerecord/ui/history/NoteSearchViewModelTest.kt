package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.NoteSearchResult
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.ui.home.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val store = ViewModelStore()

    @After
    fun clearViewModels() {
        store.clear()
    }

    @Test
    fun `blank input skips the query and clearing immediately removes results`() = runTest {
        val repo = SearchRepository { flowOf(listOf(row("record", "头痛"))) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.updateQuery("   ")
        advanceUntilIdle()
        assertEquals(NoteSearchContent.Idle, vm.uiState.value.content)
        assertEquals(emptyList<String>(), repo.queries)

        vm.updateQuery(" 头 ")
        assertEquals(NoteSearchContent.Loading, vm.uiState.value.content)
        advanceTimeBy(249)
        assertEquals(emptyList<String>(), repo.queries)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("头"), repo.queries)
        assertEquals(listOf("record"), vm.resultIds())

        vm.updateQuery(" 头    ")
        advanceUntilIdle()
        assertEquals(listOf("头"), repo.queries)
        assertEquals(listOf("record"), vm.resultIds())

        vm.updateQuery("")
        assertEquals(NoteSearchUiState("", NoteSearchContent.Idle), vm.uiState.value)
        advanceUntilIdle()
        assertEquals(listOf("头"), repo.queries)
    }

    @Test
    fun `new input cancels the old flow before its own debounce ends`() = runTest {
        val oldResults = MutableStateFlow(listOf(row("first", "头痛")))
        val ended = mutableListOf<String>()
        val repo = SearchRepository { keyword ->
            if (keyword == "头") {
                flow {
                    try {
                        emitAll(oldResults)
                    } finally {
                        ended += keyword
                    }
                }
            } else {
                flowOf(listOf(row("second", "头晕")))
            }
        }
        val vm = viewModel(repo)
        vm.updateQuery("头")
        advanceUntilIdle()
        assertEquals(listOf("first"), vm.resultIds())

        vm.updateQuery("头晕")
        oldResults.value = listOf(row("outdated", "头痛"))
        runCurrent()
        assertEquals(listOf("头"), ended)
        assertEquals(NoteSearchContent.Loading, vm.uiState.value.content)
        assertEquals(listOf("头"), repo.queries)

        vm.updateQuery(" 晕 ")
        advanceUntilIdle()
        assertEquals(listOf("头", "晕"), repo.queries)
        assertEquals(listOf("second"), vm.resultIds())
    }

    @Test
    fun `observed changes update the snippet then remove the result`() = runTest {
        val rows = MutableStateFlow(listOf(row("record", "今天头痛")))
        val vm = viewModel(SearchRepository { rows })
        vm.updateQuery("头痛")
        advanceUntilIdle()
        assertEquals("今天头痛", vm.singleResult().snippet?.text)

        rows.value = listOf(row("record", "昨晚头痛"))
        runCurrent()
        assertEquals("昨晚头痛", vm.singleResult().snippet?.text)

        rows.value = emptyList()
        runCurrent()
        assertEquals(NoteSearchContent.Empty, vm.uiState.value.content)
    }

    @Test
    fun `reports a query error and can retry the same keyword`() = runTest {
        var failed = true
        val repo = SearchRepository {
            if (failed) error("test query error")
            flowOf(emptyList())
        }
        val vm = viewModel(repo)
        vm.updateQuery("头痛")
        advanceUntilIdle()
        assertEquals(NoteSearchContent.Error, vm.uiState.value.content)

        failed = false
        vm.retry()
        assertEquals(NoteSearchContent.Loading, vm.uiState.value.content)
        advanceUntilIdle()
        assertEquals(NoteSearchContent.Empty, vm.uiState.value.content)
        assertEquals(listOf("头痛", "头痛"), repo.queries)
    }

    @Test
    fun `restores saved query and a new entry starts empty`() = runTest {
        val repo = SearchRepository { flowOf(listOf(row("record", "头痛"))) }
        val handle = SavedStateHandle()
        viewModel(repo, handle, key = "initial").updateQuery(" 头痛 ")

        val restored = viewModel(repo, handle, key = "restored")
        val fresh = viewModel(repo, SavedStateHandle(), key = "fresh")
        advanceUntilIdle()

        assertEquals(" 头痛 ", restored.uiState.value.query)
        assertEquals(listOf("record"), restored.resultIds())
        assertEquals(NoteSearchUiState("", NoteSearchContent.Idle), fresh.uiState.value)
    }

    private fun viewModel(
        repository: BloodPressureRepository,
        handle: SavedStateHandle = SavedStateHandle(),
        key: String = "search"
    ): NoteSearchViewModel =
        NoteSearchViewModel(repository, handle, computeContext = dispatcher).also { store.put(key, it) }

    private fun NoteSearchViewModel.resultIds() =
        (uiState.value.content as NoteSearchContent.Results).items.map { it.id }

    private fun NoteSearchViewModel.singleResult() =
        (uiState.value.content as NoteSearchContent.Results).items.single()

    private fun row(id: String, note: String) = NoteSearchResult(
        id = id,
        measuredAt = 0L,
        avgSystolic = 118,
        avgDiastolic = 76,
        note = note,
        symptomNote = null,
        factorNote = null
    )

    private class SearchRepository(
        private val results: (String) -> Flow<List<NoteSearchResult>>
    ) : BloodPressureRepository {
        val queries = mutableListOf<String>()

        override fun observeNoteSearchResults(keyword: String): Flow<List<NoteSearchResult>> {
            queries += keyword
            return results(keyword)
        }

        override fun observeSession(sessionId: String): Flow<SessionRecord?> = error("Unexpected call")
        override fun observeLatestSessionSummary(): Flow<LatestSessionSummary?> = error("Unexpected call")
        override fun observeCalendarSessionSummaries(startInclusive: Long, endExclusive: Long):
            Flow<List<CalendarSessionSummary>> = error("Unexpected call")
        override fun observeSessionSummariesInRange(startInclusive: Long, endExclusive: Long):
            Flow<List<SessionSummary>> = error("Unexpected call")
        override fun observePeriodStatistics(startInclusive: Long, endExclusive: Long):
            Flow<PeriodStatistics> = error("Unexpected call")
        override suspend fun saveSession(input: SaveSessionInput): Result<String> = error("Unexpected call")
        override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> =
            error("Unexpected call")
        override suspend fun deleteSession(sessionId: String): Result<Unit> = error("Unexpected call")
        override suspend fun restoreSession(session: SessionRecord): Result<Unit> = error("Unexpected call")
    }
}
