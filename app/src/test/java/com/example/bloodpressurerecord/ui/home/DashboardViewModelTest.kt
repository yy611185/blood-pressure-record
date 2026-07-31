package com.example.bloodpressurerecord.ui.home

import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun `跨零点后今日统计切换到新的一天`() = runTest {
        val day1 = LocalDate.of(2026, 7, 25)
        val day2 = LocalDate.of(2026, 7, 26)
        val repo = FakeRepository(
            countsByRangeStart = mapOf(
                day1.toEpochMillisRange(zone).startInclusive to 3,
                day2.toEpochMillisRange(zone).startInclusive to 1
            )
        )
        val todayTicks = MutableStateFlow(day1)
        val vm = DashboardViewModel(
            repository = repo,
            zoneId = zone,
            todayTicks = todayTicks
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.todayCount)

        todayTicks.value = day2
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.todayCount)
    }

    private class FakeRepository(
        private val countsByRangeStart: Map<Long, Int>
    ) : BloodPressureRepository {
        override fun observeSession(sessionId: String): Flow<SessionRecord?> = flowOf(null)
        override fun observeLatestSessionSummary(): Flow<LatestSessionSummary?> = flowOf(null)
        override fun observeCalendarSessionSummaries(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<List<CalendarSessionSummary>> = flowOf(emptyList())
        override fun observeSessionSummariesInRange(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<List<SessionSummary>> = flowOf(emptyList())
        override fun observePeriodStatistics(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<PeriodStatistics> = flowOf(
            PeriodStatistics(recordCount = countsByRangeStart[startInclusive] ?: 0)
        )
        override suspend fun saveSession(input: SaveSessionInput): Result<String> = Result.success("id")
        override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession(session: SessionRecord): Result<Unit> = Result.success(Unit)
    }
}
