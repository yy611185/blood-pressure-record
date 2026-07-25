package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.SavedStateHandle
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import com.example.bloodpressurerecord.ui.home.MainDispatcherRule
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun `有记录日期可选择且当天记录按时间正序`() = runTest {
        val date = LocalDate.of(2026, 7, 25)
        val repo = FakeRepository().apply {
            summaries.value = listOf(CalendarSessionSummary(epoch(date, 9), false))
            records.value = listOf(record("late", epoch(date, 20)), record("early", epoch(date, 7)))
        }
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone) { date }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.selectDate(date)
        advanceUntilIdle()

        assertEquals(date, vm.uiState.value.selectedDate)
        assertEquals(listOf("early", "late"), vm.uiState.value.selectedDayRecords.map { it.id })
    }

    @Test
    fun `无记录日期不可选择`() = runTest {
        val date = LocalDate.of(2026, 7, 25)
        val vm = HistoryViewModel(FakeRepository(), SavedStateHandle(), zone) { date }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.selectDate(date)
        assertNull(vm.uiState.value.selectedDate)
    }

    @Test
    fun `删除最后一条后清除选择并禁用日期`() = runTest {
        val date = LocalDate.of(2026, 7, 25)
        val repo = FakeRepository().apply {
            summaries.value = listOf(CalendarSessionSummary(epoch(date, 9), false))
            records.value = listOf(record("one", epoch(date, 9)))
        }
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone) { date }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.selectDate(date)
        advanceUntilIdle()
        repo.records.value = emptyList()
        repo.summaries.value = emptyList()
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedDate)
        assertTrue(vm.uiState.value.daySummaries.isEmpty())
    }

    @Test
    fun `SavedState恢复年月和选中日期`() = runTest {
        val date = LocalDate.of(2024, 2, 29)
        val handle = SavedStateHandle(
            mapOf(
                "history.displayedMonth" to "2024-02",
                "history.selectedDate" to "2024-02-29"
            )
        )
        val repo = FakeRepository().apply {
            summaries.value = listOf(CalendarSessionSummary(epoch(date, 12), false))
        }
        val vm = HistoryViewModel(repo, handle, zone) { date }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("2024-02", vm.uiState.value.displayedMonth.toString())
        assertEquals(date, vm.uiState.value.selectedDate)
    }

    @Test
    fun `快速切换月份时旧月份结果不能覆盖新月份`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val june = YearMonth.of(2026, 6)
        val may = YearMonth.of(2026, 5)
        val juneDate = june.atDay(10)
        val repo = FakeRepository()
        val juneFlow = MutableStateFlow<List<CalendarSessionSummary>>(emptyList())
        val mayFlow = MutableStateFlow<List<CalendarSessionSummary>>(emptyList())
        repo.summariesByStart[june.toEpochMillisRange(zone).startInclusive] = juneFlow
        repo.summariesByStart[may.toEpochMillisRange(zone).startInclusive] = mayFlow
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone) { today }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.showMonth(june)
        vm.showMonth(may)
        juneFlow.value = listOf(CalendarSessionSummary(epoch(juneDate, 9), false))
        advanceUntilIdle()

        assertEquals(may, vm.uiState.value.displayedMonth)
        assertTrue(vm.uiState.value.daySummaries.isEmpty())
    }

    @Test
    fun `快速选择日期时旧日期结果不能串页`() = runTest {
        val firstDate = LocalDate.of(2026, 7, 24)
        val secondDate = LocalDate.of(2026, 7, 25)
        val repo = FakeRepository().apply {
            summaries.value = listOf(
                CalendarSessionSummary(epoch(firstDate, 9), false),
                CalendarSessionSummary(epoch(secondDate, 9), false)
            )
        }
        val firstFlow = MutableStateFlow<List<SessionSummary>>(emptyList())
        val secondFlow = MutableStateFlow<List<SessionSummary>>(emptyList())
        repo.summaryRecordsByStart[firstDate.toEpochMillisRange(zone).startInclusive] = firstFlow
        repo.summaryRecordsByStart[secondDate.toEpochMillisRange(zone).startInclusive] = secondFlow
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone) { secondDate }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectDate(firstDate)
        vm.selectDate(secondDate)
        secondFlow.value = listOf(summaryRecord("second", epoch(secondDate, 8)))
        firstFlow.value = listOf(summaryRecord("first-stale", epoch(firstDate, 8)))
        advanceUntilIdle()

        assertEquals(secondDate, vm.uiState.value.selectedDate)
        assertEquals(listOf("second"), vm.uiState.value.selectedDayRecords.map { it.id })
    }

    private fun epoch(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun record(id: String, measuredAt: Long) = SessionRecord(
        id = id,
        measuredAt = measuredAt,
        scene = "居家安静",
        note = null,
        symptoms = emptyList(),
        avgSystolic = 120,
        avgDiastolic = 80,
        avgPulse = 70,
        category = "NORMAL",
        containsHighRiskReading = false,
        readings = emptyList()
    )

    private fun summaryRecord(id: String, measuredAt: Long) = SessionSummary(
        id = id,
        measuredAt = measuredAt,
        avgSystolic = 120,
        avgDiastolic = 80,
        avgPulse = 70,
        category = "NORMAL",
        scene = "居家安静",
        noteSummary = null,
        containsHighRiskReading = false
    )

    private class FakeRepository : BloodPressureRepository {
        val summaries = MutableStateFlow<List<CalendarSessionSummary>>(emptyList())
        val records = MutableStateFlow<List<SessionRecord>>(emptyList())
        val summariesByStart =
            mutableMapOf<Long, MutableStateFlow<List<CalendarSessionSummary>>>()
        val summaryRecordsByStart =
            mutableMapOf<Long, MutableStateFlow<List<SessionSummary>>>()
        override fun observeSessionCount(): Flow<Int> = flowOf(0)
        override fun observeSessions(): Flow<List<SessionRecord>> = records
        override fun observeSession(sessionId: String): Flow<SessionRecord?> = flowOf(null)
        override fun observeLatestSession(): Flow<SessionRecord?> = flowOf(null)
        override fun observeLatestSessionSummary(): Flow<LatestSessionSummary?> = flowOf(null)
        override fun observeCalendarSessionSummaries(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<List<CalendarSessionSummary>> = summariesByStart[startInclusive] ?: summaries
        override fun observeSessionSummariesInRange(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<List<SessionSummary>> = summaryRecordsByStart[startInclusive]
            ?: records.map { values ->
                values.map {
                    SessionSummary(
                        id = it.id,
                        measuredAt = it.measuredAt,
                        avgSystolic = it.avgSystolic,
                        avgDiastolic = it.avgDiastolic,
                        avgPulse = it.avgPulse,
                        category = it.category,
                        scene = it.scene,
                        noteSummary = it.note,
                        containsHighRiskReading = it.containsHighRiskReading
                    )
                }
            }
        override fun observePeriodStatistics(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<PeriodStatistics> = flowOf(PeriodStatistics())
        override fun observeSessionsInRange(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<List<SessionRecord>> = records
        override suspend fun saveSession(input: SaveSessionInput): Result<String> = Result.success("id")
        override suspend fun updateSession(sessionId: String, input: SaveSessionInput) = Result.success(Unit)
        override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
        override suspend fun restoreSession(session: SessionRecord) = Result.success(Unit)
    }
}
