package com.example.bloodpressurerecord.ui.home

import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.BpZoneConfigRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.data.repository.SessionReading
import com.example.bloodpressurerecord.data.db.dao.MedicationWithTimes
import com.example.bloodpressurerecord.domain.model.BpZone
import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import com.example.bloodpressurerecord.domain.model.BpZoneCuts
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import com.example.bloodpressurerecord.ui.settings.FakeTargetSettingsRepository
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun `dashboard waits for first persisted config`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val latest = latestSummary(record("reading", today, 8, 0, ""))
            .copy(avgSystolic = 119, avgDiastolic = 75)
        val delayedConfig = MutableSharedFlow<BpZoneConfig>()
        val custom = BpZoneConfig.Default.copy(
            sys = BpZoneConfig.Default.sys.copy(cuts = BpZoneCuts(90, 110, 130))
        )
        val vm = DashboardViewModel(
            FakeRepository(emptyMap(), latestFlow = flowOf(latest)),
            zoneId = zone,
            todayTicks = flowOf(today),
            zoneConfigRepository = FakeZoneConfigRepository(observedConfig = delayedConfig)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.bpRangeState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.loading)
        assertEquals(BpRangeUiState.Empty, vm.bpRangeState.value)

        delayedConfig.emit(custom)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.loading)
        assertEquals(BpZone.ELEVATED, vm.bpRangeState.value.zone)
    }

    @Test
    fun `range follows empty ready and deleted readings using one dashboard snapshot`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val latest = MutableStateFlow<LatestSessionSummary?>(null)
        val configRepo = FakeZoneConfigRepository()
        val vm = DashboardViewModel(
            FakeRepository(emptyMap(), latestFlow = latest),
            zoneId = zone,
            todayTicks = flowOf(today),
            zoneConfigRepository = configRepo
        )
        val emissions = mutableListOf<BpRangeUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.bpRangeState.collect { emissions += it }
        }
        advanceUntilIdle()
        assertEquals(BpRangeUiState.Empty, vm.bpRangeState.value)

        latest.value = latestSummary(record("newest", today, 20, 0, ""))
            .copy(avgSystolic = 119, avgDiastolic = 75)
        advanceUntilIdle()
        val ready = vm.bpRangeState.value as BpRangeUiState.Ready
        assertEquals(119, ready.sys)
        assertEquals(75, ready.dia)
        assertEquals(BpZone.NORMAL, ready.zone)
        assertEquals(R.string.bp_zone_normal, ready.zoneLabelRes)
        assertEquals(ready, vm.uiState.value.bpRange)
        assertEquals(vm.uiState.value.latest?.avgSystolic, ready.sys)
        assertEquals(vm.uiState.value.latest?.avgDiastolic, ready.dia)

        latest.value = null
        advanceUntilIdle()
        assertEquals(BpRangeUiState.Empty, vm.bpRangeState.value)
        assertEquals(listOf(BpRangeUiState.Empty, ready, BpRangeUiState.Empty), emissions)
    }

    @Test
    fun `saving a target config from settings updates range without changing the latest reading`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val latest = latestSummary(record("reading", today, 8, 0, ""))
            .copy(avgSystolic = 119, avgDiastolic = 75)
        val configRepo = FakeZoneConfigRepository()
        val vm = DashboardViewModel(
            FakeRepository(emptyMap(), latestFlow = flowOf(latest)),
            zoneId = zone,
            todayTicks = flowOf(today),
            zoneConfigRepository = configRepo
        )
        // 只订阅色条即可激活首页与目标配置；无需额外订阅 uiState。
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.bpRangeState.collect {} }
        // 公开配置也是 WhileSubscribed，订阅后获得最新配置。
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.zoneConfig.collect {} }
        advanceUntilIdle()
        val before = vm.bpRangeState.value as BpRangeUiState.Ready
        assertEquals(BpZone.NORMAL, before.zone)

        val settings = SettingsViewModel(FakeTargetSettingsRepository(), zoneConfigRepository = configRepo)
        settings.openZoneTargetEditor()
        advanceUntilIdle()
        settings.updateZoneTargetField(BpZoneAxis.SYS, BpZoneField.NORMAL_CUT, "110")
        settings.updateZoneTargetField(BpZoneAxis.SYS, BpZoneField.HIGH_CUT, "130")
        settings.saveZoneTargetEditor()
        advanceUntilIdle()
        val after = vm.bpRangeState.value as BpRangeUiState.Ready
        assertEquals(BpZone.ELEVATED, after.zone)
        assertEquals(R.string.bp_zone_elevated, after.zoneLabelRes)
        assertTrue(after.position.percent > before.position.percent)
        assertEquals(latest, vm.uiState.value.latest)
        assertEquals(after, vm.uiState.value.bpRange)
        assertEquals(BpZoneCuts(90, 110, 130), vm.zoneConfig.value.sys.cuts)
        assertEquals(null, settings.zoneTargetEditorState.value)
    }

    @Test
    fun `invalid latest reading produces empty range`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val latest = MutableStateFlow<LatestSessionSummary?>(
            latestSummary(record("reading", today, 8, 0, "")).copy(avgSystolic = 0)
        )
        val vm = DashboardViewModel(
            FakeRepository(emptyMap(), latestFlow = latest),
            zoneId = zone,
            todayTicks = flowOf(today)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.bpRangeState.collect {} }
        advanceUntilIdle()
        assertEquals(BpRangeUiState.Empty, vm.bpRangeState.value)
        latest.value = latest.value?.copy(avgSystolic = 119, avgDiastolic = -1)
        advanceUntilIdle()
        assertEquals(BpRangeUiState.Empty, vm.bpRangeState.value)
        assertEquals(null, vm.bpRangeState.value.zone)
    }

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
        assertEquals(day2, vm.uiState.value.today)
        // 自然周随之滚动：7/26 是周日，本周固定为 7/26（周日）→ 8/1（周六）。
        assertEquals(LocalDate.of(2026, 7, 26), vm.uiState.value.week.first().date)
        assertEquals(LocalDate.of(2026, 8, 1), vm.uiState.value.week.last().date)
        assertTrue(vm.uiState.value.week.any { it.date == day2 })
        assertEquals(null, vm.uiState.value.todayMorning)
        assertEquals(null, vm.uiState.value.todayEvening)
    }

    @Test
    fun `today uses the last morning and evening session and week uses each day's mean`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val yesterday = today.minusDays(1)
        val repo = FakeRepository(
            countsByRangeStart = mapOf(today.toEpochMillisRange(zone).startInclusive to 4),
            summaries = listOf(
                summary("evening-first", today, 18, 30, 150, 90),
                summary("morning-last", today, 11, 45, 130, 85),
                summary("prior-day", yesterday, 8, 0, 110, 70),
                summary("evening-last", today, 22, 0, 140, 90),
                summary("morning-first", today, 7, 0, 120, 80)
            )
        )
        val vm = DashboardViewModel(repo, zoneId = zone, todayTicks = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("morning-last", state.todayMorning?.id)
        assertEquals("evening-last", state.todayEvening?.id)
        assertEquals(4, state.todayCount)
        // 「这一周」是固定自然周（周日→周六）：2026-07-25 是周六，本周日为 7/19，
        // 因此昨天（周五 7/24）落在索引 5，今天（周六 7/25）落在索引 6。
        assertEquals(7, state.week.size)
        assertEquals(LocalDate.of(2026, 7, 19), state.week.first().date)
        assertEquals(today, state.week.last().date)
        assertEquals(yesterday, state.week[5].date)
        assertEquals(110, state.week[5].averageSystolic)
        assertEquals(70, state.week[5].averageDiastolic)
        assertEquals(today, state.week[6].date)
        assertEquals(135, state.week[6].averageSystolic)
        assertEquals(86, state.week[6].averageDiastolic)
        assertTrue(state.week[6].recorded)
        assertEquals(null, state.week[4].averageSystolic)
        assertFalse(state.week[4].recorded)
    }

    @Test
    fun `week always starts on sunday and keeps a fixed natural week`() = runTest {
        // 2026-07-22 是周三，本周日应为 7/19，周六为 7/25。
        val today = LocalDate.of(2026, 7, 22)
        val repo = FakeRepository(countsByRangeStart = emptyMap())
        val vm = DashboardViewModel(repo, zoneId = zone, todayTicks = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        val week = vm.uiState.value.week
        assertEquals(7, week.size)
        assertEquals(
            listOf(19, 20, 21, 22, 23, 24, 25),
            week.map { it.date.dayOfMonth }
        )
        // 未来日期没有记录，也不应影响今天标记。
        assertEquals(today, week[3].date)
        assertFalse(week[6].recorded)
    }

    @Test
    fun `latest detail follows its summary id when latest record changes`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val earlier = record("earlier", today, 8, 0, "测试旧记录")
        val newest = record("newest", today, 20, 0, "测试新记录")
        val latest = MutableStateFlow<LatestSessionSummary?>(latestSummary(earlier))
        val earlierFlow = MutableStateFlow<SessionRecord?>(earlier)
        val newestFlow = MutableStateFlow<SessionRecord?>(newest)
        val repo = FakeRepository(
            countsByRangeStart = emptyMap(),
            latestFlow = latest,
            sessionFlows = mapOf("earlier" to earlierFlow, "newest" to newestFlow)
        )
        val vm = DashboardViewModel(repo, zoneId = zone, todayTicks = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("earlier", vm.uiState.value.latest?.id)
        assertEquals("测试旧记录", vm.uiState.value.latestSession?.note)

        latest.value = latestSummary(newest)
        advanceUntilIdle()
        assertEquals("newest", vm.uiState.value.latest?.id)
        assertEquals("newest", vm.uiState.value.latestSession?.id)
        assertEquals("测试新记录", vm.uiState.value.latestSession?.note)

        earlierFlow.value = earlier.copy(note = "过期详情不应覆盖最新记录")
        advanceUntilIdle()
        assertEquals("newest", vm.uiState.value.latestSession?.id)
        assertEquals("测试新记录", vm.uiState.value.latestSession?.note)
    }

    @Test
    fun `rapid medication clicks are serialized and expose a pending id`() = runTest {
        val today = LocalDate.of(2026, 7, 25)
        val medication = BlockingMedicationRepository()
        val vm = DashboardViewModel(
            repository = FakeRepository(emptyMap()),
            medicationRepository = medication,
            zoneId = zone,
            todayTicks = flowOf(today)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        val slot = MedicationSlot(
            medicationId = 1L,
            timeId = 2L,
            name = "测试药物",
            dosage = "1 片",
            timeText = "08:00",
            taken = false
        )
        vm.toggleMedicationTaken(slot, true)
        runCurrent()
        medication.firstCallStarted.await()
        assertTrue(vm.uiState.value.pendingMedicationTimeIds.contains(slot.timeId))

        vm.toggleMedicationTaken(slot, false)
        runCurrent()
        assertEquals(listOf(true), medication.takenValues)
        assertEquals(1, medication.maxActiveCalls)

        medication.releaseFirstCall.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(true, false), medication.takenValues)
        assertEquals(1, medication.maxActiveCalls)
        assertFalse(vm.uiState.value.pendingMedicationTimeIds.contains(slot.timeId))
    }

    private fun summary(
        id: String,
        day: LocalDate,
        hour: Int,
        minute: Int,
        systolic: Int,
        diastolic: Int
    ) = SessionSummary(
        id = id,
        measuredAt = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli(),
        avgSystolic = systolic,
        avgDiastolic = diastolic,
        avgPulse = 70,
        category = "NORMAL",
        scene = "MORNING",
        noteSummary = null,
        containsHighRiskReading = false
    )

    private fun record(id: String, day: LocalDate, hour: Int, minute: Int, note: String) =
        SessionRecord(
            id = id,
            measuredAt = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli(),
            scene = "MORNING",
            note = note,
            symptoms = emptyList(),
            avgSystolic = 135,
            avgDiastolic = 85,
            avgPulse = 70,
            category = "NORMAL",
            containsHighRiskReading = false,
            readings = listOf(SessionReading("$id-reading", 1, 135, 85, 70))
        )

    private fun latestSummary(record: SessionRecord) = LatestSessionSummary(
        id = record.id,
        measuredAt = record.measuredAt,
        avgSystolic = record.avgSystolic,
        avgDiastolic = record.avgDiastolic,
        category = record.category,
        containsHighRiskReading = record.containsHighRiskReading
    )

    private class FakeRepository(
        private val countsByRangeStart: Map<Long, Int>,
        private val summaries: List<SessionSummary> = emptyList(),
        private val latestFlow: Flow<LatestSessionSummary?> = flowOf(null),
        private val sessionFlows: Map<String, Flow<SessionRecord?>> = emptyMap()
    ) : BloodPressureRepository {
        override fun observeSession(sessionId: String): Flow<SessionRecord?> =
            sessionFlows[sessionId] ?: flowOf(null)
        override fun observeLatestSessionSummary(): Flow<LatestSessionSummary?> = latestFlow
        override fun observeCalendarSessionSummaries(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<List<CalendarSessionSummary>> = flowOf(
            summaries.filter { it.measuredAt in startInclusive until endExclusive }.map {
                CalendarSessionSummary(it.measuredAt, it.noteSummary, it.containsHighRiskReading)
            }
        )
        override fun observeSessionSummariesInRange(
            startInclusive: Long,
            endExclusive: Long
        ): Flow<List<SessionSummary>> = flowOf(
            summaries.filter { it.measuredAt in startInclusive until endExclusive }
        )
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

    private class FakeZoneConfigRepository(
        initial: BpZoneConfig = BpZoneConfig.Default,
        private val observedConfig: Flow<BpZoneConfig>? = null
    ) : BpZoneConfigRepository {
        private val config = MutableStateFlow(initial)
        var saveResult: Result<Unit> = Result.success(Unit)
        var saveGate: CompletableDeferred<Unit>? = null
        var saveCalls = 0

        override fun observeConfig(): Flow<BpZoneConfig> = observedConfig ?: config

        override suspend fun saveConfig(config: BpZoneConfig): Result<Unit> {
            saveCalls += 1
            if (!config.isValid()) return Result.failure(IllegalArgumentException("Invalid target config"))
            saveGate?.await()
            if (saveResult.isSuccess) this.config.value = config
            return saveResult
        }
    }

    private class BlockingMedicationRepository : MedicationRepository {
        val firstCallStarted = CompletableDeferred<Unit>()
        val releaseFirstCall = CompletableDeferred<Unit>()
        val takenValues = mutableListOf<Boolean>()
        var activeCalls = 0
        var maxActiveCalls = 0

        override fun observeMedicationsWithTimes(): Flow<List<MedicationWithTimes>> = flowOf(emptyList())

        override fun observeSlotsForDay(date: LocalDate): Flow<List<MedicationSlot>> = flowOf(emptyList())

        override suspend fun getSlotsForDay(date: LocalDate): List<MedicationSlot> = emptyList()

        override suspend fun setTaken(
            medicationId: Long,
            timeId: Long,
            date: LocalDate,
            taken: Boolean
        ) {
            activeCalls += 1
            maxActiveCalls = maxOf(maxActiveCalls, activeCalls)
            takenValues += taken
            try {
                if (takenValues.size == 1) {
                    firstCallStarted.complete(Unit)
                    releaseFirstCall.await()
                }
            } finally {
                activeCalls -= 1
            }
        }

        override suspend fun addMedication(
            name: String,
            dosage: String,
            times: List<String>
        ): Result<Long> = Result.success(1L)

        override suspend fun updateMedication(
            id: Long,
            name: String,
            dosage: String,
            enabled: Boolean,
            times: List<String>
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteMedication(id: Long): Result<Unit> = Result.success(Unit)
    }
}
