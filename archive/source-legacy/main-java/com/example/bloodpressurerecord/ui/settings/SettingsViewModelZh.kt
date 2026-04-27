package com.example.bloodpressurerecord.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLargeTextEnabled: Boolean = true,
    val highRiskAlertEnabled: Boolean = true,
    val showTrendChart: Boolean = true,
    val morningReminderEnabled: Boolean = false,
    val morningReminderTime: String = "07:30",
    val eveningReminderEnabled: Boolean = false,
    val eveningReminderTime: String = "21:00",
    val name: String = "",
    val ageText: String = "",
    val gender: String = "",
    val targetSystolicText: String = "",
    val targetDiastolicText: String = "",
    val message: String = "",
    val showClearConfirm: Boolean = false,
    val showInfoDialog: Boolean = false,
    val showDisclaimerDialog: Boolean = false
)

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var isProfileDirty: Boolean = false

    init {
        viewModelScope.launch {
            repository.observeSettings().collectLatest { bundle ->
                _uiState.update { state ->
                    val profileName = if (isProfileDirty) state.name else bundle.userProfile.name.orEmpty()
                    val profileAge = if (isProfileDirty) state.ageText else bundle.userProfile.age?.toString().orEmpty()
                    val profileGender = if (isProfileDirty) state.gender else bundle.userProfile.gender.orEmpty()
                    val profileTargetSys = if (isProfileDirty) state.targetSystolicText else bundle.userProfile.targetSystolic?.toString().orEmpty()
                    val profileTargetDia = if (isProfileDirty) state.targetDiastolicText else bundle.userProfile.targetDiastolic?.toString().orEmpty()
                    state.copy(
                        isLargeTextEnabled = bundle.appSettings.largeTextEnabled,
                        highRiskAlertEnabled = bundle.appSettings.highRiskAlertEnabled,
                        showTrendChart = bundle.appSettings.showTrendChart,
                        morningReminderEnabled = bundle.appSettings.morningReminderEnabled,
                        morningReminderTime = bundle.appSettings.morningReminderTime,
                        eveningReminderEnabled = bundle.appSettings.eveningReminderEnabled,
                        eveningReminderTime = bundle.appSettings.eveningReminderTime,
                        name = profileName,
                        ageText = profileAge,
                        gender = profileGender,
                        targetSystolicText = profileTargetSys,
                        targetDiastolicText = profileTargetDia
                    )
                }
            }
        }
    }

    fun setLargeTextEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setLargeTextEnabled(enabled) }
    }

    fun setHighRiskAlertEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHighRiskAlertEnabled(enabled) }
    }

    fun setShowTrendChart(enabled: Boolean) {
        viewModelScope.launch { repository.setShowTrendChart(enabled) }
    }

    fun setMorningReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setMorningReminderEnabled(enabled) }
    }

    fun setEveningReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEveningReminderEnabled(enabled) }
    }

    fun updateMorningTime(value: String) {
        _uiState.update { it.copy(morningReminderTime = value) }
    }

    fun updateEveningTime(value: String) {
        _uiState.update { it.copy(eveningReminderTime = value) }
    }

    fun saveReminderTimes() {
        val state = _uiState.value
        if (!isTimeTextValid(state.morningReminderTime) || !isTimeTextValid(state.eveningReminderTime)) {
            _uiState.update { it.copy(message = "提醒时间格式应为 HH:mm，例如 07:30。") }
            return
        }
        viewModelScope.launch {
            repository.setMorningReminderTime(state.morningReminderTime)
            repository.setEveningReminderTime(state.eveningReminderTime)
            _uiState.update { it.copy(message = "提醒时间已保存。") }
        }
    }

    fun updateName(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(name = value) }
    }

    fun updateAgeText(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(ageText = value) }
    }

    fun updateGender(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(gender = value) }
    }

    fun updateTargetSystolicText(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(targetSystolicText = value) }
    }

    fun updateTargetDiastolicText(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(targetDiastolicText = value) }
    }

    fun saveUserProfile() {
        val state = _uiState.value
        val age = state.ageText.toIntOrNull()
        val targetSys = state.targetSystolicText.toIntOrNull()
        val targetDia = state.targetDiastolicText.toIntOrNull()

        if (state.ageText.isNotBlank() && age == null) {
            _uiState.update { it.copy(message = "年龄应为整数。") }
            return
        }
        if (state.targetSystolicText.isNotBlank() && targetSys == null) {
            _uiState.update { it.copy(message = "目标收缩压（高压）应为整数。") }
            return
        }
        if (state.targetDiastolicText.isNotBlank() && targetDia == null) {
            _uiState.update { it.copy(message = "目标舒张压（低压）应为整数。") }
            return
        }

        viewModelScope.launch {
            repository.saveUserProfile(
                UserProfile(
                    name = state.name,
                    age = age,
                    gender = state.gender,
                    targetSystolic = targetSys,
                    targetDiastolic = targetDia
                )
            )
            isProfileDirty = false
            _uiState.update { it.copy(message = "用户资料已保存。") }
        }
    }

    fun exportCsv() = launchDataAction { repository.exportCsv().getOrThrow() }
    fun exportXlsx() = launchDataAction { repository.exportXlsx().getOrThrow() }
    fun importCsv() = launchDataAction { repository.importCsv().getOrThrow() }
    fun importXlsx() = launchDataAction { repository.importXlsx().getOrThrow() }

    fun importCsvFromUri(uri: Uri, fileNameHint: String? = "import.csv") {
        viewModelScope.launch {
            runCatching {
                repository.stageImportFromUri(uri, fileNameHint).getOrThrow()
                repository.importCsv().getOrThrow()
            }.onSuccess { msg ->
                _uiState.update { it.copy(message = msg) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(message = throwable.message ?: "导入失败") }
            }
        }
    }

    fun importXlsxFromUri(uri: Uri, fileNameHint: String? = "import.xlsx") {
        viewModelScope.launch {
            runCatching {
                repository.stageImportFromUri(uri, fileNameHint).getOrThrow()
                repository.importXlsx().getOrThrow()
            }.onSuccess { msg ->
                _uiState.update { it.copy(message = msg) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(message = throwable.message ?: "导入失败") }
            }
        }
    }

    fun requestClearAll() {
        _uiState.update { it.copy(showClearConfirm = true) }
    }

    fun dismissClearAll() {
        _uiState.update { it.copy(showClearConfirm = false) }
    }

    fun confirmClearAll() {
        viewModelScope.launch {
            repository.clearAllData()
                .onSuccess {
                    isProfileDirty = false
                    _uiState.update { it.copy(showClearConfirm = false, message = "全部数据已清空。") }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            showClearConfirm = false,
                            message = "清空失败：${throwable.message ?: "请稍后重试"}"
                        )
                    }
                }
        }
    }

    fun showInfoDialog(show: Boolean) {
        _uiState.update { it.copy(showInfoDialog = show) }
    }

    fun showDisclaimerDialog(show: Boolean) {
        _uiState.update { it.copy(showDisclaimerDialog = show) }
    }

    private fun launchDataAction(block: suspend () -> String) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { text -> _uiState.update { it.copy(message = text) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(message = throwable.message ?: "操作失败") }
                }
        }
    }

    private fun isTimeTextValid(value: String): Boolean {
        return Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(value)
    }
}
