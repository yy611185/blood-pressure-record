package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.SavedStateHandle
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.NoteSearchResult
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionReading
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.ui.common.SessionFormLogic
import com.example.bloodpressurerecord.ui.common.RestoreExtraReadingResult
import com.example.bloodpressurerecord.ui.home.MainDispatcherRule
import com.example.bloodpressurerecord.update.UpdatePromptGate
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditSessionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = DateTimeInputFormatter.parse("2026-10-12 10:00")!!

    @Test
    fun `retains persisted representative values and DISCARD_FIRST strategy plus legacy note`() = runTest {
        // 第1组偏高(150/95)，后两组均为 120/80，保存策略为 DISCARD_FIRST -> 已持久化均值 120/80
        val record = SessionRecord(
            id = "session-discard-first",
            measuredAt = fixedNow - 3_600_000L,
            scene = "晨间",
            note = "旧版手写备注",
            symptoms = listOf("头晕"),
            avgSystolic = 120,
            avgDiastolic = 80,
            avgPulse = 70,
            category = "NORMAL",
            containsHighRiskReading = false,
            readings = listOf(
                SessionReading("r1", 0, 150, 95, 76),
                SessionReading("r2", 1, 120, 80, 70),
                SessionReading("r3", 2, 120, 80, 70)
            ),
            averageStrategy = AverageStrategy.DISCARD_FIRST,
            symptomNote = "轻微不适",
            factorNote = "没睡好"
        )
        val repo = FakeEditBloodPressureRepository(record)
        val vm = EditSessionViewModel(
            sessionId = record.id,
            repository = repo,
            nowMillis = { fixedNow }
        )
        advanceUntilIdle()

        val initial = vm.uiState.value
        assertFalse(initial.loading)
        assertTrue(initial.hasLegacyNote)
        assertEquals("旧版手写备注", initial.note)
        assertEquals(120, initial.avgSystolic)
        assertEquals(80, initial.avgDiastolic)
        assertEquals(1, initial.extraReadings.size)

        // 修改第3组为 130/84，仍按原记录 DISCARD_FIRST 计算：(120+130)/2 = 125, (80+84)/2 = 82
        vm.updateExtraReadingSystolic(vm.uiState.value.extraReadings[0].editorId, "130")
        vm.updateExtraReadingDiastolic(vm.uiState.value.extraReadings[0].editorId, "84")
        assertEquals(125, vm.uiState.value.avgSystolic)
        assertEquals(82, vm.uiState.value.avgDiastolic)

        vm.updateNote("旧版手写备注（已补充）")
        vm.onSaveClicked()
        advanceUntilIdle()

        val savedInput = requireNotNull(repo.lastUpdatedInput)
        assertEquals(AverageStrategy.DISCARD_FIRST, savedInput.averageStrategy)
        assertEquals("旧版手写备注（已补充）", savedInput.note)
        assertEquals(listOf("头晕"), savedInput.symptoms)
        assertTrue(vm.consumeSavedSession())
        // 二次消费必须返回 false，防止返回竞态双次 pop
        assertFalse(vm.consumeSavedSession())
    }

    @Test
    fun `highRiskAlertEnabled gates high risk confirmation dialog`() = runTest {
        val baseRecord = sampleTwoReadingSession()
        val repoEnabled = FakeEditBloodPressureRepository(baseRecord)
        val vmEnabled = EditSessionViewModel(
            sessionId = baseRecord.id,
            repository = repoEnabled,
            nowMillis = { fixedNow },
            highRiskAlertEnabled = flowOf(true)
        )
        advanceUntilIdle()
        vmEnabled.updateReading1Systolic("182")
        vmEnabled.updateReading1Diastolic("110")
        vmEnabled.onSaveClicked()
        assertTrue(vmEnabled.uiState.value.showHighRiskDialog)
        assertEquals(null, repoEnabled.lastUpdatedInput)
        vmEnabled.confirmHighRiskAndSave()
        advanceUntilIdle()
        assertNotNull(repoEnabled.lastUpdatedInput)

        val repoDisabled = FakeEditBloodPressureRepository(baseRecord)
        val vmDisabled = EditSessionViewModel(
            sessionId = baseRecord.id,
            repository = repoDisabled,
            nowMillis = { fixedNow },
            highRiskAlertEnabled = flowOf(false)
        )
        advanceUntilIdle()
        vmDisabled.updateReading1Systolic("182")
        vmDisabled.updateReading1Diastolic("110")
        vmDisabled.onSaveClicked()
        advanceUntilIdle()
        assertFalse(vmDisabled.uiState.value.showHighRiskDialog)
        assertNotNull(repoDisabled.lastUpdatedInput)
    }

    @Test
    fun `locks form edits and holds update gate during save and handles save failure`() = runTest {
        val gate = CompletableDeferred<Result<Unit>>()
        val promptGate = UpdatePromptGate()
        val repo = FakeEditBloodPressureRepository(sampleTwoReadingSession(), updateDeferred = gate)
        val vm = EditSessionViewModel(
            sessionId = "s-1",
            repository = repo,
            savedStateHandle = SavedStateHandle(),
            nowMillis = { fixedNow },
            updatePromptGate = promptGate
        )
        advanceUntilIdle()

        vm.updateReading1Systolic("126")
        vm.onSaveClicked()
        runCurrent()

        assertTrue(vm.uiState.value.isSaving)
        assertTrue(promptGate.busy.value)

        // 保存中修改表单不会污染状态
        vm.updateReading1Systolic("166")
        vm.updateSymptomNote("保存中误触")
        assertEquals("126", vm.uiState.value.reading1.systolic)
        assertEquals("", vm.uiState.value.symptomNote)

        gate.complete(Result.failure(IllegalStateException("磁盘只读")))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSaving)
        assertFalse(promptGate.busy.value)
        assertFalse(vm.uiState.value.saved)
        assertTrue(vm.uiState.value.isDirty)
        assertTrue(vm.uiState.value.message.contains("保存失败"))
    }

    @Test
    fun `restoreExtraReading rejects exceeding 20 groups with explicit message`() = runTest {
        val repo = FakeEditBloodPressureRepository(sampleTwoReadingSession())
        val vm = EditSessionViewModel(
            sessionId = "s-1",
            repository = repo,
            nowMillis = { fixedNow }
        )
        advanceUntilIdle()

        repeat(SessionFormLogic.UI_MAX_READING_COUNT - 2) {
            vm.addNextReadingGroup()
        }
        assertEquals(18, vm.uiState.value.extraReadings.size)

        val editorId = vm.uiState.value.extraReadings.first().editorId
        vm.updateExtraReadingSystolic(editorId, "122")
        val removed = requireNotNull(vm.removeExtraReading(editorId))
        assertEquals(17, vm.uiState.value.extraReadings.size)

        // 用户补满第 20 组后再点旧撤销
        vm.addNextReadingGroup()
        assertEquals(18, vm.uiState.value.extraReadings.size)

        assertEquals(RestoreExtraReadingResult.AT_LIMIT, vm.restoreExtraReading(removed))
        assertTrue(vm.uiState.value.message.contains("上限"))

        // 删除一组腾出空间后即可成功恢复
        vm.removeExtraReading(vm.uiState.value.extraReadings.last().editorId)
        assertEquals(RestoreExtraReadingResult.RESTORED, vm.restoreExtraReading(removed))
        assertEquals(editorId, vm.uiState.value.extraReadings.first().editorId)
        assertEquals("122", vm.uiState.value.extraReadings.first().systolic)
    }

    private fun sampleTwoReadingSession() = SessionRecord(
        id = "s-1",
        measuredAt = fixedNow - 1_800_000L,
        scene = "晨间",
        note = null,
        symptoms = emptyList(),
        avgSystolic = 120,
        avgDiastolic = 78,
        avgPulse = 68,
        category = "NORMAL",
        containsHighRiskReading = false,
        readings = listOf(
            SessionReading("r1", 0, 120, 78, 68),
            SessionReading("r2", 1, 120, 78, 68)
        ),
        averageStrategy = AverageStrategy.ALL
    )

    private class FakeEditBloodPressureRepository(
        initial: SessionRecord,
        private val updateDeferred: CompletableDeferred<Result<Unit>>? = null
    ) : BloodPressureRepository {
        private val sessionFlow = MutableStateFlow<SessionRecord?>(initial)
        var lastUpdatedInput: SaveSessionInput? = null

        override fun observeSession(sessionId: String): Flow<SessionRecord?> = sessionFlow
        override fun observeLatestSessionSummary(): Flow<LatestSessionSummary?> = flowOf(null)
        override fun observeCalendarSessionSummaries(startInclusive: Long, endExclusive: Long): Flow<List<CalendarSessionSummary>> = flowOf(emptyList())
        override fun observeSessionSummariesInRange(startInclusive: Long, endExclusive: Long): Flow<List<SessionSummary>> = flowOf(emptyList())
        override fun observeNoteSearchResults(keyword: String): Flow<List<NoteSearchResult>> = flowOf(emptyList())
        override fun observePeriodStatistics(startInclusive: Long, endExclusive: Long): Flow<PeriodStatistics> = flowOf(PeriodStatistics())
        override suspend fun saveSession(input: SaveSessionInput): Result<String> = Result.success("new-id")

        override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> {
            val res = updateDeferred?.await() ?: Result.success(Unit)
            if (res.isSuccess) {
                lastUpdatedInput = input
            }
            return res
        }

        override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession(session: SessionRecord): Result<Unit> = Result.success(Unit)
    }
}
