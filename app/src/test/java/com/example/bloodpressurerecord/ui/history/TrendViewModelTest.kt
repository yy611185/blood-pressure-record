package com.example.bloodpressurerecord.ui.history

import android.net.Uri
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SettingsBundle
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.TrendRepository
import com.example.bloodpressurerecord.data.repository.UserProfile
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.DayNightAverage
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.ui.home.MainDispatcherRule
import java.time.LocalDate
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
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrendViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun `昼夜统计沿用七天三十天全部范围且不额外包含跨午夜窗口外记录`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val repo = FakeTrendRepository()
        repo.records.value = listOf(0L, 6L, 7L, 29L, 30L).flatMapIndexed { index, daysAgo ->
            val date = today.minusDays(daysAgo)
            listOf(
                TrendRecord(
                    id = "day-$daysAgo",
                    measuredAt = date.atTime(6, 0).atZone(zone).toInstant().toEpochMilli(),
                    systolic = 120 + index * 20,
                    diastolic = 80 + index * 10,
                    pulse = null,
                    category = "NORMAL"
                ),
                TrendRecord(
                    id = "night-$daysAgo",
                    measuredAt = date.atTime(if (daysAgo == 0L) 0 else 22, 0)
                        .atZone(zone).toInstant().toEpochMilli(),
                    systolic = 110 + index * 20,
                    diastolic = 70 + index * 10,
                    pulse = null,
                    category = "NORMAL"
                )
            )
        }
        val vm = TrendViewModel(
            trendRepository = repo,
            settingsRepository = FakeSettingsRepository(),
            clockMillis = { now },
            zoneId = zone,
            computeContext = UnconfinedTestDispatcher(testScheduler),
            todayTicks = flowOf(today)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.setRange(TrendRange.DAYS_7)
        advanceUntilIdle()
        assertEquals(DayNightAverage(130, 85, 2, 2), vm.uiState.value.insights.daytimeAverage)
        assertEquals(DayNightAverage(120, 75, 2, 2), vm.uiState.value.insights.nighttimeAverage)

        vm.setRange(TrendRange.DAYS_30)
        advanceUntilIdle()
        assertEquals(DayNightAverage(150, 95, 4, 4), vm.uiState.value.insights.daytimeAverage)
        assertEquals(DayNightAverage(140, 85, 4, 4), vm.uiState.value.insights.nighttimeAverage)

        vm.setRange(TrendRange.ALL)
        advanceUntilIdle()
        assertEquals(DayNightAverage(160, 100, 5, 5), vm.uiState.value.insights.daytimeAverage)
        assertEquals(DayNightAverage(150, 90, 5, 5), vm.uiState.value.insights.nighttimeAverage)
    }

    @Test
    fun `打开趋势页之后新增的记录会进入折线`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val openedAt = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        var nowMillis = openedAt
        val repo = FakeTrendRepository()
        val vm = TrendViewModel(
            trendRepository = repo,
            settingsRepository = FakeSettingsRepository(),
            clockMillis = { nowMillis },
            zoneId = zone,
            computeContext = UnconfinedTestDispatcher(testScheduler),
            todayTicks = flowOf(today)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.series.points.size)

        // 打开页面 1 小时后新增一条记录：查询上界不应固定在打开页面的时刻。
        nowMillis = openedAt + 60 * 60 * 1000L
        repo.records.value = listOf(
            TrendRecord(
                id = "later",
                measuredAt = openedAt + 30 * 60 * 1000L,
                systolic = 128,
                diastolic = 82,
                pulse = 70,
                category = "NORMAL",
                containsHighRiskReading = false
            )
        )
        advanceUntilIdle()

        // 30 天显示每次原始测量，所以新记录直接作为自己的节点进入折线。
        assertEquals(listOf("later"), vm.uiState.value.series.points.map { it.id })
        assertEquals(1, vm.uiState.value.series.rawRecordCount)
        assertEquals(1, vm.uiState.value.series.points.single().recordCount)
        assertEquals(1, vm.uiState.value.insights.recordCount)
        assertEquals(1, vm.uiState.value.insights.recordDays)
        assertEquals(128 to 82, vm.uiState.value.insights.periodAverage)
    }

    private class FakeTrendRepository : TrendRepository {
        val records = MutableStateFlow<List<TrendRecord>>(emptyList())

        // 模拟 DAO 行为：只返回请求范围内的记录。
        override fun observeRecords(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<List<TrendRecord>> = records.map { list ->
            list.filter { it.measuredAt >= startInclusive && it.measuredAt < endExclusive }
        }

        override fun observeStatistics(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<PeriodStatistics> = flowOf(PeriodStatistics())

        override suspend fun getRecords(
            startInclusive: Long,
            endExclusive: Long
        ): List<TrendRecord> = emptyList()
    }

    private class FakeSettingsRepository : SettingsRepository {
        override fun observeSettings(): Flow<SettingsBundle> = flowOf(SettingsBundle())
        override suspend fun setLargeTextEnabled(enabled: Boolean) = Unit
        override suspend fun setHighRiskAlertEnabled(enabled: Boolean) = Unit
        override suspend fun setShowTrendChart(enabled: Boolean) = Unit
        override suspend fun setAppearanceMode(mode: String) = Unit
        override suspend fun setShowBuddy(enabled: Boolean) = Unit
        override suspend fun setDiscardFirstReading(enabled: Boolean) = Unit
        override suspend fun setMorningReminderEnabled(enabled: Boolean) = Unit
        override suspend fun setMorningReminderTime(value: String) = Unit
        override suspend fun setEveningReminderEnabled(enabled: Boolean) = Unit
        override suspend fun setEveningReminderTime(value: String) = Unit
        override suspend fun setMedicationReminderEnabled(enabled: Boolean) = Unit
        override suspend fun setMedicationCalendarSyncEnabled(enabled: Boolean) = Unit
        override suspend fun refreshReminders() = Unit
        override suspend fun saveUserProfile(profile: UserProfile) = Unit
        override suspend fun clearAllData(): Result<com.example.bloodpressurerecord.data.repository.ClearAllDataResult> =
            Result.success(
                com.example.bloodpressurerecord.data.repository.ClearAllDataResult(
                    databaseCleared = true,
                    settingsCleared = true,
                    remindersRescheduled = true,
                    widgetRefreshed = true
                )
            )
        override suspend fun exportBackupXlsxToUri(
            uri: Uri,
            fileNameHint: String,
            passphrase: CharArray?
        ): Result<String> =
            Result.success("")
        override suspend fun importBackupXlsxFromUri(
            uri: Uri,
            passphrase: CharArray?
        ): Result<String> = Result.success("")
    }
}
