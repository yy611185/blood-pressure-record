package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.domain.calculator.HistoryStatisticsCalculator
import com.example.bloodpressurerecord.domain.model.SessionStatPoint
import com.example.bloodpressurerecord.domain.model.TrendDayPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val periodType: HistoryPeriodType = HistoryPeriodType.WEEK,
    val trendMetric: TrendMetricType = TrendMetricType.BOTH,
    val groups: List<HistoryDateGroupUi> = emptyList(),
    val totalCountInPeriod: Int = 0,
    val avg7dSystolic: String = "--",
    val avg7dDiastolic: String = "--",
    val avg30dSystolic: String = "--",
    val avg30dDiastolic: String = "--",
    val weekRecordCount: Int = 0,
    val highRiskCount30d: Int = 0,
    val showTrendChart: Boolean = true,
    val trend7d: List<TrendDayPoint> = emptyList(),
    val trend30d: List<TrendDayPoint> = emptyList(),
    val sessions7d: List<SessionRecord> = emptyList(),
    val sessions30d: List<SessionRecord> = emptyList(),
    val sessions90d: List<SessionRecord> = emptyList(),
    val sessionsAll: List<SessionRecord> = emptyList(),
    val targetSystolic: Int? = null,
    val targetDiastolic: Int? = null
)

enum class HistoryPeriodType { DAY, WEEK, MONTH, ALL }
enum class TrendMetricType { SYSTOLIC, DIASTOLIC, BOTH }

data class HistoryDateGroupUi(
    val dateLabel: String,
    val sessions: List<HistorySessionItemUi>
)

data class HistorySessionItemUi(
    val id: String,
    val measuredAtText: String,
    val avgBloodPressureText: String,
    val avgPulseText: String,
    val scene: String,
    val categoryText: String,
    val noteSummary: String
)

class HistoryViewModel(
    repository: BloodPressureRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val periodType = MutableStateFlow(HistoryPeriodType.WEEK)
    private val trendMetricType = MutableStateFlow(TrendMetricType.BOTH)

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observeSessions(),
        periodType,
        trendMetricType,
        settingsRepository.observeSettings()
    ) { sessions, currentPeriod, metricType, settingsBundle ->
        buildUiState(
            sessions = sessions,
            period = currentPeriod,
            metricType = metricType,
            showTrendChart = settingsBundle.appSettings.showTrendChart,
            targetSystolic = settingsBundle.userProfile.targetSystolic,
            targetDiastolic = settingsBundle.userProfile.targetDiastolic
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setPeriodType(type: HistoryPeriodType) {
        periodType.value = type
    }

    fun setTrendMetric(type: TrendMetricType) {
        trendMetricType.value = type
    }

    private fun buildUiState(
        sessions: List<SessionRecord>,
        period: HistoryPeriodType,
        metricType: TrendMetricType,
        showTrendChart: Boolean,
        targetSystolic: Int?,
        targetDiastolic: Int?
    ): HistoryUiState {
        val today = LocalDate.now()
        val statPoints = sessions.map {
            SessionStatPoint(
                measuredAt = it.measuredAt,
                avgSystolic = it.avgSystolic,
                avgDiastolic = it.avgDiastolic,
                highRisk = it.highRiskAlertTriggered
            )
        }
        val statistics = HistoryStatisticsCalculator.calculate(statPoints)
        val filteredSessions = filterSessionsForPeriod(sessions, period, today)

        val groups = filteredSessions
            .groupBy { toLocalDate(it.measuredAt) }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                HistoryDateGroupUi(
                    dateLabel = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                    sessions = list.sortedByDescending { it.measuredAt }.map { record ->
                        HistorySessionItemUi(
                            id = record.id,
                            measuredAtText = Instant.ofEpochMilli(record.measuredAt)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("HH:mm")),
                            avgBloodPressureText = "${record.avgSystolic}/${record.avgDiastolic}",
                            avgPulseText = record.avgPulse?.toString() ?: "--",
                            scene = record.scene,
                            categoryText = record.category.toChineseCategory(),
                            noteSummary = record.note?.takeIf { it.isNotBlank() }?.take(24) ?: NO_NOTE_TEXT
                        )
                    }
                )
            }

        return HistoryUiState(
            periodType = period,
            trendMetric = metricType,
            groups = groups,
            totalCountInPeriod = filteredSessions.size,
            avg7dSystolic = statistics.average7d.avgSystolic?.toString() ?: "--",
            avg7dDiastolic = statistics.average7d.avgDiastolic?.toString() ?: "--",
            avg30dSystolic = statistics.average30d.avgSystolic?.toString() ?: "--",
            avg30dDiastolic = statistics.average30d.avgDiastolic?.toString() ?: "--",
            weekRecordCount = statistics.weekRecordCount,
            highRiskCount30d = statistics.highRiskCount30d,
            showTrendChart = showTrendChart,
            trend7d = statistics.trend7d,
            trend30d = statistics.trend30d,
            sessions7d = sessions.filter { !toLocalDate(it.measuredAt).isBefore(today.minusDays(6)) },
            sessions30d = sessions.filter { !toLocalDate(it.measuredAt).isBefore(today.minusDays(29)) },
            sessions90d = sessions.filter { !toLocalDate(it.measuredAt).isBefore(today.minusDays(89)) },
            sessionsAll = sessions,
            targetSystolic = targetSystolic,
            targetDiastolic = targetDiastolic
        )
    }

    private fun filterSessionsForPeriod(
        sessions: List<SessionRecord>,
        period: HistoryPeriodType,
        today: LocalDate
    ): List<SessionRecord> {
        return sessions.filter { record ->
            val localDate = toLocalDate(record.measuredAt)
            when (period) {
                HistoryPeriodType.DAY -> localDate == today
                HistoryPeriodType.WEEK -> {
                    val firstDayOfWeek = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
                    val lastDayOfWeek = firstDayOfWeek.plusDays(6)
                    !localDate.isBefore(firstDayOfWeek) && !localDate.isAfter(lastDayOfWeek)
                }
                HistoryPeriodType.MONTH -> localDate.year == today.year && localDate.month == today.month
                HistoryPeriodType.ALL -> true
            }
        }
    }

    private fun String.toChineseCategory(): String = when (uppercase()) {
        "NORMAL" -> "正常"
        "ELEVATED" -> "偏高"
        "STAGE1" -> "1级偏高"
        "STAGE2" -> "2级偏高"
        "SEVERE" -> "严重偏高"
        else -> this
    }

    private fun toLocalDate(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    companion object {
        const val NO_NOTE_TEXT = "无备注"
    }
}
