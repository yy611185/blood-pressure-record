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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CompletableDeferred
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
    fun `每日平均状态保留且副图选择单独保存`() = runTest {
        val handle = SavedStateHandle(mapOf(
            "trend.aggregation" to TrendAggregation.DAILY.name,
            "trend.showPulse" to true,
            "trend.fullscreenShowPulse" to true,
            "trend.showTimeLabels" to true
        ))
        val vm = TrendViewModel(FakeTrendRepository(), FakeSettingsRepository(), savedStateHandle = handle)
        assertEquals(TrendAggregation.DAILY, vm.uiState.value.aggregation)
        assertEquals(TrendAggregation.DAILY.name, handle.get<String>("trend.aggregation"))
        assertEquals(true, vm.uiState.value.fullscreenShowPulse)
        assertEquals(true, vm.uiState.value.showTimeLabels)
        vm.setFullscreenShowPulse(false)
        assertEquals(false, handle.get<Boolean>("trend.fullscreenShowPulse"))
        assertEquals(true, handle.get<Boolean>("trend.showPulse"))
    }

    @Test
    fun `六种范围粒度组合复用Session统计且主动偏好跨页面保留`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val previous = today.minusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val repo = FakeTrendRepository().apply {
            records.value = listOf(
                record("a", previous).copy(systolic = 120, pulse = 60),
                record("b", previous + 1000).copy(systolic = 150),
                record("c", now - 1000).copy(systolic = 120, pulse = 80)
            )
        }
        val settings = FakeSettingsRepository()
        val vm = TrendViewModel(repo, settings, { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(TrendAggregation.DAILY, vm.uiState.value.aggregation)
        vm.setRange(TrendRange.DAYS_7)
        advanceUntilIdle()
        assertEquals(TrendAggregation.RAW, vm.uiState.value.aggregation)
        vm.setRange(TrendRange.ALL)
        advanceUntilIdle()
        assertEquals(TrendAggregation.DAILY, vm.uiState.value.aggregation)

        // 主动点击当前默认 DAILY 也应持久化，而非仍随范围改变。
        vm.setAggregation(TrendAggregation.DAILY)
        vm.setRange(TrendRange.DAYS_7)
        advanceUntilIdle()
        assertEquals(TrendAggregation.DAILY, vm.uiState.value.aggregation)
        assertEquals(TrendAggregation.DAILY, settings.aggregationPreference.value)
        val reentered = TrendViewModel(repo, settings, { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reentered.uiState.collect {} }
        advanceUntilIdle()
        reentered.setRange(TrendRange.DAYS_7)
        advanceUntilIdle()
        assertEquals(TrendAggregation.DAILY, reentered.uiState.value.aggregation)

        for (aggregation in listOf(TrendAggregation.RAW, TrendAggregation.DAILY)) {
            vm.setAggregation(aggregation)
            for (range in TrendRange.entries) {
                vm.setRange(range)
                advanceUntilIdle()
                val state = vm.uiState.value
                assertEquals(range, state.range)
                assertEquals(aggregation, state.aggregation)
                assertEquals(aggregation, state.series.aggregation)
                assertEquals(if (aggregation == TrendAggregation.RAW) 3 else 2, state.series.points.size)
                assertEquals(130, state.series.averageSystolic)
                assertEquals(70, state.series.averagePulse)
                assertEquals(130 to 82, state.insights.periodAverage)
                if (aggregation == TrendAggregation.DAILY) {
                    assertEquals(135, state.series.points.first().systolic)
                    assertEquals(60, state.series.points.first().pulse)
                    assertEquals(2, state.series.points.first().recordCount)
                    assertEquals(1, state.series.points.first().pulseRecordCount)
                }
            }
        }
    }

    @Test
    fun `迟到的持久偏好不覆盖主动选择且旋转恢复优先`() = runTest {
        val settings = FakeSettingsRepository().apply {
            aggregationPreference.value = TrendAggregation.DAILY
            preferenceGate = CompletableDeferred()
        }
        val handle = SavedStateHandle()
        val vm = TrendViewModel(FakeTrendRepository(), settings,
            computeContext = UnconfinedTestDispatcher(testScheduler), savedStateHandle = handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        // 先让仓库捕获旧 DAILY 值并阻塞首读，再在读完前主动选择 RAW。
        advanceUntilIdle()
        vm.setAggregation(TrendAggregation.RAW)
        settings.preferenceGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(TrendAggregation.RAW, vm.uiState.value.aggregation)
        assertEquals(TrendAggregation.RAW, settings.aggregationPreference.value)
        // 模拟恢复时尚未看到最新落盘值；SavedState 的明确选择优先。
        settings.aggregationPreference.value = TrendAggregation.DAILY
        val restored = TrendViewModel(FakeTrendRepository(), settings,
            computeContext = UnconfinedTestDispatcher(testScheduler), savedStateHandle =
            SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(TrendAggregation.RAW, restored.uiState.value.aggregation)
    }

    @Test
    fun `每日平均往返恢复同时间的原Session并保持视窗与明细来源`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val date = today.minusDays(2)
        val timestamp = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val start = timestamp - 60 * 60 * 1000L
        val end = timestamp + 60 * 60 * 1000L
        val handle = SavedStateHandle(mapOf("trend.viewportStart" to start, "trend.viewportEnd" to end))
        val repo = FakeTrendRepository().apply {
            records.value = listOf(record("same-a", timestamp), record("same-b", timestamp))
            extraDetailRecords = listOf(record("not-in-series", timestamp))
        }
        val settings = FakeSettingsRepository()
        val vm = TrendViewModel(repo, settings, { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today), handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setAggregation(TrendAggregation.RAW)
        advanceUntilIdle()
        vm.chartController.updateSeries(vm.uiState.value.series)
        vm.selectPoint(vm.uiState.value.series.points.first { it.id == "same-b" })
        vm.setAggregation(TrendAggregation.DAILY)
        advanceUntilIdle()
        assertEquals("day:$date", vm.uiState.value.selectedPointId)
        vm.chartController.updateSeries(vm.uiState.value.series)
        vm.openPointDetails(vm.uiState.value.series.points.single())
        advanceUntilIdle()
        assertEquals("$date 每日平均记录", vm.uiState.value.dayDetails?.title)
        assertEquals(listOf("same-a", "same-b"), vm.uiState.value.dayDetails?.records?.map { it.id })
        assertNull(vm.uiState.value.dayDetails?.selectedRecordId)

        // DAILY 页面重建后仍记住原始 ID，不能只靠相同时间的最近点恢复。
        val restored = TrendViewModel(repo, settings, { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today),
            SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect {} }
        advanceUntilIdle()
        restored.chartController.updateSeries(restored.uiState.value.series)
        restored.setAggregation(TrendAggregation.RAW)
        advanceUntilIdle()
        restored.chartController.updateSeries(restored.uiState.value.series)
        assertEquals("same-b", restored.uiState.value.selectedPointId)
        val viewport = restored.chartController.viewportFor(restored.uiState.value.range)
        assertEquals(start, viewport.startMillis())
        assertEquals(end, viewport.endMillis())
        assertEquals(start, handle.get<Long>("trend.viewportStart"))
        assertEquals(end, handle.get<Long>("trend.viewportEnd"))
        restored.setAggregation(TrendAggregation.DAILY)
        advanceUntilIdle()
        repo.records.value = repo.records.value.filter { it.id != "same-b" }
        advanceUntilIdle()
        restored.setAggregation(TrendAggregation.RAW)
        advanceUntilIdle()
        assertEquals("same-a", restored.uiState.value.selectedPointId)
    }

    @Test
    fun `窄视窗聚合往返只平移到映射节点且保持跨度与原Session`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val date = today.minusDays(2)
        val early = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val late = date.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val latest = today.atTime(11, 0).atZone(zone).toInstant().toEpochMilli()
        val hour = 60 * 60 * 1000L
        val handle = SavedStateHandle(mapOf(
            "trend.viewportStart" to early - hour,
            "trend.viewportEnd" to early + hour
        ))
        val repo = FakeTrendRepository().apply {
            records.value = listOf(record("early", early), record("late", late), record("latest", latest))
        }
        val settings = FakeSettingsRepository()
        val vm = TrendViewModel(repo, settings, { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today), handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setAggregation(TrendAggregation.RAW)
        advanceUntilIdle()
        vm.selectPoint(vm.uiState.value.series.points.first { it.id == "early" })
        // 尚未由图表初始化 Controller 时也必须先恢复 08–10 的窗口，再平移。
        vm.setAggregation(TrendAggregation.DAILY)
        advanceUntilIdle()
        val daily = vm.uiState.value.series.points.first { it.id == "day:$date" }
        val dailyViewport = vm.chartController.viewportFor(vm.uiState.value.range)
        assertEquals("day:$date", vm.uiState.value.selectedPointId)
        assertEquals(2 * hour, dailyViewport.endMillis() - dailyViewport.startMillis())
        assertTrue(daily.timestamp in dailyViewport.startMillis()..dailyViewport.endMillis())
        assertTrue(dailyViewport.endMillis() < latest)
        assertEquals(dailyViewport.startMillis(), handle.get<Long>("trend.viewportStart"))
        assertEquals(dailyViewport.endMillis(), handle.get<Long>("trend.viewportEnd"))

        val restored = TrendViewModel(repo, settings, { now }, zone,
            UnconfinedTestDispatcher(testScheduler), flowOf(today),
            SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect {} }
        advanceUntilIdle()
        // 重建后的 Controller 也尚未初始化，切回 RAW 恢复 early 而非跳至 latest。
        restored.setAggregation(TrendAggregation.RAW)
        advanceUntilIdle()
        val rawViewport = restored.chartController.viewportFor(restored.uiState.value.range)
        assertEquals("early", restored.uiState.value.selectedPointId)
        assertEquals(2 * hour, rawViewport.endMillis() - rawViewport.startMillis())
        assertTrue(early in rawViewport.startMillis()..rawViewport.endMillis())
        assertTrue(rawViewport.endMillis() < latest)
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

        // 默认 30 天按自然日聚合，新增记录仍立即进入当日节点。
        assertEquals(listOf("day:$today"), vm.uiState.value.series.points.map { it.id })
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
        val aggregationPreference = MutableStateFlow<TrendAggregation?>(null)
        var preferenceGate: CompletableDeferred<Unit>? = null
        override fun observeTrendAggregationPreference(): Flow<TrendAggregation?> = flow {
            val initial = aggregationPreference.value
            preferenceGate?.await()
            emit(initial)
        }
        override suspend fun setTrendAggregationPreference(aggregation: TrendAggregation) {
            aggregationPreference.value = aggregation
        }
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
