package com.example.bloodpressurerecord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import com.example.bloodpressurerecord.domain.time.toLocalDate
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val today: LocalDate = LocalDate.now(),
    val latest: LatestSessionSummary? = null,
    val todayCount: Int = 0,
    val todayAverageSystolic: Int? = null,
    val todayAverageDiastolic: Int? = null,
    /** 从今天（今天没记录则从昨天）往前连续有记录的天数。 */
    val streakDays: Int = 0,
    /** 最近 7 天（旧→新，最后一个是今天）每天是否有记录。 */
    val weekRecorded: List<Boolean> = List(7) { false },
    /** 今日服药打卡行（按时间升序）。 */
    val medicationSlots: List<MedicationSlot> = emptyList(),
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    repository: BloodPressureRepository,
    private val medicationRepository: MedicationRepository? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    todayTicks: Flow<LocalDate> = flow { emit(LocalDate.now(zoneId)) }
) : ViewModel() {

    // “今日”范围跟随日期流重算，跨零点后自动切换到新的一天。
    val uiState: StateFlow<DashboardUiState> = todayTicks
        .distinctUntilChanged()
        .flatMapLatest { today ->
            val todayRange = today.toEpochMillisRange(zoneId)
            val streakStart = today.minusDays(STREAK_WINDOW_DAYS)
                .atStartOfDay(zoneId).toInstant().toEpochMilli()
            combine(
                repository.observeLatestSessionSummary(),
                repository.observePeriodStatistics(todayRange.startInclusive, todayRange.endExclusive),
                // 连续打卡与一周圆点：复用现有日历轻量投影，不新增数据层查询。
                repository.observeCalendarSessionSummaries(streakStart, todayRange.endExclusive),
                medicationRepository?.observeSlotsForDay(today) ?: flowOf(emptyList())
            ) { latest, todayStatistics, recentSummaries, medicationSlots ->
                val recordedDates = recentSummaries
                    .mapTo(hashSetOf()) { it.measuredAt.toLocalDate(zoneId) }
                DashboardUiState(
                    today = today,
                    latest = latest,
                    todayCount = todayStatistics.recordCount,
                    todayAverageSystolic = todayStatistics.averageSystolic?.toInt(),
                    todayAverageDiastolic = todayStatistics.averageDiastolic?.toInt(),
                    streakDays = streakDays(today, recordedDates),
                    weekRecorded = (6 downTo 0).map { daysAgo ->
                        today.minusDays(daysAgo.toLong()) in recordedDates
                    },
                    medicationSlots = medicationSlots,
                    loading = false
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardUiState()
        )

    /** 勾选/取消勾选某个服药时间点的“已服”状态。 */
    fun toggleMedicationTaken(slot: MedicationSlot, taken: Boolean) {
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            medRepo.setTaken(slot.medicationId, slot.timeId, LocalDate.now(zoneId), taken)
        }
    }

    /** 今天有记录则从今天连续往前数；今天还没测则从昨天起算，避免早上被“清零”。 */
    private fun streakDays(today: LocalDate, recordedDates: Set<LocalDate>): Int {
        var cursor = if (today in recordedDates) today else today.minusDays(1)
        var streak = 0
        while (cursor in recordedDates && streak < STREAK_WINDOW_DAYS) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    companion object {
        private const val STREAK_WINDOW_DAYS = 365L
    }
}
