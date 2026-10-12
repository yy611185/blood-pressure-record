package com.example.bloodpressurerecord.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.BuildConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UpdateCheckState { IDLE, CHECKING, CURRENT, AVAILABLE, FAILED }

data class UpdateUiState(
    val settings: UpdateSettings = UpdateSettings(),
    val manifest: UpdateManifest? = null,
    val checkState: UpdateCheckState = UpdateCheckState.IDLE,
    val checkMessage: String? = null,
    val download: UpdateDownloadState = UpdateDownloadState(),
    val installInProgress: Boolean = false,
    val installMessage: String? = null,
    val dialogVisible: Boolean = false,
    val required: Boolean = false
)

class UpdateViewModel(
    private val repository: UpdateRepository,
    private val preferences: UpdatePreferences,
    private val downloadManager: UpdateDownloadManager,
    private val currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var foreground = false
    private var promptAllowed = false
    private var initialized = false
    private var pendingInitialManual = false
    private var checkJob: Job? = null
    private var reminderJob: Job? = null
    private var manualRequested = false
    private var pendingManualPrompt = false
    private var activeManualDialogIdentity: String? = null
    private var promptedIdentity: String? = null
    private var deferredDialogIdentity: String? = null
    private var policySuppressedIdentity: String? = null
    private var failedAt: Long? = null
    private var autoStartedIdentity: String? = null
    private var policyPausedIdentity: String? = null
    private var explicitAutoDownloadRequest = false

    init {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                mutableState.update { it.copy(settings = settings) }
                downloadManager.setWifiOnly(settings.wifiOnly)
                if (!settings.autoDownloadEnabled) {
                    downloadManager.pauseAutomatic()
                }
                if (!initialized) {
                    val cached = repository.cachedManifest()
                    if (cached != null) {
                        acceptManifest(cached)
                    }
                    initialized = true
                    if (pendingInitialManual) {
                        pendingInitialManual = false
                        requestCheck(manual = true)
                    } else if (foreground) {
                        requestCheck(manual = false)
                    }
                }
                evaluateReminder()
            }
        }
        viewModelScope.launch {
            downloadManager.state.collect { download ->
                val previousPhase = mutableState.value.download.phase
                mutableState.update { it.copy(download = download) }
                if (download.phase != previousPhase && download.phase in setOf(
                        UpdateDownloadPhase.IDLE, UpdateDownloadPhase.READY, UpdateDownloadPhase.FAILED
                    )) {
                    evaluateReminder()
                }
            }
        }
    }

    fun onForegroundChanged(value: Boolean) {
        foreground = value
        downloadManager.setForeground(value)
        if (value) {
            if (initialized) {
                requestCheck(manual = false)
            }
            evaluateReminder()
        } else {
            reminderJob?.cancel()
            if (mutableState.value.dialogVisible) {
                deferredDialogIdentity = mutableState.value.manifest?.identity
            }
            mutableState.update { it.copy(dialogVisible = false) }
        }
    }

    fun setPromptAllowed(value: Boolean) {
        promptAllowed = value
        evaluateReminder()
    }

    fun checkManually() = requestCheck(manual = true)

    private fun requestCheck(manual: Boolean) {
        if (!initialized) {
            if (manual) {
                pendingInitialManual = true
            }
            return
        }
        if (!manual && !foreground) {
            return
        }
        if (checkJob?.isActive == true) {
            if (manual) {
                manualRequested = true
            }
            return
        }
        val now = clock()
        if (!manual && (!UpdatePolicy.shouldAutoCheck(mutableState.value.settings, now) ||
                failedAt?.let { now >= it && now - it < UpdatePolicy.FAILURE_BACKOFF_MS } == true)) {
            evaluateReminder()
            return
        }
        manualRequested = manual
        checkJob = viewModelScope.launch {
            mutableState.update { it.copy(checkState = UpdateCheckState.CHECKING, checkMessage = null) }
            try {
                val manifest = repository.check(clock)
                failedAt = null
                acceptManifest(manifest)
                pendingManualPrompt = manualRequested && UpdatePolicy.decision(manifest, currentVersionCode) != UpdateDecision.CURRENT
                evaluateReminder()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failedAt = clock()
                // A failed or invalid response is never treated as CURRENT. Only manual failures have text.
                mutableState.update {
                    it.copy(checkState = UpdateCheckState.FAILED, checkMessage = if (manualRequested) {
                        if (error is UpdateRequestException) error.message else "检查更新失败，请确认网络后重试"
                    } else null)
                }
            } finally {
                manualRequested = false
            }
        }
    }

    private fun acceptManifest(manifest: UpdateManifest) {
        val decision = UpdatePolicy.decision(manifest, currentVersionCode)
        mutableState.update {
            it.copy(
                manifest = manifest,
                required = decision == UpdateDecision.REQUIRED,
                checkState = if (decision == UpdateDecision.CURRENT) UpdateCheckState.CURRENT else UpdateCheckState.AVAILABLE,
                checkMessage = if (decision == UpdateDecision.CURRENT) "已是最新版本" else null,
                dialogVisible = if (decision == UpdateDecision.CURRENT) false else it.dialogVisible
            )
        }
    }

    private fun evaluateReminder() {
        reminderJob?.cancel()
        val state = mutableState.value
        val manifest = state.manifest ?: return
        if (!foreground) {
            return
        }
        val now = clock()
        val manualPrompt = pendingManualPrompt || activeManualDialogIdentity == manifest.identity
        val eligible = (state.settings.autoCheckEnabled || manualPrompt) &&
            UpdatePolicy.shouldPrompt(manifest, currentVersionCode, state.settings, now, manualPrompt)
        if (!eligible && UpdatePolicy.decision(manifest, currentVersionCode) != UpdateDecision.CURRENT) {
            policySuppressedIdentity = manifest.identity
        } else if (eligible && policySuppressedIdentity == manifest.identity) {
            promptedIdentity = null
            policySuppressedIdentity = null
        }
        val show = promptAllowed && eligible && (pendingManualPrompt || promptedIdentity != manifest.identity ||
            deferredDialogIdentity == manifest.identity || state.dialogVisible)
        if (state.dialogVisible && !show && eligible) {
            deferredDialogIdentity = manifest.identity
        }
        mutableState.update { it.copy(dialogVisible = show) }
        if (show) {
            promptedIdentity = manifest.identity
            if (pendingManualPrompt) {
                activeManualDialogIdentity = manifest.identity
            }
            pendingManualPrompt = false
            deferredDialogIdentity = null
        }
        // A failed task is retried only by an explicit user action, never in a preference emission loop.
        val canAutoDownload = (state.settings.autoCheckEnabled || manualPrompt || explicitAutoDownloadRequest) &&
            UpdatePolicy.shouldAutoDownload(manifest, currentVersionCode, state.settings, now)
        val canReplaceOldTask = state.download.manifest?.identity != manifest.identity &&
            state.download.phase in setOf(UpdateDownloadPhase.READY, UpdateDownloadPhase.FAILED)
        if (canAutoDownload && autoStartedIdentity != manifest.identity &&
            (state.download.phase == UpdateDownloadPhase.IDLE || canReplaceOldTask)) {
            autoStartedIdentity = manifest.identity
            explicitAutoDownloadRequest = false
            downloadAction {
                try { downloadManager.start(manifest, automatic = true) }
                finally {
                    if (downloadManager.state.value.manifest?.identity != manifest.identity) {
                        autoStartedIdentity = null
                    }
                }
            }
        } else if (canAutoDownload &&
            policyPausedIdentity == manifest.identity && state.download.phase == UpdateDownloadPhase.PAUSED) {
            policyPausedIdentity = null
            downloadAction { downloadManager.start(manifest, automatic = true) }
        }
        val deadline = when (UpdatePolicy.decision(manifest, currentVersionCode)) {
            UpdateDecision.OPTIONAL -> state.settings.snoozedUntil.takeIf { state.settings.snoozedVersionCode == manifest.versionCode }
            UpdateDecision.REQUIRED -> state.settings.requiredDismissedUntil.takeIf { state.settings.requiredDismissedVersionCode == manifest.versionCode }
            UpdateDecision.CURRENT -> null
        }
        if (state.settings.autoCheckEnabled && deadline != null && deadline > now) {
            reminderJob = viewModelScope.launch {
                delay(deadline - now)
                promptedIdentity = null
                downloadManager.setForeground(foreground)
                evaluateReminder()
            }
        }
    }

    fun download() {
        val manifest = mutableState.value.manifest ?: return
        policyPausedIdentity = null
        downloadAction { downloadManager.start(manifest, automatic = false) }
    }

    fun retryDownload() { policyPausedIdentity = null; downloadAction { downloadManager.retry() } }
    fun cancelDownload() { policyPausedIdentity = null; downloadAction { downloadManager.cancel() } }
    suspend fun verifiedFile(): File? = downloadManager.verifiedFile()

    /** Visible feedback for both the update Dialog and the Settings update card. */
    fun setInstallFeedback(inProgress: Boolean, message: String? = null) {
        mutableState.update {
            it.copy(installInProgress = inProgress, installMessage = message)
        }
    }

    fun snooze() {
        val manifest = mutableState.value.manifest ?: return
        if (mutableState.value.required) {
            dismissDialog()
            return
        }
        hidePrompt()
        policySuppressedIdentity = manifest.identity
        policyPausedIdentity = manifest.identity
        downloadManager.pauseAutomatic()
        preferenceAction { preferences.snooze(manifest.versionCode, clock() + UpdatePolicy.SNOOZE_MS) }
    }

    fun skip() {
        val manifest = mutableState.value.manifest ?: return
        if (mutableState.value.required) {
            return
        }
        hidePrompt()
        policySuppressedIdentity = manifest.identity
        policyPausedIdentity = manifest.identity
        downloadManager.pauseAutomatic()
        preferenceAction { preferences.skip(manifest.versionCode) }
    }

    fun dismissDialog() {
        val manifest = mutableState.value.manifest ?: return
        if (!mutableState.value.required) {
            snooze()
            return
        }
        hidePrompt()
        policySuppressedIdentity = manifest.identity
        policyPausedIdentity = manifest.identity
        downloadManager.pauseAutomatic()
        preferenceAction { preferences.dismissRequired(manifest.versionCode, clock() + UpdatePolicy.CHECK_INTERVAL_MS) }
    }

    private fun hidePrompt() {
        pendingManualPrompt = false
        activeManualDialogIdentity = null
        deferredDialogIdentity = null
        mutableState.update { it.copy(dialogVisible = false) }
    }

    fun setAutoCheckEnabled(value: Boolean) {
        preferenceAction {
            preferences.setAutoCheckEnabled(value)
            if (value && foreground) {
                // DataStore emits asynchronously; the explicit action supplies the new preference immediately.
                mutableState.update { it.copy(settings = it.settings.copy(autoCheckEnabled = true)) }
                requestCheck(manual = false)
            }
        }
    }

    fun setAutoDownloadEnabled(value: Boolean) {
        if (!value) {
            downloadManager.pauseAutomatic()
        }
        if (value) {
            autoStartedIdentity = null
            explicitAutoDownloadRequest = true
        }
        preferenceAction { preferences.setAutoDownloadEnabled(value) }
    }

    fun setWifiOnly(value: Boolean) {
        downloadManager.setWifiOnly(value)
        preferenceAction { preferences.setWifiOnly(value) }
    }

    private fun preferenceAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IOException) { mutableState.update { it.copy(checkMessage = "更新偏好保存失败，请重试") } }
        }
    }

    private fun downloadAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(checkMessage = "下载操作失败，请稍后重试") } }
        }
    }
}
