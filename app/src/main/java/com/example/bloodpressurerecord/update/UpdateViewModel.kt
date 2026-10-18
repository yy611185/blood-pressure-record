package com.example.bloodpressurerecord.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.BuildConfig
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
    val dialogVisible: Boolean = false,
    val required: Boolean = false
)

class UpdateViewModel(
    private val repository: UpdateRepository,
    private val preferences: UpdatePreferences,
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
    private var browserOpenedDuringCheckVersionCode: Long? = null
    private var pendingManualPrompt = false
    private var activeManualDialogIdentity: String? = null
    private var promptedIdentity: String? = null
    private var deferredDialogIdentity: String? = null
    private var policySuppressedIdentity: String? = null
    private var failedAt: Long? = null
    private var pendingBrowserDeferral: Pair<Long, Long>? = null

    init {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                val browserDeferral = pendingBrowserDeferral
                val effectiveSettings = if (browserDeferral == null) settings else {
                    val (versionCode, until) = browserDeferral
                    if (settings.snoozedVersionCode == versionCode && settings.snoozedUntil == until &&
                        settings.requiredDismissedVersionCode == versionCode && settings.requiredDismissedUntil == until) {
                        pendingBrowserDeferral = null
                    }
                    settings.withBrowserDeferral(versionCode, until)
                }
                mutableState.update { it.copy(settings = effectiveSettings) }
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
    }

    fun onForegroundChanged(value: Boolean) {
        foreground = value
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
                browserOpenedDuringCheckVersionCode = null
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
        browserOpenedDuringCheckVersionCode = null
        checkJob = viewModelScope.launch {
            mutableState.update { it.copy(checkState = UpdateCheckState.CHECKING, checkMessage = null) }
            try {
                val manifest = repository.check(clock)
                failedAt = null
                acceptManifest(manifest)
                pendingManualPrompt = manualRequested && browserOpenedDuringCheckVersionCode != manifest.versionCode &&
                    UpdatePolicy.decision(manifest, currentVersionCode) != UpdateDecision.CURRENT
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
        val deadline = when (UpdatePolicy.decision(manifest, currentVersionCode)) {
            UpdateDecision.OPTIONAL -> state.settings.snoozedUntil.takeIf { state.settings.snoozedVersionCode == manifest.versionCode }
            UpdateDecision.REQUIRED -> state.settings.requiredDismissedUntil.takeIf { state.settings.requiredDismissedVersionCode == manifest.versionCode }
            UpdateDecision.CURRENT -> null
        }
        if (state.settings.autoCheckEnabled && deadline != null && deadline > now) {
            reminderJob = viewModelScope.launch {
                delay(deadline - now)
                promptedIdentity = null
                evaluateReminder()
            }
        }
    }

    fun snooze() {
        val manifest = mutableState.value.manifest ?: return
        val required = mutableState.value.required
        val until = clock() + UpdatePolicy.SNOOZE_MS
        hidePrompt()
        policySuppressedIdentity = manifest.identity
        mutableState.update {
            it.copy(settings = if (required) {
                it.settings.copy(requiredDismissedVersionCode = manifest.versionCode, requiredDismissedUntil = until)
            } else {
                it.settings.copy(snoozedVersionCode = manifest.versionCode, snoozedUntil = until)
            })
        }
        preferenceAction {
            if (required) preferences.dismissRequired(manifest.versionCode, until)
            else preferences.snooze(manifest.versionCode, until)
        }
    }

    fun skip() {
        val manifest = mutableState.value.manifest ?: return
        if (mutableState.value.required) {
            return
        }
        hidePrompt()
        policySuppressedIdentity = manifest.identity
        mutableState.update { it.copy(settings = it.settings.copy(skippedVersionCode = manifest.versionCode)) }
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
        val until = clock() + UpdatePolicy.CHECK_INTERVAL_MS
        mutableState.update {
            it.copy(settings = it.settings.copy(requiredDismissedVersionCode = manifest.versionCode, requiredDismissedUntil = until))
        }
        preferenceAction { preferences.dismissRequired(manifest.versionCode, until) }
    }

    /** Called only after ACTION_VIEW succeeds, including for an external fallback channel. */
    fun onBrowserOpened(versionCode: Long) {
        if (versionCode <= currentVersionCode) return
        if (checkJob?.isActive == true) {
            // Consume the in-flight manual prompt for this version, while allowing a newer result.
            browserOpenedDuringCheckVersionCode = versionCode
        }
        val until = clock() + UpdatePolicy.SNOOZE_MS
        pendingBrowserDeferral = versionCode to until
        if (mutableState.value.manifest?.versionCode == versionCode) {
            hidePrompt()
            policySuppressedIdentity = mutableState.value.manifest?.identity
        }
        // Apply immediately so a foreground event cannot race the asynchronous DataStore write.
        mutableState.update {
            it.copy(settings = it.settings.withBrowserDeferral(versionCode, until), checkMessage = null)
        }
        preferenceAction { preferences.recordBrowserOpened(versionCode, until) }
        evaluateReminder()
    }

    fun onBrowserOpenFailed(message: String) {
        mutableState.update { it.copy(checkMessage = message) }
    }

    private fun UpdateSettings.withBrowserDeferral(versionCode: Long, until: Long) = copy(
        snoozedVersionCode = versionCode, snoozedUntil = until,
        requiredDismissedVersionCode = versionCode, requiredDismissedUntil = until
    )

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

    private fun preferenceAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IOException) { mutableState.update { it.copy(checkMessage = "更新偏好保存失败，请重试") } }
        }
    }

}
