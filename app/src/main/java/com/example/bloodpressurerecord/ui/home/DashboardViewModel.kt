package com.example.bloodpressurerecord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.data.repository.SettingsBundle
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.domain.time.naturalWeekDates
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import com.example.bloodpressurerecord.domain.time.toLocalDate
import com.example.bloodpressurerecord.domain.time.weekStartSunday
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

data class DashboardUiState(
    val today: LocalDate = LocalDate.now(),
    val latest: LatestSessionSummary? = null,
    val latestSession: SessionRecord? = null,
    val todayMorning: SessionSummary? = null,
    val todayEvening: SessionSummary? = null,
    /** 自然周（周日→周六，固定 7 天）的每日均值与打卡状态。 */
    val week: List<DashboardWeekDay> = emptyList(),
    val userName: String? = null,
    val showTrendChart: Boolean = true,
    val showBuddy: Boolean = true,
    val todayCount: Int = 0,
    val todayAverageSystolic: Int? = null,
    val todayAverageDiastolic: Int? = null,
    /** 从今天（今天没记录则从昨天）往前连续有记录的天数。 */
    val streakDays: Int = 0,
    /** 今日服药打卡行（按时间升序）。 */
    val medicationSlots: List<MedicationSlot> = emptyList(),
    /** 正在写入的时间点；UI 应暂时禁用对应复选框，避免快速点击产生竞态。 */
    val pendingMedicationTimeIds: Set<Long> = emptySet(),
    val loading: Boolean = true
)

data class DashboardWeekDay(
    val date: LocalDate,
    val averageSystolic: Int? = null,
    val averageDiastolic: Int? = null,
    val recorded: Boolean = false
)

data class MedicationFeedback(
    val slot: MedicationSlot,
    val taken: Boolean,
    val success: Boolean
)

private data class MeasurementSources(
    val latest: LatestSessionSummary?,
    val latestSession: SessionRecord?,
    val todayStatistics: PeriodStatistics,
    val recentSummaries: List<CalendarSessionSummary>,
    val weekSummaries: List<SessionSummary>
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    repository: BloodPressureRepository,
    private val medicationRepository: MedicationRepository? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    todayTicks: Flow<LocalDate> = flow { emit(LocalDate.now(zoneId)) },
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {
    private val pendingMedicationCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val medicationFeedbackEvents = MutableSharedFlow<MedicationFeedback>(extraBufferCapacity = 4)
    val medicationFeedback = medicationFeedbackEvents.asSharedFlow()
    private val medicationToggleMutex = Mutex()

    // “今日”范围跟随日期流重算，跨零点后自动切换到新的一天。
    val uiState: StateFlow<DashboardUiState> = todayTicks
        .distinctUntilChanged()
        .flatMapLatest { today ->
            val todayRange = today.toEpochMillisRange(zoneId)
            val streakStart = today.minusDays(STREAK_WINDOW_DAYS)
                .atStartOfDay(zoneId).toInstant().toEpochMilli()
            // 「这一周」固定为自然周（周日→周六），查询范围与展示的 7 天完全一致。
            val weekStartDate = weekStartSunday(today)
            val weekStart = weekStartDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val latestWithDetail: Flow<Pair<LatestSessionSummary?, SessionRecord?>> =
                repository.observeLatestSessionSummary()
                .flatMapLatest { latest ->
                    if (latest == null) flowOf(null to null)
                    else repository.observeSession(latest.id).map { latest to it }
                }
            val measurementSources = combine(
                latestWithDetail,
                repository.observePeriodStatistics(todayRange.startInclusive, todayRange.endExclusive),
                // 连续打卡与一周圆点：复用现有日历轻量投影，不新增数据层查询。
                repository.observeCalendarSessionSummaries(streakStart, todayRange.endExclusive),
                repository.observeSessionSummariesInRange(weekStart, todayRange.endExclusive)
            ) { latestPair, todayStatistics, recentSummaries, weekSummaries ->
                MeasurementSources(
                    latest = latestPair.first,
                    latestSession = latestPair.second,
                    todayStatistics = todayStatistics,
                    recentSummaries = recentSummaries,
                    weekSummaries = weekSummaries
                )
            }
            combine(
                measurementSources,
                medicationRepository?.observeSlotsForDay(today) ?: flowOf(emptyList<MedicationSlot>()),
                settingsRepository?.observeSettings() ?: flowOf(SettingsBundle())
            ) { measurements, medicationSlots, settings ->
                val latest = measurements.latest
                val latestSession = measurements.latestSession
                val todayStatistics = measurements.todayStatistics
                val recentSummaries = measurements.recentSummaries
                val weekSummaries = measurements.weekSummaries
                val recordedDates = recentSummaries
                    .mapTo(hashSetOf()) { it.measuredAt.toLocalDate(zoneId) }
                val todaySessions = weekSummaries
                    .filter { it.measuredAt.toLocalDate(zoneId) == today }
                    .sortedBy { it.measuredAt }
                val weekByDate = weekSummaries.groupBy { it.measuredAt.toLocalDate(zoneId) }
                DashboardUiState(
                    today = today,
                    latest = latest,
                    latestSession = latestSession,
                    todayMorning = todaySessions.lastOrNull { localHour(it.measuredAt) < 12 },
                    todayEvening = todaySessions.lastOrNull { localHour(it.measuredAt) >= 12 },
                    week = naturalWeekDates(today).map { date ->
                        val sessions = weekByDate[date].orEmpty()
                        DashboardWeekDay(
                            date = date,
                            averageSystolic = sessions.takeIf { it.isNotEmpty() }
                                ?.map { it.avgSystolic }?.average()?.roundToInt(),
                            averageDiastolic = sessions.takeIf { it.isNotEmpty() }
                                ?.map { it.avgDiastolic }?.average()?.roundToInt(),
                            recorded = date in recordedDates
                        )
                    },
                    userName = settings.userProfile.name?.trim()?.takeIf { it.isNotEmpty() },
                    showTrendChart = settings.appSettings.showTrendChart,
                    showBuddy = settings.appSettings.showBuddy,
                    todayCount = todayStatistics.recordCount,
                    todayAverageSystolic = todayStatistics.averageSystolic?.roundToInt(),
                    todayAverageDiastolic = todayStatistics.averageDiastolic?.roundToInt(),
                    streakDays = streakDays(today, recordedDates),
                    medicationSlots = medicationSlots,
                    loading = false
                )
            }
        }
        .combine(pendingMedicationCounts) { state, pendingCounts ->
            state.copy(pendingMedicationTimeIds = pendingCounts.keys)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardUiState()
        )

    /** 勾选/取消勾选某个服药时间点的“已服”状态。 */
    fun toggleMedicationTaken(slot: MedicationSlot, taken: Boolean) {
        val medRepo = medicationRepository ?: return
        val date = LocalDate.now(zoneId)
        pendingMedicationCounts.update { counts ->
            counts + (slot.timeId to ((counts[slot.timeId] ?: 0) + 1))
        }
        viewModelScope.launch {
            try {
                medicationToggleMutex.withLock {
                    medRepo.setTaken(slot.medicationId, slot.timeId, date, taken)
                }
                medicationFeedbackEvents.emit(MedicationFeedback(slot, taken, success = true))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                medicationFeedbackEvents.emit(MedicationFeedback(slot, taken, success = false))
            } finally {
                pendingMedicationCounts.update { counts ->
                    val remaining = (counts[slot.timeId] ?: 1) - 1
                    if (remaining <= 0) counts - slot.timeId
                    else counts + (slot.timeId to remaining)
                }
            }
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

    private fun localHour(measuredAt: Long): Int =
        Instant.ofEpochMilli(measuredAt).atZone(zoneId).hour

    companion object {
        private const val STREAK_WINDOW_DAYS = 365L
    }
}
