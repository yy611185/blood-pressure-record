package com.example.bloodpressurerecord.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.db.dao.MedicationWithTimes
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.UserProfile
import com.example.bloodpressurerecord.data.repository.backup.BackupContainerFormatException
import com.example.bloodpressurerecord.data.repository.backup.BackupImportOptions
import com.example.bloodpressurerecord.data.repository.backup.BackupImportPreview
import com.example.bloodpressurerecord.data.repository.backup.BackupPassphraseException
import com.example.bloodpressurerecord.data.repository.backup.BackupPassphraseRequiredException
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
    val discardFirstReading: Boolean = false,
    val morningReminderEnabled: Boolean = false,
    val morningReminderTime: String = "07:30",
    val eveningReminderEnabled: Boolean = false,
    val eveningReminderTime: String = "21:00",
    val medicationReminderEnabled: Boolean = true,
    val medicationCalendarSyncEnabled: Boolean = false,
    val medications: List<MedicationWithTimes> = emptyList(),
    val name: String = "",
    val ageText: String = "",
    val gender: String = "",
    val targetSystolicText: String = "",
    val targetDiastolicText: String = "",
    val message: String = "",
    val showClearConfirm: Boolean = false,
    val showBackupExportConfirm: Boolean = false,
    val showExportPassphraseDialog: Boolean = false,
    val showBackupImportConfirm: Boolean = false,
    val showImportPassphraseDialog: Boolean = false,
    val importPassphraseError: String? = null,
    val backupImportPreview: BackupImportPreview? = null,
    val importMeasurementsSelected: Boolean = true,
    val restoreUserProfileSelected: Boolean = false,
    val restoreDisplaySettingsSelected: Boolean = false,
    val restoreReminderSettingsSelected: Boolean = false,
    val isDataActionRunning: Boolean = false,
    val lastSuccessfulExportAt: Long? = null,
    val ageError: String? = null,
    val targetSystolicError: String? = null,
    val targetDiastolicError: String? = null
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val medicationRepository: MedicationRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var isProfileDirty: Boolean = false
    /** 等待用户输入口令后继续预览的加密备份文件。 */
    private var pendingImportUri: Uri? = null

    init {
        medicationRepository?.let { medRepo ->
            viewModelScope.launch {
                medRepo.observeMedicationsWithTimes().collectLatest { meds ->
                    _uiState.update { it.copy(medications = meds) }
                }
            }
        }
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
                        discardFirstReading = bundle.appSettings.discardFirstReading,
                        morningReminderEnabled = bundle.appSettings.morningReminderEnabled,
                        morningReminderTime = bundle.appSettings.morningReminderTime,
                        eveningReminderEnabled = bundle.appSettings.eveningReminderEnabled,
                        eveningReminderTime = bundle.appSettings.eveningReminderTime,
                        medicationReminderEnabled = bundle.appSettings.medicationReminderEnabled,
                        medicationCalendarSyncEnabled =
                            bundle.appSettings.medicationCalendarSyncEnabled,
                        name = profileName,
                        ageText = profileAge,
                        gender = profileGender,
                        targetSystolicText = profileTargetSys,
                        targetDiastolicText = profileTargetDia,
                        lastSuccessfulExportAt = bundle.appSettings.lastSuccessfulExportAt
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

    fun setDiscardFirstReading(enabled: Boolean) {
        viewModelScope.launch { repository.setDiscardFirstReading(enabled) }
    }

    fun setMorningReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMorningReminderEnabled(enabled)
            _uiState.update {
                it.copy(message = if (enabled) "晨间提醒已启用。" else "晨间提醒已关闭。")
            }
        }
    }

    fun setEveningReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEveningReminderEnabled(enabled)
            _uiState.update {
                it.copy(message = if (enabled) "晚间提醒已启用。" else "晚间提醒已关闭。")
            }
        }
    }

    fun setMedicationReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setMedicationReminderEnabled(enabled) }
                .onSuccess {
                    _uiState.update {
                        it.copy(message = if (enabled) "服药提醒已启用。" else "服药提醒已关闭。")
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = "服药提醒更新失败：${throwable.message ?: "请稍后重试"}")
                    }
                }
        }
    }

    fun setMedicationCalendarSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setMedicationCalendarSyncEnabled(enabled) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            message = if (enabled) {
                                "已开启同步：服药时间会写入系统日历的每日日程。"
                            } else {
                                "已关闭日历同步，本应用创建的服药日程已清理。"
                            }
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = "日历同步失败：${throwable.message ?: "请稍后重试"}")
                    }
                }
        }
    }

    fun addMedication(name: String, dosage: String, times: List<String>) {
        if (name.isBlank()) {
            _uiState.update { it.copy(message = "请填写药品名称。") }
            return
        }
        if (times.isEmpty()) {
            _uiState.update { it.copy(message = "请至少添加一个服药时间。") }
            return
        }
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            medRepo.addMedication(name, dosage, times)
                .onSuccess {
                    runCatching { repository.refreshReminders() }
                        .onSuccess {
                            _uiState.update { it.copy(message = "已添加「${name.trim()}」。") }
                        }
                        .onFailure { throwable ->
                            _uiState.update {
                                it.copy(
                                    message = "药品已保存，但提醒同步失败：" +
                                        (throwable.message ?: "请稍后在提醒设置中重试")
                                )
                            }
                        }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = "添加失败：${throwable.message ?: "请稍后重试"}")
                    }
                }
        }
    }

    fun updateMedication(id: Long, name: String, dosage: String, times: List<String>) {
        if (name.isBlank()) {
            _uiState.update { it.copy(message = "请填写药品名称。") }
            return
        }
        if (times.isEmpty()) {
            _uiState.update { it.copy(message = "请至少添加一个服药时间。") }
            return
        }
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            medRepo.updateMedication(id, name, dosage, enabled = true, times = times)
                .onSuccess {
                    runCatching { repository.refreshReminders() }
                        .onSuccess {
                            _uiState.update { it.copy(message = "「${name.trim()}」已更新。") }
                        }
                        .onFailure { throwable ->
                            _uiState.update {
                                it.copy(
                                    message = "药品已更新，但提醒同步失败：" +
                                        (throwable.message ?: "请稍后在提醒设置中重试")
                                )
                            }
                        }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = "保存失败：${throwable.message ?: "请稍后重试"}")
                    }
                }
        }
    }

    fun deleteMedication(id: Long) {
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            medRepo.deleteMedication(id)
                .onSuccess {
                    runCatching { repository.refreshReminders() }
                        .onSuccess {
                            _uiState.update { it.copy(message = "药品已删除。") }
                        }
                        .onFailure { throwable ->
                            _uiState.update {
                                it.copy(
                                    message = "药品已删除，但提醒同步失败：" +
                                        (throwable.message ?: "请稍后在提醒设置中重试")
                                )
                            }
                        }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = "删除失败：${throwable.message ?: "请稍后重试"}")
                    }
                }
        }
    }

    fun showReminderAuthorizationRequired(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun refreshReminders() {
        viewModelScope.launch {
            runCatching { repository.refreshReminders() }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = "提醒刷新失败：${throwable.message ?: "请稍后重试"}")
                    }
                }
        }
    }

    fun updateMorningTime(value: String) {
        if (!isTimeTextValid(value)) {
            _uiState.update { it.copy(message = "提醒时间格式应为 HH:mm，例如 07:30。") }
            return
        }
        if (_uiState.value.morningReminderTime == value) return
        _uiState.update { it.copy(morningReminderTime = value) }
        viewModelScope.launch {
            runCatching { repository.setMorningReminderTime(value) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(message = "晨间提醒时间已自动保存。")
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(message = "自动保存失败：${throwable.message ?: "请稍后重试"}")
                    }
                }
        }
    }

    fun updateEveningTime(value: String) {
        if (!isTimeTextValid(value)) {
            _uiState.update { it.copy(message = "提醒时间格式应为 HH:mm，例如 19:30。") }
            return
        }
        if (_uiState.value.eveningReminderTime == value) return
        _uiState.update { it.copy(eveningReminderTime = value) }
        viewModelScope.launch {
            runCatching { repository.setEveningReminderTime(value) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(message = "晚间提醒时间已自动保存。")
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(message = "自动保存失败：${throwable.message ?: "请稍后重试"}")
                    }
                }
        }
    }

    fun updateName(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(name = value) }
    }

    fun updateAgeText(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(ageText = value, ageError = null) }
    }

    fun updateGender(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(gender = value) }
    }

    fun updateTargetSystolicText(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(targetSystolicText = value, targetSystolicError = null) }
    }

    fun updateTargetDiastolicText(value: String) {
        isProfileDirty = true
        _uiState.update { it.copy(targetDiastolicText = value, targetDiastolicError = null) }
    }

    fun saveUserProfile() {
        val state = _uiState.value
        val age = state.ageText.toIntOrNull()
        val targetSys = state.targetSystolicText.toIntOrNull()
        val targetDia = state.targetDiastolicText.toIntOrNull()

        if (state.ageText.isNotBlank() && age == null) {
            _uiState.update { it.copy(ageError = "年龄应为整数。", message = "请检查用户资料。") }
            return
        }
        if (age != null && age !in 1..120) {
            _uiState.update { it.copy(ageError = "年龄须在 1–120 岁之间。", message = "请检查用户资料。") }
            return
        }
        if (state.targetSystolicText.isNotBlank() && targetSys == null) {
            _uiState.update { it.copy(targetSystolicError = "请输入有效整数。", message = "请检查用户资料。") }
            return
        }
        if (state.targetDiastolicText.isNotBlank() && targetDia == null) {
            _uiState.update { it.copy(targetDiastolicError = "请输入有效整数。", message = "请检查用户资料。") }
            return
        }
        if (targetSys != null && targetSys !in 40..300) {
            _uiState.update { it.copy(targetSystolicError = "数值须在 40–300 之间。", message = "请检查用户资料。") }
            return
        }
        if (targetDia != null && targetDia !in 20..200) {
            _uiState.update { it.copy(targetDiastolicError = "数值须在 20–200 之间。", message = "请检查用户资料。") }
            return
        }
        if (targetSys != null && targetDia != null && targetDia >= targetSys) {
            _uiState.update {
                it.copy(
                    targetDiastolicError = "目标舒张压必须低于目标收缩压。",
                    message = "请检查用户资料。"
                )
            }
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
            _uiState.update {
                it.copy(
                    message = "用户资料已保存。",
                    ageError = null,
                    targetSystolicError = null,
                    targetDiastolicError = null
                )
            }
        }
    }

    fun requestBackupExport() {
        _uiState.update { it.copy(showBackupExportConfirm = true) }
    }

    fun dismissBackupExport() {
        _uiState.update { it.copy(showBackupExportConfirm = false) }
    }

    fun requestEncryptedBackupExport() {
        _uiState.update {
            it.copy(
                showBackupExportConfirm = false,
                showExportPassphraseDialog = true
            )
        }
    }

    fun dismissEncryptedBackupExport() {
        _uiState.update { it.copy(showExportPassphraseDialog = false) }
    }

    fun exportBackupXlsxToUri(uri: Uri, fileNameHint: String) {
        runExport(uri, fileNameHint, passphrase = null)
    }

    fun exportEncryptedBackupToUri(uri: Uri, fileNameHint: String, passphrase: CharArray) {
        runExport(uri, fileNameHint, passphrase)
    }

    private fun runExport(uri: Uri, fileNameHint: String, passphrase: CharArray?) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showBackupExportConfirm = false,
                    showExportPassphraseDialog = false,
                    isDataActionRunning = true,
                    message = if (passphrase != null) {
                        "正在导出加密备份，请稍候..."
                    } else {
                        "正在导出 Excel 备份，请稍候..."
                    }
                )
            }
            repository.exportBackupXlsxToUri(uri, fileNameHint, passphrase)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isDataActionRunning = false, message = msg) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isDataActionRunning = false,
                            message = throwable.message ?: "导出失败，请重新选择保存位置后再试"
                        )
                    }
                }
        }
    }

    fun requestClearAll() {
        _uiState.update { it.copy(showClearConfirm = true) }
    }

    fun requestBackupImport() {
        _uiState.update { it.copy(showBackupImportConfirm = true) }
    }

    fun dismissBackupImport() {
        _uiState.update {
            it.copy(
                showBackupImportConfirm = false,
                backupImportPreview = null,
                importMeasurementsSelected = true,
                restoreUserProfileSelected = false,
                restoreDisplaySettingsSelected = false,
                restoreReminderSettingsSelected = false
            )
        }
    }

    fun importBackupXlsxFromUri(uri: Uri) {
        previewBackupXlsxFromUri(uri)
    }

    fun previewBackupXlsxFromUri(uri: Uri, passphrase: CharArray? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showBackupImportConfirm = false,
                    isDataActionRunning = true,
                    message = "正在验证备份并生成导入预览..."
                )
            }
            repository.previewBackupXlsxFromUri(uri, passphrase)
                .onSuccess { preview ->
                    pendingImportUri = null
                    _uiState.update {
                        it.copy(
                            isDataActionRunning = false,
                            backupImportPreview = preview,
                            showImportPassphraseDialog = false,
                            importPassphraseError = null,
                            importMeasurementsSelected = true,
                            restoreUserProfileSelected = false,
                            restoreDisplaySettingsSelected = false,
                            restoreReminderSettingsSelected = false,
                            message = "预览完成：不会自动写入数据，请确认导入范围。"
                        )
                    }
                }
                .onFailure { throwable ->
                    when (throwable) {
                        is BackupPassphraseRequiredException -> {
                            pendingImportUri = uri
                            _uiState.update {
                                it.copy(
                                    isDataActionRunning = false,
                                    showImportPassphraseDialog = true,
                                    importPassphraseError = null,
                                    message = "该备份文件已加密。"
                                )
                            }
                        }
                        is BackupPassphraseException -> {
                            pendingImportUri = uri
                            _uiState.update {
                                it.copy(
                                    isDataActionRunning = false,
                                    showImportPassphraseDialog = true,
                                    importPassphraseError = throwable.message,
                                    message = throwable.message ?: "备份口令错误。"
                                )
                            }
                        }
                        else -> {
                            _uiState.update {
                                it.copy(
                                    isDataActionRunning = false,
                                    message = "导入失败：${throwable.message ?: "文件格式不受支持"}"
                                )
                            }
                        }
                    }
                }
        }
    }

    fun submitImportPassphrase(passphrase: CharArray) {
        val uri = pendingImportUri ?: return
        previewBackupXlsxFromUri(uri, passphrase)
    }

    fun dismissImportPassphrase() {
        pendingImportUri = null
        _uiState.update {
            it.copy(
                showImportPassphraseDialog = false,
                importPassphraseError = null
            )
        }
    }

    fun setImportMeasurementsSelected(selected: Boolean) {
        _uiState.update { it.copy(importMeasurementsSelected = selected) }
    }

    fun setRestoreUserProfileSelected(selected: Boolean) {
        _uiState.update { it.copy(restoreUserProfileSelected = selected) }
    }

    fun setRestoreDisplaySettingsSelected(selected: Boolean) {
        _uiState.update { it.copy(restoreDisplaySettingsSelected = selected) }
    }

    fun setRestoreReminderSettingsSelected(selected: Boolean) {
        _uiState.update { it.copy(restoreReminderSettingsSelected = selected) }
    }

    fun commitBackupImport() {
        val state = _uiState.value
        val preview = state.backupImportPreview ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isDataActionRunning = true, message = "正在提交导入，请稍候...")
            }
            repository.commitBackupImport(
                preview = preview,
                options = BackupImportOptions(
                    importMeasurements = state.importMeasurementsSelected,
                    restoreUserProfile = state.restoreUserProfileSelected,
                    restoreDisplaySettings = state.restoreDisplaySettingsSelected,
                    restoreReminderSettings = state.restoreReminderSettingsSelected
                )
            ).onSuccess { message ->
                isProfileDirty = false
                _uiState.update {
                    it.copy(
                        isDataActionRunning = false,
                        backupImportPreview = null,
                        message = message
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isDataActionRunning = false,
                        message = "导入失败：${throwable.message ?: "文件格式不受支持"}"
                    )
                }
            }
        }
    }

    fun dismissClearAll() {
        _uiState.update { it.copy(showClearConfirm = false) }
    }

    fun confirmClearAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDataActionRunning = true, message = "正在清空本地数据...") }
            repository.clearAllData()
                .onSuccess { result ->
                    isProfileDirty = false
                    _uiState.update {
                        it.copy(
                            showClearConfirm = false,
                            isDataActionRunning = false,
                            message = result.toUserMessage()
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            showClearConfirm = false,
                            isDataActionRunning = false,
                            message = "清空失败：${throwable.message ?: "请稍后重试"}"
                        )
                    }
                }
        }
    }

    private fun isTimeTextValid(value: String): Boolean {
        return Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(value)
    }
}
