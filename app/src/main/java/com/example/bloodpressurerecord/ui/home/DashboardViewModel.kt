package com.example.bloodpressurerecord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val latest: LatestSessionSummary? = null,
    val todayCount: Int = 0,
    val todayAverageSystolic: Int? = null,
    val todayAverageDiastolic: Int? = null,
    val loading: Boolean = true
)

class DashboardViewModel(
    repository: BloodPressureRepository,
    zoneId: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zoneId)
) : ViewModel() {
    private val todayRange = today.toEpochMillisRange(zoneId)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeLatestSessionSummary(),
        repository.observePeriodStatistics(todayRange.startInclusive, todayRange.endExclusive)
    ) { latest, todayStatistics ->
        DashboardUiState(
            latest = latest,
            todayCount = todayStatistics.recordCount,
            todayAverageSystolic = todayStatistics.averageSystolic?.toInt(),
            todayAverageDiastolic = todayStatistics.averageDiastolic?.toInt(),
            loading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUiState()
    )
}
