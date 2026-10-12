package com.example.bloodpressurerecord.update

enum class UpdateDecision { CURRENT, OPTIONAL, REQUIRED }

object UpdatePolicy {
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    const val SNOOZE_MS = 72L * 60 * 60 * 1000
    const val FAILURE_BACKOFF_MS = 15L * 60 * 1000

    fun decision(manifest: UpdateManifest, currentVersionCode: Long): UpdateDecision = when {
        manifest.versionCode <= currentVersionCode -> UpdateDecision.CURRENT
        manifest.minSupportedVersionCode?.let { currentVersionCode < it } == true -> UpdateDecision.REQUIRED
        else -> UpdateDecision.OPTIONAL
    }

    fun shouldAutoCheck(settings: UpdateSettings, now: Long): Boolean {
        val last = settings.lastSuccessfulCheckAt
        return settings.autoCheckEnabled && (last == null || now < last || now - last >= CHECK_INTERVAL_MS)
    }

    fun shouldPrompt(manifest: UpdateManifest, currentVersionCode: Long, settings: UpdateSettings, now: Long, manual: Boolean = false): Boolean {
        return when (decision(manifest, currentVersionCode)) {
            UpdateDecision.CURRENT -> false
            UpdateDecision.REQUIRED -> manual || settings.requiredDismissedVersionCode != manifest.versionCode ||
                expired(settings.requiredDismissedUntil, now)
            UpdateDecision.OPTIONAL -> manual || (settings.skippedVersionCode != manifest.versionCode &&
                (settings.snoozedVersionCode != manifest.versionCode || expired(settings.snoozedUntil, now)))
        }
    }

    fun shouldAutoDownload(manifest: UpdateManifest, currentVersionCode: Long, settings: UpdateSettings, now: Long): Boolean =
        settings.autoDownloadEnabled && settings.cancelledAutoDownloadVersionCode != manifest.versionCode &&
            shouldPrompt(manifest, currentVersionCode, settings, now)

    private fun expired(deadline: Long?, now: Long): Boolean = deadline == null || now >= deadline
}
