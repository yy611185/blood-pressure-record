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
    fun `默认日历模式并保存近期模式和范围`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val handle = SavedStateHandle()
        val vm = HistoryViewModel(FakeRepository(), handle, zone, todayProvider = { today })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(HistoryViewMode.CALENDAR, vm.uiState.value.viewMode)
        vm.setViewMode(HistoryViewMode.RECENT)
        vm.setRecentPeriod(RecentPeriod.THIS_MONTH)
        advanceUntilIdle()

        assertEquals(HistoryViewMode.RECENT, vm.uiState.value.viewMode)
        assertEquals(RecentPeriod.THIS_MONTH, vm.uiState.value.recentPeriod)
        assertEquals("RECENT", handle["history.viewMode"])
        assertEquals("THIS_MONTH", handle["history.recentPeriod"])
    }

    @Test
    fun `SavedState恢复日历和近期各自状态`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val selected = LocalDate.of(2024, 2, 29)
        val handle = SavedStateHandle(
            mapOf(
                "history.viewMode" to "RECENT",
                "history.recentPeriod" to "THIS_MONTH",
                "history.displayedMonth" to "2024-02",
                "history.selectedDate" to selected.toString()
            )
        )
        val repo = FakeRepository().apply {
            summaries.value = listOf(CalendarSessionSummary(epoch(selected, 9), null, false))
        }
        val vm = HistoryViewModel(repo, handle, zone, todayProvider = { today })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(HistoryViewMode.RECENT, vm.uiState.value.viewMode)
        assertEquals(RecentPeriod.THIS_MONTH, vm.uiState.value.recentPeriod)
        assertEquals(YearMonth.of(2024, 2), vm.uiState.value.displayedMonth)
        assertEquals(selected, vm.uiState.value.selectedDate)
    }

    @Test
    fun `近期记录按时间从新到旧并使用SQL统计`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val repo = FakeRepository().apply {
            records.value = listOf(
                record("early", epoch(today, 7)),
                record("late", epoch(today, 20))
            )
            statistics.value = PeriodStatistics(
                recordCount = 2,
                averageSystolic = 121.5,
                averageDiastolic = 81.5,
                averagePulse = 70.5,
                highRiskCount = 1
            )
        }
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone, todayProvider = { today })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setViewMode(HistoryViewMode.RECENT)
        advanceUntilIdle()

        assertEquals(listOf("late", "early"), vm.uiState.value.recentRecords.map { it.id })
        assertEquals(2, vm.uiState.value.recentSummary?.recordCount)
        assertEquals(1, vm.uiState.value.recentSummary?.highRiskCount)
    }

    @Test
    fun `从首页打开指定日期会切回日历模式`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val repo = FakeRepository().apply {
            summaries.value = listOf(CalendarSessionSummary(epoch(today, 9), null, false))
        }
        val vm = HistoryViewModel(
            repo,
            SavedStateHandle(mapOf("history.viewMode" to "RECENT")),
            zone,
            todayProvider = { today }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openDateWhenAvailable(today)
        advanceUntilIdle()

        assertEquals(HistoryViewMode.CALENDAR, vm.uiState.value.viewMode)
        assertEquals(today, vm.uiState.value.selectedDate)
    }

    @Test
    fun `有记录日期可选择且当天记录按时间正序`() = runTest {
        val date = LocalDate.of(2026, 7, 25)
        val repo = FakeRepository().apply {
            summaries.value = listOf(CalendarSessionSummary(epoch(date, 9), null, false))
            records.value = listOf(record("late", epoch(date, 20)), record("early", epoch(date, 7)))
        }
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone, todayProvider = { date })
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
        val vm = HistoryViewModel(FakeRepository(), SavedStateHandle(), zone, todayProvider = { date })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.selectDate(date)
        assertNull(vm.uiState.value.selectedDate)
    }

    @Test
    fun `日历摘要标记当天是否存在自定义备注`() = runTest {
        val noted = LocalDate.of(2026, 7, 25)
        val plain = LocalDate.of(2026, 7, 26)
        val repo = FakeRepository().apply {
            summaries.value = listOf(
                CalendarSessionSummary(epoch(noted, 9), "加了一片阿尔马尔", false),
                CalendarSessionSummary(epoch(noted, 20), null, false),
                CalendarSessionSummary(epoch(plain, 9), null, false)
            )
        }
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone, todayProvider = { noted })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.daySummaries.getValue(noted).hasNote)
        assertEquals(false, vm.uiState.value.daySummaries.getValue(plain).hasNote)
    }

    @Test
    fun `删除最后一条后清除选择并禁用日期`() = runTest {
        val date = LocalDate.of(2026, 7, 25)
        val repo = FakeRepository().apply {
            summaries.value = listOf(CalendarSessionSummary(epoch(date, 9), null, false))
            records.value = listOf(record("one", epoch(date, 9)))
        }
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone, todayProvider = { date })
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
            summaries.value = listOf(CalendarSessionSummary(epoch(date, 12), null, false))
        }
        val vm = HistoryViewModel(repo, handle, zone, todayProvider = { date })
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
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone, todayProvider = { today })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.showMonth(june)
        vm.showMonth(may)
        juneFlow.value = listOf(CalendarSessionSummary(epoch(juneDate, 9), null, false))
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
                CalendarSessionSummary(epoch(firstDate, 9), null, false),
                CalendarSessionSummary(epoch(secondDate, 9), null, false)
            )
        }
        val firstFlow = MutableStateFlow<List<SessionSummary>>(emptyList())
        val secondFlow = MutableStateFlow<List<SessionSummary>>(emptyList())
        repo.summaryRecordsByStart[firstDate.toEpochMillisRange(zone).startInclusive] = firstFlow
        repo.summaryRecordsByStart[secondDate.toEpochMillisRange(zone).startInclusive] = secondFlow
        val vm = HistoryViewModel(repo, SavedStateHandle(), zone, todayProvider = { secondDate })
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

    @Test
    fun `近期范围跨零点后跟随新的一天`() = runTest {
        val sunday = LocalDate.of(2026, 7, 26)
        val nextMonday = LocalDate.of(2026, 7, 27)
        val todayTicks = MutableStateFlow(sunday)
        val vm = HistoryViewModel(
            FakeRepository(),
            SavedStateHandle(),
            zone,
            todayProvider = { sunday },
            todayTicks = todayTicks
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 7, 20), vm.uiState.value.recentSummary?.startDate)

        todayTicks.value = nextMonday
        advanceUntilIdle()
        assertEquals(nextMonday, vm.uiState.value.recentSummary?.startDate)
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
        val statistics = MutableStateFlow(PeriodStatistics())
        override fun observeSession(sessionId: String): Flow<SessionRecord?> = flowOf(null)
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
        ): Flow<PeriodStatistics> = statistics
        override suspend fun saveSession(input: SaveSessionInput): Result<String> = Result.success("id")
        override suspend fun updateSession(sessionId: String, input: SaveSessionInput) = Result.success(Unit)
        override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
        override suspend fun restoreSession(session: SessionRecord) = Result.success(Unit)
    }
}
