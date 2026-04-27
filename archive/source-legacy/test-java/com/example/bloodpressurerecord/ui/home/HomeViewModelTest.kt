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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `少于两组有效读数时不能保存`() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo)

        vm.updateReading1Systolic("120")
        vm.updateReading1Diastolic("80")
        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(0, repo.savedCount)
        assertTrue(vm.uiState.value.formMessage.contains("第2组"))
    }

    @Test
    fun `高值时先弹高优先级提醒并确认后保存`() = runTest {
        val repo = FakeRepository()
        val vm = HomeViewModel(repo)

        vm.updateReading1Systolic("181")
        vm.updateReading1Diastolic("100")
        vm.updateReading2Systolic("170")
        vm.updateReading2Diastolic("95")
        vm.onSaveClicked()

        assertTrue(vm.uiState.value.showHighRiskDialog)
        assertEquals(0, repo.savedCount)

        vm.confirmHighRiskAndSave()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showHighRiskDialog)
        assertEquals(1, repo.savedCount)
    }

    private class FakeRepository : BloodPressureRepository {
        private val count = MutableStateFlow(0)
        var savedCount: Int = 0
            private set

        override fun observeSessionCount(): Flow<Int> = count

        override fun observeSessions(): Flow<List<SessionRecord>> = count.map { emptyList() }

        override fun observeSession(sessionId: String): Flow<SessionRecord?> = count.map { null }

        override suspend fun saveSession(input: SaveSessionInput): Result<String> {
            savedCount += 1
            count.value = savedCount
            return Result.success("session-$savedCount")
        }

        override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun deleteSession(sessionId: String): Result<Unit> {
            return Result.success(Unit)
        }
    }
}
