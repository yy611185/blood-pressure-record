package com.example.bloodpressurerecord.ui.history

import android.net.Uri
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SettingsBundle
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.TrendRepository
import com.example.bloodpressurerecord.data.repository.UserProfile
import com.example.bloodpressurerecord.domain.model.TrendRecord
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

        assertEquals(listOf("later"), vm.uiState.value.series.points.map { it.id })
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
