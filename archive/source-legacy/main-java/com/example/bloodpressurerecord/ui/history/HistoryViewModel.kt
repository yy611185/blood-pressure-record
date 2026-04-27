package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.domain.calculator.HistoryStatisticsCalculator
import com.example.bloodpressurerecord.domain.model.SessionStatPoint
import com.example.bloodpressurerecord.domain.model.TrendDayPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

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
    val trend30d: List<TrendDayPoint> = emptyList()
)

enum class HistoryPeriodType { DAY, WEEK, MONTH }
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
        settingsRepository.observeSettings().map { it.appSettings.showTrendChart }
    ) { sessions, currentPeriod, metricType, showTrendChart ->
        buildUiState(sessions, currentPeriod, metricType, showTrendChart)
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
        showTrendChart: Boolean
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
        val filtered = sessions.filter { record ->
            val localDate = toLocalDate(record.measuredAt)
            when (period) {
                HistoryPeriodType.DAY -> localDate == today
                HistoryPeriodType.WEEK -> {
                    val firstDayOfWeek = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
                    val lastDayOfWeek = firstDayOfWeek.plusDays(6)
                    !localDate.isBefore(firstDayOfWeek) && !localDate.isAfter(lastDayOfWeek)
                }
                HistoryPeriodType.MONTH -> localDate.year == today.year && localDate.month == today.month
            }
        }

        val groups = filtered
            .groupBy { toLocalDate(it.measuredAt) }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                HistoryDateGroupUi(
                    dateLabel = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    sessions = list.sortedByDescending { it.measuredAt }.map { record ->
                        HistorySessionItemUi(
                            id = record.id,
                            measuredAtText = Instant.ofEpochMilli(record.measuredAt)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("HH:mm")),
                            avgBloodPressureText = "${record.avgSystolic}/${record.avgDiastolic}",
                            avgPulseText = record.avgPulse?.toString() ?: "--",
                            scene = record.scene,
                            categoryText = record.category,
                            noteSummary = record.note?.takeIf { it.isNotBlank() }?.take(24) ?: "无备注"
                        )
                    }
                )
            }
        return HistoryUiState(
            periodType = period,
            trendMetric = metricType,
            groups = groups,
            totalCountInPeriod = filtered.size,
            avg7dSystolic = statistics.average7d.avgSystolic?.toString() ?: "--",
            avg7dDiastolic = statistics.average7d.avgDiastolic?.toString() ?: "--",
            avg30dSystolic = statistics.average30d.avgSystolic?.toString() ?: "--",
            avg30dDiastolic = statistics.average30d.avgDiastolic?.toString() ?: "--",
            weekRecordCount = statistics.weekRecordCount,
            highRiskCount30d = statistics.highRiskCount30d,
            showTrendChart = showTrendChart,
            trend7d = statistics.trend7d,
            trend30d = statistics.trend30d
        )
    }

    private fun toLocalDate(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
