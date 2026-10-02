package com.example.bloodpressurerecord.ui.home

import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.domain.time.MeasurementTimestampValidator
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelDynamicTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun dynamic_readings_are_saved() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo)

        vm.updateReading1Systolic("120")
        vm.updateReading1Diastolic("80")
        vm.updateReading2Systolic("125")
        vm.updateReading2Diastolic("82")

        vm.addNextReadingGroup()
        vm.updateExtraReadingSystolic(0, "128")
        vm.updateExtraReadingDiastolic(0, "84")
        vm.addNextReadingGroup()
        vm.updateExtraReadingSystolic(1, "130")
        vm.updateExtraReadingDiastolic(1, "85")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(1, repo.savedCount)
        assertEquals(4, repo.lastInput?.readings?.size)
    }

    @Test
    fun collapse_extra_readings_clears_extra_data() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo)
        vm.addNextReadingGroup()
        vm.updateExtraReadingSystolic(0, "130")
        vm.removeExtraReading(0)
        assertTrue(vm.uiState.value.extraReadings.isEmpty())
    }

    @Test
    fun expand_third_group_initializes_dynamic_list() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo)
        vm.addNextReadingGroup()
        assertEquals(1, vm.uiState.value.extraReadings.size)
    }

    @Test
    fun disabledHighRiskAlertSavesWithoutShowingRiskDialog() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo, highRiskAlertEnabled = flowOf(false))
        advanceUntilIdle()
        vm.updateReading1Systolic("181")
        vm.updateReading1Diastolic("80")
        vm.updateReading2Systolic("181")
        vm.updateReading2Diastolic("80")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(1, repo.savedCount)
        assertTrue(!vm.uiState.value.showHighRiskDialog)
    }

    @Test
    fun enabledHighRiskAlertWaitsForExplicitConfirmation() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo, highRiskAlertEnabled = flowOf(true))
        vm.updateReading1Systolic("181")
        vm.updateReading1Diastolic("80")
        vm.updateReading2Systolic("181")
        vm.updateReading2Diastolic("80")

        vm.onSaveClicked()
        advanceUntilIdle()
        assertEquals(0, repo.savedCount)
        assertTrue(vm.uiState.value.showHighRiskDialog)

        vm.confirmHighRiskAndSave()
        advanceUntilIdle()
        assertEquals(1, repo.savedCount)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun saveFailureMarksErrorMessageAndDoesNotNavigate() = runTest {
        val repo = FakeRepository(failSave = true)
        val vm = HomeViewModel(repo)
        vm.updateReading1Systolic("120")
        vm.updateReading1Diastolic("80")
        vm.updateReading2Systolic("125")
        vm.updateReading2Diastolic("82")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertTrue(!vm.uiState.value.saved)
        assertTrue(vm.uiState.value.formMessageIsError)
        assertTrue(vm.uiState.value.formMessage.startsWith("保存失败"))
    }

    @Test
    fun future_measurement_time_is_rejected_before_save() = runTest {
        val fixedNow = Instant.parse("2026-08-06T04:00:00Z").toEpochMilli()
        val repo = FakeRepository()
        val vm = HomeViewModel(repo, nowMillis = { fixedNow })
        vm.updateMeasuredAtText(
            DateTimeInputFormatter.format(
                fixedNow + MeasurementTimestampValidator.FUTURE_TOLERANCE_MILLIS + 60_000
            )
        )
        vm.updateReading1Systolic("120")
        vm.updateReading1Diastolic("80")
        vm.updateReading2Systolic("125")
        vm.updateReading2Diastolic("82")

        vm.onSaveClicked()

        assertEquals(0, repo.savedCount)
        assertEquals(
            MeasurementTimestampValidator.FUTURE_MEASUREMENT_TIME_MESSAGE,
            vm.uiState.value.formMessage
        )
        assertTrue(vm.uiState.value.formMessageIsError)
    }

    @Test
    fun time_period_always_follows_measured_time() = runTest {
        val vm = HomeViewModel(FakeRepository())

        vm.updateMeasuredAtText("2026-07-26 07:30")
        assertEquals("清晨", vm.uiState.value.scene)
        vm.updateMeasuredAtText("2026-07-26 10:00")
        assertEquals("上午", vm.uiState.value.scene)
        vm.updateMeasuredAtText("2026-07-26 13:00")
        assertEquals("下午", vm.uiState.value.scene)
        vm.updateMeasuredAtText("2026-07-26 20:00")
        assertEquals("晚上", vm.uiState.value.scene)
        vm.updateMeasuredAtText("2026-07-26 02:00")
        assertEquals("凌晨", vm.uiState.value.scene)

    }

    @Test
    fun free_notes_are_separate_and_average_uses_all_readings() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo)
        fillReadings(vm)
        vm.updateSymptomNote("有点头晕")
        vm.updateFactorNote("昨晚没睡好")
        vm.onSaveClicked()
        advanceUntilIdle()
        assertEquals("有点头晕", repo.lastInput?.symptomNote)
        assertEquals("昨晚没睡好", repo.lastInput?.factorNote)
        assertTrue(repo.lastInput!!.symptoms.isEmpty())
        assertEquals(com.example.bloodpressurerecord.domain.model.AverageStrategy.ALL, repo.lastInput?.averageStrategy)
        assertEquals(125, vm.uiState.value.avgSystolic)
        assertEquals(80, vm.uiState.value.avgDiastolic)
    }

    @Test
    fun saved_record_edit_and_repeated_click_update_same_session() = runTest {
        val repo = FakeRepository()
        val handle = androidx.lifecycle.SavedStateHandle()
        val drafts = MemoryDraftRepository()
        val vm = HomeViewModel(repo, savedStateHandle = handle, draftRepository = drafts)
        fillReadings(vm)
        vm.onSaveClicked()
        vm.onSaveClicked()
        advanceUntilIdle()
        vm.onSaveClicked()
        assertEquals(1, repo.savedCount)
        assertEquals("session-1", vm.uiState.value.savedSessionId)
        assertTrue(vm.uiState.value.completionDeadlineMillis != null)
        vm.editSavedSession()
        assertEquals(null, vm.uiState.value.completionDeadlineMillis)
        vm.updateReading1Systolic("122")
        // Recreate from SavedStateHandle while editing the just-saved record.
        val restored = HomeViewModel(repo, savedStateHandle = handle, draftRepository = drafts)
        assertEquals("session-1", restored.uiState.value.savedSessionId)
        val draftSaved = CompletableDeferred<Unit>()
        restored.saveDraft { draftSaved.complete(Unit) }
        draftSaved.await()
        assertEquals("session-1", drafts.load("add_session").getOrThrow()?.sessionId)
        val reopened = HomeViewModel(repo, draftRepository = drafts)
        assertEquals("session-1", reopened.uiState.value.savedSessionId)
        reopened.onSaveClicked()
        reopened.onSaveClicked()
        advanceUntilIdle()
        assertEquals(1, repo.savedCount)
        assertEquals(1, repo.updatedCount)
        assertEquals("session-1", repo.updatedId)
        assertEquals(122, repo.lastInput?.readings?.first()?.systolic)
    }

    private fun fillReadings(vm: HomeViewModel) {
        vm.updateReading1Systolic("120")
        vm.updateReading1Diastolic("78")
        vm.updateReading2Systolic("130")
        vm.updateReading2Diastolic("82")
    }

    @Test
    fun extra_reading_can_be_removed_but_required_two_remain() = runTest {
        val vm = HomeViewModel(FakeRepository())
        vm.addNextReadingGroup()
        assertEquals(1, vm.uiState.value.extraReadings.size)

        vm.removeExtraReading(0)

        assertEquals(0, vm.uiState.value.extraReadings.size)
        assertTrue(!vm.uiState.value.canSave)
    }

    private class MemoryDraftRepository : com.example.bloodpressurerecord.ui.common.SessionDraftRepository {
        private var draft: com.example.bloodpressurerecord.ui.common.SessionFormDraft? = null
        override fun load(key: String) = Result.success(draft)
        override fun save(key: String, draft: com.example.bloodpressurerecord.ui.common.SessionFormDraft): Result<Unit> {
            this.draft = draft
            return Result.success(Unit)
        }
        override fun delete(key: String): Result<Unit> { draft = null; return Result.success(Unit) }
        override fun clearAll(): Result<Unit> { draft = null; return Result.success(Unit) }
    }

    private class FakeRepository(
        private val failSave: Boolean = false
    ) : BloodPressureRepository {
        var savedCount: Int = 0
            private set
        var updatedCount = 0
        var updatedId: String? = null
        var lastInput: SaveSessionInput? = null
            private set

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
        ): Flow<PeriodStatistics> = flowOf(PeriodStatistics())

        override suspend fun saveSession(input: SaveSessionInput): Result<String> {
            if (failSave) return Result.failure(IllegalStateException("磁盘已满"))
            savedCount += 1
            lastInput = input
            return Result.success("session-$savedCount")
        }

        override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> {
            updatedCount += 1
            updatedId = sessionId
            lastInput = input
            return Result.success(Unit)
        }

        override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession(session: SessionRecord): Result<Unit> = Result.success(Unit)
    }
}
