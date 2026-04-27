package com.example.bloodpressurerecord.ui.home

import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

    private class FakeRepository : BloodPressureRepository {
        private val count = MutableStateFlow(0)
        var savedCount: Int = 0
            private set
        var lastInput: SaveSessionInput? = null
            private set

        override fun observeSessionCount(): Flow<Int> = count

        override fun observeSessions(): Flow<List<SessionRecord>> = count.map { emptyList() }

        override fun observeSession(sessionId: String): Flow<SessionRecord?> = count.map { null }

        override suspend fun saveSession(input: SaveSessionInput): Result<String> {
            savedCount += 1
            lastInput = input
            count.value = savedCount
            return Result.success("session-$savedCount")
        }

        override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> = Result.success(Unit)

        override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)
    }
}
