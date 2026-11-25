package com.example.bloodpressurerecord.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.datastore.AppSettings
import com.example.bloodpressurerecord.data.db.dao.MedicationWithTimes
import com.example.bloodpressurerecord.data.repository.BpZoneConfigRepository
import com.example.bloodpressurerecord.data.repository.DeletionUndoManager
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.UserProfile
import com.example.bloodpressurerecord.data.repository.backup.BackupExportPlan
import com.example.bloodpressurerecord.data.repository.backup.BackupImportOptions
import com.example.bloodpressurerecord.data.repository.backup.BackupImportPreview
import com.example.bloodpressurerecord.data.repository.backup.BackupPassphraseException
import com.example.bloodpressurerecord.data.repository.backup.BackupPassphraseRequiredException
import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import com.example.bloodpressurerecord.ui.home.BpZoneAxis
import com.example.bloodpressurerecord.ui.home.BpZoneField
import com.example.bloodpressurerecord.ui.home.BpZoneTargetDraft
import com.example.bloodpressurerecord.ui.home.BpZoneTargetEditorUiState
import com.example.bloodpressurerecord.update.UpdatePromptGate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SettingsDataAction {
    EXPORT_XLSX,
    EXPORT_BPX,
    IMPORT,
    CLEAR
}

data class SettingsUiState(
    val isLargeTextEnabled: Boolean = true,
    val highRiskAlertEnabled: Boolean = true,
    val showTrendChart: Boolean = true,
    val appearanceMode: String = "system",
    val showBuddy: Boolean = true,
    val morningReminderEnabled: Boolean = false,
    val morningReminderTime: String = "07:30",
    val eveningReminderEnabled: Boolean = false,
    val eveningReminderTime: String = "21:00",
    val medicationReminderEnabled: Boolean = true,
    val medicationCalendarSyncEnabled: Boolean = false,
    val medicationCalendarId: Long? = null,
    val medicationCalendarIncludeDetails: Boolean = false,
    val medications: List<MedicationWithTimes> = emptyList(),
    val name: String = "",
    val ageText: String = "",
    val gender: String = "",
    val targetSystolicText: String = "",
    val targetDiastolicText: String = "",
    val isProfileDirty: Boolean = false,
    val isProfileLoaded: Boolean = false,
    val isSavingProfile: Boolean = false,
    val message: String = "",
    val messageIsError: Boolean = false,
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
    val restoreMedicationsSelected: Boolean = false,
    val exportVolumeIndex: Int = 0,
    val exportVolumeCount: Int = 0,
    val exportTotalRecords: Int = 0,
    val exportVolumeRecords: Int = 0,
    val exportEncrypted: Boolean = false,
    val exportFileName: String = "",
    val exportVolumeRequestId: String? = null,
    val isDataActionRunning: Boolean = false,
    val isChoosingBackupLocation: Boolean = false,
    val activeDataAction: SettingsDataAction? = null,
    val lastSuccessfulExportAt: Long? = null,
    val ageError: String? = null,
    val targetSystolicError: String? = null,
    val targetDiastolicError: String? = null
) {
    val hasImportSelection: Boolean get() = importMeasurementsSelected || restoreUserProfileSelected ||
        restoreDisplaySettingsSelected || restoreReminderSettingsSelected || restoreMedicationsSelected
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val medicationRepository: MedicationRepository? = null,
    private val zoneConfigRepository: BpZoneConfigRepository? = null,
    private val updatePromptGate: UpdatePromptGate? = null,
    private val deletionUndoManager: DeletionUndoManager? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val mutableZoneTargetEditorState = MutableStateFlow<BpZoneTargetEditorUiState?>(null)
    val zoneTargetEditorState: StateFlow<BpZoneTargetEditorUiState?> =
        mutableZoneTargetEditorState.asStateFlow()
    private var openZoneTargetEditorJob: Job? = null
    // 始终观察持久化配置；打开编辑器时使用真实配置，不让默认占位值覆盖已保存目标。
    private val zoneConfigFlow =
        (zoneConfigRepository?.observeConfig() ?: flowOf(BpZoneConfig.Default))
            .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
    private var isProfileDirty: Boolean = false
    private var latestSavedProfile: UserProfile = UserProfile()
    private var latestSavedAppSettings: AppSettings = AppSettings()
    private val morningReminderMutex = Mutex()
    private val eveningReminderMutex = Mutex()
    /** 等待用户输入口令后继续预览的加密备份文件。 */
    private var pendingImportUri: Uri? = null
    private var pendingExportPlan: BackupExportPlan? = null
    private var pendingExportPassphrase: CharArray? = null
    private var pendingExportBaseName: String = ""
    private var releaseBackupLocation: (() -> Unit)? = null

    fun beginBackupLocation(): Boolean {
        val state = _uiState.value
        if (state.isDataActionRunning || state.isChoosingBackupLocation) return false
        releaseBackupLocation = updatePromptGate?.hold()
        _uiState.update { it.copy(isChoosingBackupLocation = true) }
        return true
    }

    fun finishBackupLocation(onResult: () -> Unit) {
        val release = releaseBackupLocation
        releaseBackupLocation = null
        _uiState.update { it.copy(isChoosingBackupLocation = false) }
        try {
            onResult()
        } finally {
            release?.invoke()
        }
    }

    fun onBackupLocationLaunchFailed() = finishBackupLocation {
        _uiState.update { it.copy(message = "无法打开文件选择器，请重试。", messageIsError = true) }
    }

    /** 门控随实际任务结束释放，离开数据管理页面也不会提前解除。 */
    private fun launchDataAction(
        action: SettingsDataAction,
        block: suspend CoroutineScope.() -> Unit
    ) {
        if (_uiState.value.isDataActionRunning || _uiState.value.isChoosingBackupLocation) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _uiState.update { it.copy(isDataActionRunning = true, activeDataAction = action) }
            val release = updatePromptGate?.hold()
            try {
                block()
            } finally {
                _uiState.update { it.copy(isDataActionRunning = false, activeDataAction = null) }
                release?.invoke()
            }
        }
    }

    override fun onCleared() {
        releaseBackupLocation?.invoke()
        releaseBackupLocation = null
        discardExportPlan()
        super.onCleared()
    }

    private fun discardExportPlan() {
        pendingExportPassphrase?.fill('\u0000')
        pendingExportPassphrase = null
        pendingExportPlan = null
        pendingExportBaseName = ""
        _uiState.update {
            it.copy(
                showBackupExportConfirm = false,
                showExportPassphraseDialog = false,
                exportVolumeRequestId = null,
                exportVolumeIndex = 0,
                exportVolumeCount = 0,
                exportTotalRecords = 0,
                exportVolumeRecords = 0,
                exportEncrypted = false,
                exportFileName = ""
            )
        }
    }

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
                latestSavedProfile = bundle.userProfile
                latestSavedAppSettings = bundle.appSettings
                _uiState.update { state ->
                    val keepDraft = isProfileDirty || state.isSavingProfile
                    val profileName = if (keepDraft) state.name else bundle.userProfile.name.orEmpty()
                    val profileAge = if (keepDraft) state.ageText else bundle.userProfile.age?.toString().orEmpty()
                    val profileGender = if (keepDraft) state.gender else bundle.userProfile.gender.orEmpty()
                    val profileTargetSys = if (keepDraft) state.targetSystolicText else bundle.userProfile.targetSystolic?.toString().orEmpty()
                    val profileTargetDia = if (keepDraft) state.targetDiastolicText else bundle.userProfile.targetDiastolic?.toString().orEmpty()
                    state.copy(
                        isLargeTextEnabled = bundle.appSettings.largeTextEnabled,
                        highRiskAlertEnabled = bundle.appSettings.highRiskAlertEnabled,
                        showTrendChart = bundle.appSettings.showTrendChart,
                        appearanceMode = bundle.appSettings.appearanceMode,
                        showBuddy = bundle.appSettings.showBuddy,
                        morningReminderEnabled = bundle.appSettings.morningReminderEnabled,
                        morningReminderTime = bundle.appSettings.morningReminderTime,
                        eveningReminderEnabled = bundle.appSettings.eveningReminderEnabled,
                        eveningReminderTime = bundle.appSettings.eveningReminderTime,
                        medicationReminderEnabled = bundle.appSettings.medicationReminderEnabled,
                        medicationCalendarSyncEnabled = bundle.appSettings.medicationCalendarSyncEnabled,
                        medicationCalendarId = bundle.appSettings.medicationCalendarId,
                        medicationCalendarIncludeDetails = bundle.appSettings.medicationCalendarIncludeDetails,
                        name = profileName,
                        ageText = profileAge,
                        gender = profileGender,
                        targetSystolicText = profileTargetSys,
                        targetDiastolicText = profileTargetDia,
                        isProfileDirty = isProfileDirty,
                        isProfileLoaded = true,
                        lastSuccessfulExportAt = bundle.appSettings.lastSuccessfulExportAt
                    )
                }
            }
        }
    }

    fun openZoneTargetEditor() {
        if (mutableZoneTargetEditorState.value?.saving == true) return
        openZoneTargetEditorJob?.cancel()
        val loadedConfig = zoneConfigFlow.replayCache.lastOrNull()
        if (loadedConfig != null) {
            mutableZoneTargetEditorState.value = BpZoneTargetEditorUiState(BpZoneTargetDraft.from(loadedConfig))
        } else {
            openZoneTargetEditorJob = viewModelScope.launch {
                val config = zoneConfigFlow.first()
                mutableZoneTargetEditorState.value = BpZoneTargetEditorUiState(BpZoneTargetDraft.from(config))
            }
        }
    }

    fun updateZoneTargetField(axis: BpZoneAxis, field: BpZoneField, value: String) {
        mutableZoneTargetEditorState.update { state ->
            if (state == null || state.saving) state
            else state.copy(draft = state.draft.update(axis, field, value), errorRes = null)
        }
    }

    fun resetZoneTargetToDefault() {
        mutableZoneTargetEditorState.update { state ->
            if (state == null || state.saving) state
            else state.copy(draft = BpZoneTargetDraft.from(BpZoneConfig.Default), errorRes = null)
        }
    }

    fun dismissZoneTargetEditor() {
        if (mutableZoneTargetEditorState.value?.saving != true) {
            openZoneTargetEditorJob?.cancel()
            openZoneTargetEditorJob = null
            mutableZoneTargetEditorState.value = null
        }
    }

    fun saveZoneTargetEditor() {
        val state = mutableZoneTargetEditorState.value ?: return
        if (state.saving) return
        val config = state.draft.toConfigOrNull()
        if (config == null) {
            mutableZoneTargetEditorState.value = state.copy(errorRes = R.string.bp_zone_config_invalid)
            return
        }
        mutableZoneTargetEditorState.value = state.copy(saving = true, errorRes = null)
        viewModelScope.launch {
            try {
                val result = zoneConfigRepository?.saveConfig(config)
                    ?: Result.failure(IllegalStateException("Blood pressure target repository is unavailable"))
                mutableZoneTargetEditorState.value = if (result.isSuccess) null
                else state.copy(errorRes = R.string.bp_zone_config_save_failed)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    mutableZoneTargetEditorState.value = state
                    throw error
                }
                mutableZoneTargetEditorState.value = state.copy(errorRes = R.string.bp_zone_config_save_failed)
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

    fun setAppearanceMode(mode: String) {
        viewModelScope.launch { repository.setAppearanceMode(mode) }
    }

    fun setShowBuddy(enabled: Boolean) {
        viewModelScope.launch { repository.setShowBuddy(enabled) }
    }

    fun setMorningReminderEnabled(enabled: Boolean) {
        _uiState.update { it.copy(morningReminderEnabled = enabled) }
        viewModelScope.launch {
            morningReminderMutex.withLock {
                try {
                    repository.setMorningReminderEnabled(enabled)
                    latestSavedAppSettings = latestSavedAppSettings.copy(morningReminderEnabled = enabled)
                    _uiState.update {
                        it.copy(
                            morningReminderEnabled = enabled,
                            message = if (enabled) "晨间提醒已启用。" else "晨间提醒已关闭。",
                            messageIsError = false
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    _uiState.update {
                        it.copy(
                            morningReminderEnabled = latestSavedAppSettings.morningReminderEnabled,
                            morningReminderTime = latestSavedAppSettings.morningReminderTime,
                            message = "晨间提醒更新失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
            }
        }
    }

    fun setEveningReminderEnabled(enabled: Boolean) {
        _uiState.update { it.copy(eveningReminderEnabled = enabled) }
        viewModelScope.launch {
            eveningReminderMutex.withLock {
                try {
                    repository.setEveningReminderEnabled(enabled)
                    latestSavedAppSettings = latestSavedAppSettings.copy(eveningReminderEnabled = enabled)
                    _uiState.update {
                        it.copy(
                            eveningReminderEnabled = enabled,
                            message = if (enabled) "晚间提醒已启用。" else "晚间提醒已关闭。",
                            messageIsError = false
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    _uiState.update {
                        it.copy(
                            eveningReminderEnabled = latestSavedAppSettings.eveningReminderEnabled,
                            eveningReminderTime = latestSavedAppSettings.eveningReminderTime,
                            message = "晚间提醒更新失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
            }
        }
    }

    fun setMedicationReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setMedicationReminderEnabled(enabled) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            message = if (enabled) "服药提醒已启用。" else "服药提醒已关闭。",
                            messageIsError = false
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            medicationReminderEnabled = latestSavedAppSettings.medicationReminderEnabled,
                            message = "服药提醒更新失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    suspend fun getWritableMedicationCalendars() = repository.getWritableMedicationCalendars()

    fun setMedicationCalendarSyncEnabled(
        enabled: Boolean,
        calendarId: Long? = null,
        includeDetails: Boolean = false
    ) {
        viewModelScope.launch {
            runCatching {
                val targetId = if (enabled) requireNotNull(calendarId) { "请选择系统日历" } else null
                repository.setMedicationCalendar(targetId, includeDetails)
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            message = if (enabled) {
                                "已将服药时间同步到所选日历。"
                            } else {
                                "已关闭日历同步，本应用创建的服药日程已清理。"
                            },
                            messageIsError = false
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            message = if (enabled) {
                                "日历同步失败：${throwable.message ?: "请稍后重试"}"
                            } else {
                                "日历同步更新未完成：${throwable.message ?: "请稍后重试"}"
                            },
                            messageIsError = true
                        )
                    }
                }
        }
    }

    fun addMedication(name: String, dosage: String, times: List<String>) {
        if (name.isBlank()) {
            _uiState.update { it.copy(message = "请填写药品名称。", messageIsError = true) }
            return
        }
        if (times.isEmpty()) {
            _uiState.update { it.copy(message = "请至少添加一个服药时间。", messageIsError = true) }
            return
        }
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            medRepo.addMedication(name, dosage, times)
                .onSuccess {
                    runCatching { repository.refreshReminders() }
                        .onSuccess {
                            _uiState.update { it.copy(message = "已添加「${name.trim()}」。", messageIsError = false) }
                        }
                        .onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            _uiState.update {
                                it.copy(
                                    message = "药品已保存，但提醒同步失败：" +
                                        (throwable.message ?: "请稍后在提醒设置中重试"),
                                    messageIsError = true
                                )
                            }
                        }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            message = "添加失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    fun updateMedication(id: Long, name: String, dosage: String, times: List<String>) {
        if (name.isBlank()) {
            _uiState.update { it.copy(message = "请填写药品名称。", messageIsError = true) }
            return
        }
        if (times.isEmpty()) {
            _uiState.update { it.copy(message = "请至少添加一个服药时间。", messageIsError = true) }
            return
        }
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            medRepo.updateMedication(id, name, dosage, enabled = true, times = times)
                .onSuccess {
                    runCatching { repository.refreshReminders() }
                        .onSuccess {
                            _uiState.update { it.copy(message = "「${name.trim()}」已更新。", messageIsError = false) }
                        }
                        .onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            _uiState.update {
                                it.copy(
                                    message = "药品已更新，但提醒同步失败：" +
                                        (throwable.message ?: "请稍后在提醒设置中重试"),
                                    messageIsError = true
                                )
                            }
                        }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            message = "保存失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    fun deleteMedication(id: Long) {
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            val result = deletionUndoManager?.deleteMedication { medRepo.deleteMedicationWithSnapshot(id) }
                ?: medRepo.deleteMedicationWithSnapshot(id)
            result
                .onSuccess { snapshot ->
                    runCatching { repository.refreshReminders() }
                        .onSuccess {
                            _uiState.update {
                                it.copy(
                                    message = "已删除「${snapshot.medication.name}」。",
                                    messageIsError = false
                                )
                            }
                        }
                        .onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            _uiState.update {
                                it.copy(
                                    message = "药品已删除，但提醒同步失败：" +
                                        (throwable.message ?: "请稍后在提醒设置中重试"),
                                    messageIsError = true
                                )
                            }
                        }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            message = "删除失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    fun showReminderAuthorizationRequired(message: String) {
        _uiState.update { it.copy(message = message, messageIsError = true) }
    }

    fun refreshReminders() {
        viewModelScope.launch {
            runCatching { repository.refreshReminders() }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            message = "提醒刷新失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    fun updateMorningTime(value: String) {
        if (!isTimeTextValid(value)) {
            _uiState.update { it.copy(message = "提醒时间格式应为 HH:mm，例如 07:30。", messageIsError = true) }
            return
        }
        val wasEnabled = _uiState.value.morningReminderEnabled
        if (_uiState.value.morningReminderTime == value && wasEnabled) return
        _uiState.update { it.copy(morningReminderTime = value, morningReminderEnabled = true) }
        viewModelScope.launch {
            morningReminderMutex.withLock {
                try {
                    repository.setMorningReminderSchedule(value, enabled = true)
                    latestSavedAppSettings = latestSavedAppSettings.copy(
                        morningReminderTime = value,
                        morningReminderEnabled = true
                    )
                    _uiState.update { state ->
                        state.copy(
                            morningReminderTime = value,
                            morningReminderEnabled = true,
                            message = if (wasEnabled) "晨间提醒时间已自动保存。" else "已自动开启晨间提醒并保存时间。",
                            messageIsError = false
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    _uiState.update { state ->
                        state.copy(
                            morningReminderTime = latestSavedAppSettings.morningReminderTime,
                            morningReminderEnabled = latestSavedAppSettings.morningReminderEnabled,
                            message = "自动保存失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
            }
        }
    }

    fun updateEveningTime(value: String) {
        if (!isTimeTextValid(value)) {
            _uiState.update { it.copy(message = "提醒时间格式应为 HH:mm，例如 19:30。", messageIsError = true) }
            return
        }
        val wasEnabled = _uiState.value.eveningReminderEnabled
        if (_uiState.value.eveningReminderTime == value && wasEnabled) return
        _uiState.update { it.copy(eveningReminderTime = value, eveningReminderEnabled = true) }
        viewModelScope.launch {
            eveningReminderMutex.withLock {
                try {
                    repository.setEveningReminderSchedule(value, enabled = true)
                    latestSavedAppSettings = latestSavedAppSettings.copy(
                        eveningReminderTime = value,
                        eveningReminderEnabled = true
                    )
                    _uiState.update { state ->
                        state.copy(
                            eveningReminderTime = value,
                            eveningReminderEnabled = true,
                            message = if (wasEnabled) "晚间提醒时间已自动保存。" else "已自动开启晚间提醒并保存时间。",
                            messageIsError = false
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    _uiState.update { state ->
                        state.copy(
                            eveningReminderTime = latestSavedAppSettings.eveningReminderTime,
                            eveningReminderEnabled = latestSavedAppSettings.eveningReminderEnabled,
                            message = "自动保存失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
            }
        }
    }

    fun updateName(value: String) {
        if (_uiState.value.isSavingProfile) return
        isProfileDirty = true
        _uiState.update { it.copy(name = value, isProfileDirty = true) }
    }

    fun updateAgeText(value: String) {
        if (_uiState.value.isSavingProfile) return
        isProfileDirty = true
        _uiState.update { it.copy(ageText = value, ageError = null, isProfileDirty = true) }
    }

    fun updateGender(value: String) {
        if (_uiState.value.isSavingProfile) return
        isProfileDirty = true
        _uiState.update { it.copy(gender = value, isProfileDirty = true) }
    }

    fun updateTargetSystolicText(value: String) {
        if (_uiState.value.isSavingProfile) return
        isProfileDirty = true
        _uiState.update { it.copy(targetSystolicText = value, targetSystolicError = null, isProfileDirty = true) }
    }

    fun updateTargetDiastolicText(value: String) {
        if (_uiState.value.isSavingProfile) return
        isProfileDirty = true
        _uiState.update { it.copy(targetDiastolicText = value, targetDiastolicError = null, isProfileDirty = true) }
    }

    fun discardProfileChanges() {
        if (_uiState.value.isSavingProfile) return
        isProfileDirty = false
        _uiState.update {
            it.copy(
                name = latestSavedProfile.name.orEmpty(),
                ageText = latestSavedProfile.age?.toString().orEmpty(),
                gender = latestSavedProfile.gender.orEmpty(),
                targetSystolicText = latestSavedProfile.targetSystolic?.toString().orEmpty(),
                targetDiastolicText = latestSavedProfile.targetDiastolic?.toString().orEmpty(),
                isProfileDirty = false,
                ageError = null,
                targetSystolicError = null,
                targetDiastolicError = null,
                message = "",
                messageIsError = false
            )
        }
    }

    fun saveUserProfile() {
        saveUserProfile(onSaved = null)
    }

    fun saveUserProfile(onSaved: (() -> Unit)?) {
        val state = _uiState.value
        if (!state.isProfileLoaded || state.isSavingProfile) return
        val age = state.ageText.toIntOrNull()
        val targetSys = state.targetSystolicText.toIntOrNull()
        val targetDia = state.targetDiastolicText.toIntOrNull()

        if (state.ageText.isNotBlank() && age == null) {
            _uiState.update { it.copy(ageError = "年龄应为整数。", message = "请检查用户资料。", messageIsError = true) }
            return
        }
        if (age != null && age !in 1..120) {
            _uiState.update { it.copy(ageError = "年龄须在 1–120 岁之间。", message = "请检查用户资料。", messageIsError = true) }
            return
        }
        if (state.targetSystolicText.isNotBlank() && targetSys == null) {
            _uiState.update { it.copy(targetSystolicError = "请输入有效整数。", message = "请检查用户资料。", messageIsError = true) }
            return
        }
        if (state.targetDiastolicText.isNotBlank() && targetDia == null) {
            _uiState.update { it.copy(targetDiastolicError = "请输入有效整数。", message = "请检查用户资料。", messageIsError = true) }
            return
        }
        if (targetSys != null && targetSys !in 40..300) {
            _uiState.update { it.copy(targetSystolicError = "达标高压须在 40–300 之间。", message = "请检查用户资料。", messageIsError = true) }
            return
        }
        if (targetDia != null && targetDia !in 20..200) {
            _uiState.update { it.copy(targetDiastolicError = "达标低压须在 20–200 之间。", message = "请检查用户资料。", messageIsError = true) }
            return
        }
        if (targetSys != null && targetDia != null && targetDia >= targetSys) {
            _uiState.update {
                it.copy(
                    targetDiastolicError = "达标低压必须低于达标高压。",
                    message = "请检查用户资料。",
                    messageIsError = true
                )
            }
            return
        }

        val savedProfile = UserProfile(
            name = state.name,
            age = age,
            gender = state.gender,
            targetSystolic = targetSys,
            targetDiastolic = targetDia
        )
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _uiState.update {
                it.copy(
                    isSavingProfile = true,
                    message = "",
                    messageIsError = false,
                    ageError = null,
                    targetSystolicError = null,
                    targetDiastolicError = null
                )
            }
            val release = updatePromptGate?.hold()
            try {
                repository.saveUserProfile(savedProfile)
                latestSavedProfile = savedProfile
                isProfileDirty = false
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        isProfileDirty = false,
                        message = "用户资料已保存。",
                        messageIsError = false,
                        ageError = null,
                        targetSystolicError = null,
                        targetDiastolicError = null
                    )
                }
                onSaved?.invoke()
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(isSavingProfile = false) }
                throw cancelled
            } catch (throwable: Throwable) {
                isProfileDirty = true
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        isProfileDirty = true,
                        message = "保存失败：${throwable.message ?: "请稍后重试"}",
                        messageIsError = true
                    )
                }
            } finally {
                release?.invoke()
            }
        }
    }

    fun requestBackupExport() {
        if (_uiState.value.isDataActionRunning) return
        if (pendingExportPlan != null) { showCurrentExportVolume(); return }
        _uiState.update { it.copy(showBackupExportConfirm = true) }
    }

    fun dismissBackupExport() {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(showBackupExportConfirm = false) }
    }

    fun requestEncryptedBackupExport() {
        if (_uiState.value.isDataActionRunning) return
        if (pendingExportPlan != null) { showCurrentExportVolume(); return }
        _uiState.update {
            it.copy(
                showBackupExportConfirm = false,
                showExportPassphraseDialog = true
            )
        }
    }

    fun dismissEncryptedBackupExport() {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(showExportPassphraseDialog = false) }
    }

    fun exportBackupXlsxToUri(uri: Uri, fileNameHint: String) {
        runExport(uri, fileNameHint, passphrase = null)
    }

    fun exportEncryptedBackupToUri(uri: Uri, fileNameHint: String, passphrase: CharArray) {
        runExport(uri, fileNameHint, passphrase)
    }

    private fun runExport(uri: Uri, fileNameHint: String, passphrase: CharArray?) {
        val action = if (passphrase != null) SettingsDataAction.EXPORT_BPX else SettingsDataAction.EXPORT_XLSX
        launchDataAction(action) {
            _uiState.update {
                it.copy(
                    showBackupExportConfirm = false,
                    showExportPassphraseDialog = false,
                    message = if (passphrase != null) {
                        "正在导出加密备份，请稍候…"
                    } else {
                        "正在导出 Excel 备份，请稍候…"
                    },
                    messageIsError = false
                )
            }
            repository.exportBackupXlsxToUri(uri, fileNameHint, passphrase)
                .onSuccess { msg ->
                    _uiState.update { it.copy(message = msg, messageIsError = false) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            message = throwable.message ?: "导出失败，请重新选择保存位置后再试",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    fun prepareBackupExport(baseName: String, passphrase: CharArray? = null) {
        if (_uiState.value.isDataActionRunning) {
            passphrase?.fill('\u0000')
            return
        }
        if (pendingExportPlan != null) {
            passphrase?.fill('\u0000')
            showCurrentExportVolume()
            return
        }
        pendingExportPassphrase?.fill('\u0000')
        pendingExportPassphrase = passphrase
        pendingExportBaseName = baseName
        val action = if (passphrase != null) SettingsDataAction.EXPORT_BPX else SettingsDataAction.EXPORT_XLSX
        launchDataAction(action) {
            _uiState.update { it.copy(showBackupExportConfirm = false, showExportPassphraseDialog = false) }
            repository.prepareBackupExport().onSuccess { plan ->
                pendingExportPlan = plan
                _uiState.update {
                    it.copy(
                        exportVolumeIndex = 0,
                        exportVolumeCount = plan.volumes.size,
                        exportTotalRecords = plan.totalRecords,
                        exportEncrypted = passphrase != null,
                        message = "已准备 ${plan.volumes.size} 个备份文件，请依次保存全部文件。",
                        messageIsError = false
                    )
                }
                showCurrentExportVolume()
            }.onFailure { error ->
                discardExportPlan()
                _uiState.update { it.copy(message = error.message ?: "备份准备失败", messageIsError = true) }
            }
        }
    }

    private fun showCurrentExportVolume() {
        val plan = pendingExportPlan ?: return
        val index = _uiState.value.exportVolumeIndex
        val extension = if (pendingExportPassphrase == null) "xlsx" else "bpx"
        val suffix = if (plan.volumes.size > 1) "_第${index + 1}卷_共${plan.volumes.size}卷" else ""
        val requestId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                exportVolumeRequestId = requestId,
                exportFileName = "$pendingExportBaseName$suffix.$extension",
                exportVolumeRecords = plan.volumes[index].measurements.size
            )
        }
    }

    fun onExportLocationResult(requestId: String, uri: Uri?) {
        val plan = pendingExportPlan ?: return
        val state = _uiState.value
        if (state.isDataActionRunning || state.exportVolumeRequestId != requestId) return
        val index = state.exportVolumeIndex
        if (uri == null) {
            _uiState.update {
                it.copy(
                    message = "保存已取消；已保存 $index/${plan.volumes.size} 卷。可继续保存当前卷。",
                    messageIsError = false
                )
            }
            showCurrentExportVolume()
            return
        }
        val action = if (pendingExportPassphrase != null) SettingsDataAction.EXPORT_BPX else SettingsDataAction.EXPORT_XLSX
        launchDataAction(action) {
            _uiState.update { it.copy(exportVolumeRequestId = null) }
            repository.exportBackupVolumeToUri(plan, index, uri, pendingExportPassphrase)
                .onSuccess {
                    if (index == plan.volumes.lastIndex) {
                        discardExportPlan()
                        _uiState.update {
                            it.copy(
                                message = "全部 ${plan.volumes.size} 卷已导出，共 ${plan.totalRecords} 条测量记录。请从第一卷起逐卷恢复。",
                                messageIsError = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                exportVolumeIndex = index + 1,
                                message = "已保存 ${index + 1}/${plan.volumes.size} 卷，备份尚未完整，请继续保存。",
                                messageIsError = false
                            )
                        }
                        showCurrentExportVolume()
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            message = error.message ?: "当前卷保存失败，请重试",
                            messageIsError = true
                        )
                    }
                    showCurrentExportVolume()
                }
        }
    }

    fun pauseBackupExport() {
        if (_uiState.value.isDataActionRunning || _uiState.value.isChoosingBackupLocation || pendingExportPlan == null) return
        _uiState.update {
            it.copy(
                exportVolumeRequestId = null,
                message = "备份未完成：已保存 ${it.exportVolumeIndex}/${it.exportVolumeCount} 卷。点击导出可继续；离开页面或重启后请重新导出全部卷。",
                messageIsError = false
            )
        }
    }

    fun requestClearAll() {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(showClearConfirm = true) }
    }

    fun requestBackupImport() {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(showBackupImportConfirm = true) }
    }

    fun dismissBackupImport() {
        if (_uiState.value.isDataActionRunning || _uiState.value.isChoosingBackupLocation) return
        resetBackupImportState()
    }

    private fun resetBackupImportState() {
        _uiState.update {
            it.copy(
                showBackupImportConfirm = false,
                backupImportPreview = null,
                importMeasurementsSelected = true,
                restoreUserProfileSelected = false,
                restoreDisplaySettingsSelected = false,
                restoreReminderSettingsSelected = false,
                restoreMedicationsSelected = false
            )
        }
    }

    fun importBackupXlsxFromUri(uri: Uri) {
        previewBackupXlsxFromUri(uri)
    }

    fun previewBackupXlsxFromUri(uri: Uri, passphrase: CharArray? = null) {
        launchDataAction(SettingsDataAction.IMPORT) {
            _uiState.update {
                it.copy(
                    showBackupImportConfirm = false,
                    message = "正在验证备份并生成导入预览…",
                    messageIsError = false
                )
            }
            repository.previewBackupXlsxFromUri(uri, passphrase)
                .onSuccess { preview ->
                    pendingImportUri = null
                    _uiState.update {
                        it.copy(
                            backupImportPreview = preview,
                            showImportPassphraseDialog = false,
                            importPassphraseError = null,
                            importMeasurementsSelected = preview.validRecordCount > 0,
                            restoreUserProfileSelected = false,
                            restoreDisplaySettingsSelected = false,
                            restoreReminderSettingsSelected = false,
                            restoreMedicationsSelected = false,
                            message = "预览完成：不会自动写入数据，请确认导入范围。",
                            messageIsError = false
                        )
                    }
                }
                .onFailure { throwable ->
                    when (throwable) {
                        is BackupPassphraseRequiredException -> {
                            pendingImportUri = uri
                            _uiState.update {
                                it.copy(
                                    showImportPassphraseDialog = true,
                                    importPassphraseError = null,
                                    message = "该备份文件已加密。",
                                    messageIsError = false
                                )
                            }
                        }
                        is BackupPassphraseException -> {
                            pendingImportUri = uri
                            _uiState.update {
                                it.copy(
                                    showImportPassphraseDialog = true,
                                    importPassphraseError = throwable.message,
                                    message = throwable.message ?: "备份口令错误。",
                                    messageIsError = true
                                )
                            }
                        }
                        else -> {
                            _uiState.update {
                                it.copy(
                                    message = "导入失败：${throwable.message ?: "文件格式不受支持"}",
                                    messageIsError = true
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
        if (_uiState.value.isDataActionRunning) return
        resetImportPassphraseState()
    }

    private fun resetImportPassphraseState() {
        pendingImportUri = null
        _uiState.update {
            it.copy(
                showImportPassphraseDialog = false,
                importPassphraseError = null
            )
        }
    }

    fun setImportMeasurementsSelected(selected: Boolean) {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(importMeasurementsSelected = selected) }
    }

    fun setRestoreUserProfileSelected(selected: Boolean) {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(restoreUserProfileSelected = selected) }
    }

    fun setRestoreDisplaySettingsSelected(selected: Boolean) {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(restoreDisplaySettingsSelected = selected) }
    }

    fun setRestoreReminderSettingsSelected(selected: Boolean) {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(restoreReminderSettingsSelected = selected) }
    }

    fun setRestoreMedicationsSelected(selected: Boolean) {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(restoreMedicationsSelected = selected) }
    }

    fun commitBackupImport() {
        val state = _uiState.value
        val preview = state.backupImportPreview ?: return
        if (!state.hasImportSelection || state.isDataActionRunning) return
        launchDataAction(SettingsDataAction.IMPORT) {
            _uiState.update {
                it.copy(message = "正在提交导入，请稍候…", messageIsError = false)
            }
            repository.commitBackupImport(
                preview = preview,
                options = BackupImportOptions(
                    importMeasurements = state.importMeasurementsSelected,
                    restoreUserProfile = state.restoreUserProfileSelected,
                    restoreDisplaySettings = state.restoreDisplaySettingsSelected,
                    restoreReminderSettings = state.restoreReminderSettingsSelected,
                    restoreMedications = state.restoreMedicationsSelected
                )
            ).onSuccess { message ->
                isProfileDirty = false
                _uiState.update {
                    it.copy(
                        backupImportPreview = null,
                        isProfileDirty = false,
                        message = message,
                        messageIsError = false
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        message = "导入失败：${throwable.message ?: "文件格式不受支持"}",
                        messageIsError = true
                    )
                }
            }
        }
    }

    fun dismissClearAll() {
        if (_uiState.value.isDataActionRunning) return
        _uiState.update { it.copy(showClearConfirm = false) }
    }

    fun confirmClearAll() {
        launchDataAction(SettingsDataAction.CLEAR) {
            _uiState.update { it.copy(message = "正在清空本地数据…", messageIsError = false) }
            repository.clearAllData()
                .onSuccess { result ->
                    if (result.databaseCleared) {
                        discardExportPlan()
                        resetBackupImportState()
                        resetImportPassphraseState()
                        isProfileDirty = false
                        latestSavedProfile = UserProfile()
                        _uiState.update {
                            it.copy(
                                name = "",
                                ageText = "",
                                gender = "",
                                targetSystolicText = "",
                                targetDiastolicText = "",
                                isProfileDirty = false,
                                ageError = null,
                                targetSystolicError = null,
                                targetDiastolicError = null
                            )
                        }
                    }
                    _uiState.update {
                        it.copy(
                            showClearConfirm = false,
                            message = result.toUserMessage(),
                            messageIsError = result.hasIssues
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            showClearConfirm = false,
                            message = "清空失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    private fun isTimeTextValid(value: String): Boolean {
        return Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(value)
    }
}
