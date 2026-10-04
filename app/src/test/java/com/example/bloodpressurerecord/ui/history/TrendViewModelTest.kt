package com.example.bloodpressurerecord.ui.history

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SettingsBundle
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.TrendRepository
import com.example.bloodpressurerecord.data.repository.UserProfile
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.DayNightAverage
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendAggregation
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrendViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun `保存的筛选选中点与绝对视窗在重建后恢复`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val measuredAt = today.minusDays(2).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val viewportStart = measuredAt - 60 * 60 * 1000L
        val viewportEnd = measuredAt + 60 * 60 * 1000L
        val handle = SavedStateHandle(mapOf(
            "trend.range" to TrendRange.DAYS_7.name,
            "trend.metric" to TrendMetricType.DIASTOLIC.name,
            "trend.aggregation" to TrendAggregation.RAW.name,
            "trend.selectedPointId" to "selected",
            "trend.selectedTimestamp" to measuredAt,
            "trend.showPulse" to false,
            "trend.fullscreen" to true,
            "trend.viewportStart" to viewportStart,
            "trend.viewportEnd" to viewportEnd
        ))
        val repo = FakeTrendRepository().apply {
            records.value = listOf(record("selected", measuredAt))
        }
        val vm = TrendViewModel(
            repo, FakeSettingsRepository(), { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today), handle
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(TrendRange.DAYS_7, vm.uiState.value.range)
        assertEquals(TrendMetricType.DIASTOLIC, vm.uiState.value.metric)
        assertEquals("selected", vm.uiState.value.selectedPointId)
        assertEquals(false, vm.uiState.value.showPulse)
        assertEquals(true, vm.uiState.value.fullscreen)
        vm.chartController.updateSeries(vm.uiState.value.series)
        val viewport = vm.chartController.viewportFor(vm.uiState.value.range)
        assertEquals(viewportStart, viewport.startMillis())
        assertEquals(viewportEnd, viewport.endMillis())

        // 布局重建与同一期间新增记录保留历史绝对窗口。
        repo.records.value += record("new", now - 60 * 1000L)
        advanceUntilIdle()
        vm.chartController.updateSeries(vm.uiState.value.series)
        vm.chartController.updateSeries(vm.uiState.value.series)
        assertEquals(viewportStart, viewport.startMillis())
        assertEquals(viewportEnd, viewport.endMillis())
        assertEquals("selected", vm.uiState.value.selectedPointId)

        vm.setMetric(TrendMetricType.SYSTOLIC)
        vm.setShowPulse(true)
        vm.setFullscreenShowPulse(true)
        vm.setShowTimeLabels(true)
        vm.setFullscreen(false)
        val restored = TrendViewModel(
            repo, FakeSettingsRepository(), { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today),
            SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        )
        assertEquals(TrendMetricType.SYSTOLIC, restored.uiState.value.metric)
        assertEquals(true, restored.uiState.value.showPulse)
        assertEquals(true, restored.uiState.value.fullscreenShowPulse)
        assertEquals(true, restored.uiState.value.showTimeLabels)
        assertEquals(false, restored.uiState.value.fullscreen)
    }

    @Test
    fun `旧每日范围状态恢复成原始双折线并保留选中记录与视窗`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val date = today.minusDays(1)
        val early = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val late = date.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val viewportStart = date.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        val viewportEnd = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val handle = SavedStateHandle(mapOf(
            "trend.aggregation" to TrendAggregation.DAILY_RANGE.name,
            "trend.selectedPointId" to "day:$date",
            "trend.selectedTimestamp" to late,
            "trend.viewportStart" to viewportStart,
            "trend.viewportEnd" to viewportEnd
        ))
        val repo = FakeTrendRepository().apply {
            records.value = listOf(record("early", early), record("late", late), record("future", now + 1))
        }
        // 在记录加载前再次重建，待恢复的时间映射也必须仍然存在。
        val pendingMigration = TrendViewModel(
            repo, FakeSettingsRepository(), { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today), handle
        )
        assertEquals(TrendAggregation.RAW, pendingMigration.uiState.value.aggregation)
        assertEquals(late, handle.get<Long>("trend.selectionMappingTimestamp"))
        val vm = TrendViewModel(
            repo, FakeSettingsRepository(), { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today), handle
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(TrendAggregation.RAW, vm.uiState.value.aggregation)
        assertEquals(TrendAggregation.RAW.name, handle.get<String>("trend.aggregation"))
        assertEquals(listOf("early", "late"), vm.uiState.value.series.points.map { it.id })
        assertEquals("late", vm.uiState.value.selectedPointId)
        assertEquals(false, vm.uiState.value.fullscreenShowPulse)
        assertEquals(true, vm.uiState.value.showPulse)
        vm.chartController.updateSeries(vm.uiState.value.series)
        val viewport = vm.chartController.viewportFor(vm.uiState.value.range)
        assertEquals(viewportStart, viewport.startMillis())
        assertEquals(viewportEnd, viewport.endMillis())

        // 废弃的调用不会重新启用每日范围；明细定位真实记录。
        vm.setAggregation(TrendAggregation.DAILY_RANGE)
        advanceUntilIdle()
        assertEquals(TrendAggregation.RAW, vm.uiState.value.series.aggregation)
        vm.openPointDetails(vm.uiState.value.series.points.first { it.id == "late" })
        advanceUntilIdle()
        assertEquals(listOf("early", "late"), vm.uiState.value.dayDetails?.records?.map { it.id })
        assertEquals("late", vm.uiState.value.dayDetails?.selectedRecordId)

        vm.setRange(TrendRange.DAYS_7)
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedPointId)
        assertNull(vm.uiState.value.dayDetails)
        assertNull(handle.get<Long>("trend.viewportStart"))
        assertNull(handle.get<Long>("trend.viewportEnd"))
        vm.selectPrevious()
        advanceUntilIdle()
        assertEquals("early", vm.uiState.value.selectedPointId)
        vm.selectNext()
        advanceUntilIdle()
        assertEquals("late", vm.uiState.value.selectedPointId)
        assertNull(handle.get<Long>("trend.selectionMappingTimestamp"))
    }

    @Test
    fun `旧每日平均状态也回退原始测量且副图选择单独保存`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "trend.aggregation" to TrendAggregation.DAILY.name,
            "trend.showPulse" to true,
            "trend.fullscreenShowPulse" to true,
            "trend.showTimeLabels" to true
        ))
        val vm = TrendViewModel(FakeTrendRepository(), FakeSettingsRepository(), savedStateHandle = handle)
        assertEquals(TrendAggregation.RAW, vm.uiState.value.aggregation)
        assertEquals(TrendAggregation.RAW.name, handle.get<String>("trend.aggregation"))
        assertEquals(true, vm.uiState.value.fullscreenShowPulse)
        assertEquals(true, vm.uiState.value.showTimeLabels)
        vm.setFullscreenShowPulse(false)
        assertEquals(false, handle.get<Boolean>("trend.fullscreenShowPulse"))
        assertEquals(true, handle.get<Boolean>("trend.showPulse"))
    }

    private fun record(id: String, timestamp: Long) = TrendRecord(
        id = id, measuredAt = timestamp, systolic = 128, diastolic = 82,
        pulse = null, category = "NORMAL"
    )

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
        var extraDetailRecords: List<TrendRecord> = emptyList()

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
        ): List<TrendRecord> = (records.value + extraDetailRecords).filter {
            it.measuredAt >= startInclusive && it.measuredAt < endExclusive
        }
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
