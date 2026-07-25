package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import com.example.bloodpressurerecord.domain.time.toLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val displayedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val monthState: CalendarLoadingState = CalendarLoadingState.LOADING,
    val dayState: CalendarLoadingState = CalendarLoadingState.CONTENT,
    val daySummaries: Map<LocalDate, CalendarDaySummary> = emptyMap(),
    val selectedDayRecords: List<HistorySessionItemUi> = emptyList(),
    val selectedDayAverageSystolic: Int? = null,
    val selectedDayAverageDiastolic: Int? = null,
    val monthError: String? = null,
    val dayError: String? = null
) {
    val monthHasRecords: Boolean get() = daySummaries.isNotEmpty()
}

data class HistorySessionItemUi(
    val id: String,
    val measuredAtText: String,
    val avgBloodPressureText: String,
    val avgPulseText: String,
    val scene: String,
    val categoryText: String,
    val noteSummary: String,
    val containsHighRiskReading: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: BloodPressureRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val todayProvider: () -> LocalDate = { LocalDate.now(zoneId) }
) : ViewModel() {
    private val displayedMonth = MutableStateFlow(
        savedStateHandle.get<String>(KEY_MONTH)?.let(YearMonth::parse)
            ?: YearMonth.from(todayProvider())
    )
    private val selectedDate = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SELECTED_DATE)?.let(LocalDate::parse)
    )
    private val monthRefresh = MutableStateFlow(0)
    private val dayRefresh = MutableStateFlow(0)
    private var pendingRequestedDate: LocalDate? = null

    private val monthResult: Flow<MonthResult> = combine(
        displayedMonth,
        monthRefresh
    ) { month, _ -> month }.flatMapLatest { month ->
        val range = month.toEpochMillisRange(zoneId)
        repository.observeCalendarSessionSummaries(range.startInclusive, range.endExclusive)
            .map { rows ->
                val summaries = rows.groupBy { it.measuredAt.toLocalDate(zoneId) }
                    .mapValues { (date, values) ->
                        CalendarDaySummary(
                            date = date,
                            recordCount = values.size,
                            containsHighRisk = values.any { it.containsHighRiskReading }
                        )
                    }
                MonthResult(month = month, summaries = summaries)
            }
            .catch {
                emit(MonthResult(month = month, error = "无法加载本月记录，请重试。"))
            }
    }

    private val dayResult: Flow<DayResult> = combine(
        selectedDate,
        dayRefresh
    ) { date, _ -> date }.flatMapLatest { date ->
        if (date == null) {
            flowOf(DayResult(date = null))
        } else {
            val range = date.toEpochMillisRange(zoneId)
            repository.observeSessionSummariesInRange(range.startInclusive, range.endExclusive)
                .map { records ->
                    DayResult(
                        date = date,
                        records = records.sortedWith(
                            compareBy<SessionSummary> { it.measuredAt }.thenBy { it.id }
                        )
                    )
                }
                .catch {
                    emit(DayResult(date = date, error = "无法加载当天记录，请重试。"))
                }
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        displayedMonth,
        selectedDate,
        monthResult,
        dayResult
    ) { month, selected, monthData, dayData ->
        val monthMatches = monthData.month == month
        val dayMatches = dayData.date == selected
        val records = if (dayMatches) dayData.records.map(::toItem) else emptyList()
        HistoryUiState(
            displayedMonth = month,
            selectedDate = selected,
            monthState = if (!monthMatches) {
                CalendarLoadingState.LOADING
            } else if (monthData.error == null) {
                CalendarLoadingState.CONTENT
            } else {
                CalendarLoadingState.ERROR
            },
            dayState = if (!dayMatches) {
                CalendarLoadingState.LOADING
            } else if (dayData.error == null) {
                CalendarLoadingState.CONTENT
            } else {
                CalendarLoadingState.ERROR
            },
            daySummaries = if (monthMatches) monthData.summaries else emptyMap(),
            selectedDayRecords = records,
            selectedDayAverageSystolic = dayData.records.takeIf { dayMatches && it.isNotEmpty() }
                ?.map { it.avgSystolic }?.average()?.toInt(),
            selectedDayAverageDiastolic = dayData.records.takeIf { dayMatches && it.isNotEmpty() }
                ?.map { it.avgDiastolic }?.average()?.toInt(),
            monthError = monthData.error,
            dayError = dayData.error
        )
    }.onEach { state ->
        if (state.monthState != CalendarLoadingState.CONTENT) return@onEach
        pendingRequestedDate?.let { requested ->
            pendingRequestedDate = null
            if (requested in state.daySummaries) {
                setSelectedDate(requested)
            } else {
                setSelectedDate(null)
            }
            return@onEach
        }
        if (state.selectedDate != null && state.selectedDate !in state.daySummaries) {
            setSelectedDate(null)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HistoryUiState(displayedMonth = displayedMonth.value, selectedDate = selectedDate.value)
    )

    fun showPreviousMonth() = showMonth(displayedMonth.value.minusMonths(1))

    fun showNextMonth() = showMonth(displayedMonth.value.plusMonths(1))

    fun showMonth(month: YearMonth) {
        displayedMonth.value = month
        savedStateHandle[KEY_MONTH] = month.toString()
        val selected = selectedDate.value
        if (selected != null && YearMonth.from(selected) != month) {
            selectDate(null)
        }
    }

    fun showToday(selectWhenRecorded: Boolean = true) {
        val today = todayProvider()
        if (selectWhenRecorded) openDateWhenAvailable(today) else showMonth(YearMonth.from(today))
    }

    fun openDateWhenAvailable(date: LocalDate) {
        pendingRequestedDate = date
        showMonth(YearMonth.from(date))
        if (uiState.value.displayedMonth == YearMonth.from(date) &&
            uiState.value.daySummaries.containsKey(date)
        ) {
            pendingRequestedDate = null
            selectDate(date)
        }
    }

    fun selectDate(date: LocalDate?) {
        if (date != null) {
            if (YearMonth.from(date) != displayedMonth.value) return
            if (!uiState.value.daySummaries.containsKey(date)) return
        }
        setSelectedDate(date)
    }

    fun retryMonth() {
        monthRefresh.value += 1
    }

    fun retryDay() {
        dayRefresh.value += 1
    }

    private fun setSelectedDate(date: LocalDate?) {
        selectedDate.value = date
        savedStateHandle[KEY_SELECTED_DATE] = date?.toString()
    }

    private fun toItem(record: SessionSummary): HistorySessionItemUi {
        return HistorySessionItemUi(
            id = record.id,
            measuredAtText = Instant.ofEpochMilli(record.measuredAt)
                .atZone(zoneId)
                .format(DateTimeFormatter.ofPattern("HH:mm")),
            avgBloodPressureText = "${record.avgSystolic}/${record.avgDiastolic}",
            avgPulseText = record.avgPulse?.toString() ?: "--",
            scene = record.scene,
            categoryText = record.category.toChineseCategory(),
            noteSummary = record.noteSummary?.takeIf { it.isNotBlank() } ?: NO_NOTE_TEXT,
            containsHighRiskReading = record.containsHighRiskReading
        )
    }

    private fun String.toChineseCategory(): String = when (uppercase()) {
        "NORMAL" -> "正常"
        "ELEVATED" -> "偏高"
        "STAGE1" -> "1级偏高"
        "STAGE2" -> "2级偏高"
        "SEVERE" -> "严重偏高"
        else -> this
    }

    private data class MonthResult(
        val month: YearMonth? = null,
        val summaries: Map<LocalDate, CalendarDaySummary> = emptyMap(),
        val error: String? = null
    )

    private data class DayResult(
        val date: LocalDate? = null,
        val records: List<SessionSummary> = emptyList(),
        val error: String? = null
    )

    companion object {
        const val NO_NOTE_TEXT = "无备注"
        private const val KEY_MONTH = "history.displayedMonth"
        private const val KEY_SELECTED_DATE = "history.selectedDate"
    }
}

enum class TrendMetricType { SYSTOLIC, DIASTOLIC, BOTH }
