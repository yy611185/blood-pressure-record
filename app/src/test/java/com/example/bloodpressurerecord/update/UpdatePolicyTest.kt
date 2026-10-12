package com.example.bloodpressurerecord.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val manifest = UpdateManifest.parse(requireNotNull(javaClass.getResource("/update/stable-schema1.json")).readText())
    private val now = 1_000_000_000L

    @Test fun `comparison always uses version code instead of display text`() {
        assertEquals(UpdateDecision.CURRENT, UpdatePolicy.decision(manifest.copy(version = "9.0.0", versionCode = 43), 43))
        assertEquals(UpdateDecision.CURRENT, UpdatePolicy.decision(manifest, 45))
        assertEquals(UpdateDecision.OPTIONAL, UpdatePolicy.decision(manifest.copy(version = "1.0.0"), 43))
        assertEquals(UpdateDecision.REQUIRED, UpdatePolicy.decision(manifest.copy(minSupportedVersionCode = 44), 43))
        assertEquals(UpdateDecision.OPTIONAL, UpdatePolicy.decision(manifest.copy(minSupportedVersionCode = 43), 43))
    }

    @Test fun `success throttles automatic checks for exactly twenty four hours`() {
        val settings = UpdateSettings(lastSuccessfulCheckAt = now)
        assertFalse(UpdatePolicy.shouldAutoCheck(settings, now + UpdatePolicy.CHECK_INTERVAL_MS - 1))
        assertTrue(UpdatePolicy.shouldAutoCheck(settings, now + UpdatePolicy.CHECK_INTERVAL_MS))
        assertTrue(UpdatePolicy.shouldAutoCheck(UpdateSettings(), now))
        assertFalse(UpdatePolicy.shouldAutoCheck(UpdateSettings(autoCheckEnabled = false), now))
        assertTrue(UpdatePolicy.shouldAutoCheck(settings, now - 1))
    }

    @Test fun `snooze expires after seventy two hours and does not suppress next version`() {
        val settings = UpdateSettings(snoozedVersionCode = 44, snoozedUntil = now + UpdatePolicy.SNOOZE_MS)
        assertFalse(UpdatePolicy.shouldPrompt(manifest, 43, settings, now + UpdatePolicy.SNOOZE_MS - 1))
        assertTrue(UpdatePolicy.shouldPrompt(manifest, 43, settings, now + UpdatePolicy.SNOOZE_MS))
        assertTrue(UpdatePolicy.shouldPrompt(manifest.copy(versionCode = 45), 43, settings, now))
        assertTrue(UpdatePolicy.shouldPrompt(manifest, 43, settings, now, manual = true))
    }

    @Test fun `skipped version does not suppress new versions or necessary updates`() {
        val settings = UpdateSettings(skippedVersionCode = 44, snoozedVersionCode = 44, snoozedUntil = now + 100)
        assertFalse(UpdatePolicy.shouldPrompt(manifest, 43, settings, now))
        assertTrue(UpdatePolicy.shouldPrompt(manifest.copy(versionCode = 45), 43, settings, now))
        assertTrue(UpdatePolicy.shouldPrompt(manifest.copy(minSupportedVersionCode = 44), 43, settings, now))
        assertTrue(UpdatePolicy.shouldPrompt(manifest, 43, settings, now, manual = true))
    }

    @Test fun `necessary prompt dismissal has its own expiry and manual bypass`() {
        val required = manifest.copy(minSupportedVersionCode = 44)
        val settings = UpdateSettings(requiredDismissedVersionCode = 44, requiredDismissedUntil = now + UpdatePolicy.CHECK_INTERVAL_MS)
        assertFalse(UpdatePolicy.shouldPrompt(required, 43, settings, now))
        assertTrue(UpdatePolicy.shouldPrompt(required, 43, settings, now + UpdatePolicy.CHECK_INTERVAL_MS))
        assertTrue(UpdatePolicy.shouldPrompt(required, 43, settings, now, manual = true))
    }

    @Test fun `automatic downloads require opt in and honor reminder and cancel policy`() {
        assertFalse(UpdatePolicy.shouldAutoDownload(manifest, 43, UpdateSettings(), now))
        val enabled = UpdateSettings(autoDownloadEnabled = true)
        assertTrue(UpdatePolicy.shouldAutoDownload(manifest, 43, enabled, now))
        assertFalse(UpdatePolicy.shouldAutoDownload(manifest, 43, enabled.copy(cancelledAutoDownloadVersionCode = 44), now))
        assertFalse(UpdatePolicy.shouldAutoDownload(manifest, 43, enabled.copy(skippedVersionCode = 44), now))
        assertTrue(UpdatePolicy.shouldAutoDownload(manifest.copy(versionCode = 45), 43, enabled.copy(cancelledAutoDownloadVersionCode = 44), now))
    }
}
