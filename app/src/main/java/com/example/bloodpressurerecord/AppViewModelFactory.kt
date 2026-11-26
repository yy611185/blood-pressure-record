package com.example.bloodpressurerecord

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.bloodpressurerecord.domain.time.dayTicks
import com.example.bloodpressurerecord.ui.history.HistoryViewModel
import com.example.bloodpressurerecord.ui.history.NoteSearchViewModel
import com.example.bloodpressurerecord.ui.history.TrendViewModel
import com.example.bloodpressurerecord.ui.home.DashboardViewModel
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.report.ReportViewModel
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.update.UpdateViewModel
import java.time.ZoneId
import kotlinx.coroutines.flow.map

class AppViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return createInternal(modelClass, SavedStateHandle())
    }

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return createInternal(modelClass, extras.createSavedStateHandle())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ViewModel> createInternal(
        modelClass: Class<T>,
        savedStateHandle: SavedStateHandle
    ): T {
        val container = (application as BloodPressureApplication).appContainer
        val liveDayTicks = {
            dayTicks(
                zoneProvider = ZoneId::systemDefault,
                externalTrigger = container.systemTimeChangeSignal
            )
        }
        // 用精确类型匹配而非 isAssignableFrom：后者方向容易写反，
        // 请求 ViewModel 基类时会错误命中第一个分支。
        return when (modelClass) {
            ReportViewModel::class.java -> {
                ReportViewModel(
                    repository = container.reportRepository,
                    generator = container.reportGenerator,
                    zoneProvider = ZoneId::systemDefault,
                    todayTicks = liveDayTicks(),
                    timeChangeSignal = container.systemTimeChangeSignal,
                    updatePromptGate = container.updatePromptGate
                ) as T
            }

            HomeViewModel::class.java -> {
                HomeViewModel(
                    repository = container.bloodPressureRepository,
                    highRiskAlertEnabled = container.settingsRepository.observeSettings()
                        .map { it.appSettings.highRiskAlertEnabled },
                    savedStateHandle = savedStateHandle,
                    draftRepository = container.sessionDraftRepository,
                    updatePromptGate = container.updatePromptGate
                ) as T
            }

            DashboardViewModel::class.java -> {
                DashboardViewModel(
                    repository = container.bloodPressureRepository,
                    medicationRepository = container.medicationRepository,
                    todayTicks = liveDayTicks(),
                    settingsRepository = container.settingsRepository,
                    zoneConfigRepository = container.bpZoneConfigRepository,
                    zoneProvider = ZoneId::systemDefault,
                    timeChangeSignal = container.systemTimeChangeSignal
                ) as T
            }

            HistoryViewModel::class.java -> {
                HistoryViewModel(
                    repository = container.bloodPressureRepository,
                    savedStateHandle = savedStateHandle,
                    zoneProvider = ZoneId::systemDefault,
                    todayTicks = liveDayTicks(),
                    settingsRepository = container.settingsRepository,
                    timeChangeSignal = container.systemTimeChangeSignal
                ) as T
            }

            NoteSearchViewModel::class.java -> {
                NoteSearchViewModel(container.bloodPressureRepository, savedStateHandle) as T
            }

            SettingsViewModel::class.java -> {
                SettingsViewModel(
                    repository = container.settingsRepository,
                    medicationRepository = container.medicationRepository,
                    zoneConfigRepository = container.bpZoneConfigRepository,
                    updatePromptGate = container.updatePromptGate,
                    deletionUndoManager = container.deletionUndoManager,
                    savedStateHandle = savedStateHandle
                ) as T
            }

            TrendViewModel::class.java -> {
                TrendViewModel(
                    trendRepository = container.trendRepository,
                    settingsRepository = container.settingsRepository,
                    savedStateHandle = savedStateHandle,
                    todayTicks = liveDayTicks(),
                    zoneProvider = ZoneId::systemDefault,
                    timeChangeSignal = container.systemTimeChangeSignal
                ) as T
            }

            UpdateViewModel::class.java -> {
                UpdateViewModel(
                    repository = container.updateRepository,
                    preferences = container.updatePreferences
                ) as T
            }

            else -> error("未知的 ViewModel 类型: ${modelClass.name}")
        }
    }
}
