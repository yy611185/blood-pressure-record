package com.example.bloodpressurerecord

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bloodpressurerecord.ui.history.HistoryViewModel
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.ui.history.TrendViewModel
import kotlinx.coroutines.flow.map

class AppViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val container = (application as BloodPressureApplication).appContainer
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    repository = container.bloodPressureRepository,
                    highRiskAlertEnabled = container.settingsRepository.observeSettings()
                        .map { it.appSettings.highRiskAlertEnabled }
                ) as T
            }

            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(
                    repository = container.bloodPressureRepository,
                    settingsRepository = container.settingsRepository
                ) as T
            }

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(container.settingsRepository) as T
            }

            modelClass.isAssignableFrom(TrendViewModel::class.java) -> {
                TrendViewModel(
                    trendRepository = container.trendRepository,
                    settingsRepository = container.settingsRepository
                ) as T
            }

            else -> error("未知的 ViewModel 类型: ${modelClass.name}")
        }
    }
}
