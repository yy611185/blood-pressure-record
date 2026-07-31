package com.example.bloodpressurerecord.ui.home

import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SessionSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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

        vm.toggleThirdReading(true)
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
        vm.toggleThirdReading(true)
        vm.updateExtraReadingSystolic(0, "130")
        vm.toggleThirdReading(false)
        assertTrue(vm.uiState.value.extraReadings.isEmpty())
        assertTrue(!vm.uiState.value.showExtraReadings)
    }

    @Test
    fun expand_third_group_initializes_dynamic_list() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo)
        vm.toggleThirdReading(true)
        assertTrue(vm.uiState.value.showExtraReadings)
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
        assertEquals("保存成功。", vm.uiState.value.formMessage)
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
    fun scene_follows_measured_time_until_manual_choice() = runTest {
        val vm = HomeViewModel(FakeRepository())

        vm.updateMeasuredAtText("2026-07-26 07:30")
        assertEquals("晨起", vm.uiState.value.scene)
        vm.updateMeasuredAtText("2026-07-26 10:00")
        assertEquals("上午", vm.uiState.value.scene)
        vm.updateMeasuredAtText("2026-07-26 13:00")
        assertEquals("下午", vm.uiState.value.scene)
        vm.updateMeasuredAtText("2026-07-26 20:00")
        assertEquals("晚上", vm.uiState.value.scene)
        vm.updateMeasuredAtText("2026-07-26 02:00")
        assertEquals("凌晨", vm.uiState.value.scene)

        // 手动选过场景后，改时间不再自动跟随。
        vm.updateScene("其他")
        vm.updateMeasuredAtText("2026-07-26 07:30")
        assertEquals("其他", vm.uiState.value.scene)
    }

    @Test
    fun factors_are_merged_into_saved_symptom_tags() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo)
        vm.updateReading1Systolic("120")
        vm.updateReading1Diastolic("80")
        vm.updateReading2Systolic("125")
        vm.updateReading2Diastolic("82")
        vm.toggleSymptom("头晕")
        vm.toggleFactor("饮酒后")
        vm.toggleFactor("睡眠不足")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(
            setOf("头晕", "饮酒后", "睡眠不足"),
            repo.lastInput?.symptoms?.toSet()
        )
    }

    @Test
    fun toggling_no_symptom_keeps_selected_factors() = runTest {
        val vm = HomeViewModel(FakeRepository())
        vm.toggleFactor("饮酒后")
        vm.toggleSymptom("无症状")

        assertEquals(setOf("无症状"), vm.uiState.value.selectedSymptoms)
        assertEquals(setOf("饮酒后"), vm.uiState.value.selectedFactors)
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

    private class FakeRepository(
        private val failSave: Boolean = false
    ) : BloodPressureRepository {
        var savedCount: Int = 0
            private set
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

        override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> = Result.success(Unit)

        override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession(session: SessionRecord): Result<Unit> = Result.success(Unit)
    }
}
