package com.example.bloodpressurerecord.update

internal data class InstallAttemptState(
    val preparing: Boolean = false,
    val permissionPending: Boolean = false,
    val installRequested: Boolean = false
)

internal fun canAttemptInstall(state: InstallAttemptState, promptAllowed: Boolean): Boolean =
    promptAllowed && !state.preparing && !state.permissionPending && !state.installRequested

/** Returning from a platform activity or entering READY releases both one-shot requests. */
internal fun resetInstallRequests(state: InstallAttemptState): InstallAttemptState =
    state.copy(permissionPending = false, installRequested = false)

/** Also releases preparation on every effect exit, including cancellation and a closed prompt gate. */
internal fun finishInstallAttempt(state: InstallAttemptState): InstallAttemptState =
    resetInstallRequests(state).copy(preparing = false)
