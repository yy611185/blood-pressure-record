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

    @Test fun `opening browser postpones optional and necessary reminders for seventy two hours`() {
        val settings = browserOpenedSettings()
        val required = manifest.copy(minSupportedVersionCode = 44)
        listOf(manifest, required).forEach { update ->
            assertFalse(UpdatePolicy.shouldPrompt(update, 43, settings, now))
            assertFalse(UpdatePolicy.shouldPrompt(update, 43, settings, now + UpdatePolicy.SNOOZE_MS - 1))
            assertTrue(UpdatePolicy.shouldPrompt(update, 43, settings, now + UpdatePolicy.SNOOZE_MS))
        }
    }

    @Test fun `manual check and higher version bypass browser reminder postponement`() {
        val settings = browserOpenedSettings()
        listOf(manifest, manifest.copy(minSupportedVersionCode = 44)).forEach { update ->
            assertTrue(UpdatePolicy.shouldPrompt(update, 43, settings, now, manual = true))
            assertTrue(UpdatePolicy.shouldPrompt(update.copy(versionCode = 45), 43, settings, now))
        }
    }

    @Test fun `opening browser does not change installed version decision`() {
        val settings = browserOpenedSettings()
        assertEquals(UpdateDecision.OPTIONAL, UpdatePolicy.decision(manifest, 43))
        assertEquals(UpdateDecision.REQUIRED, UpdatePolicy.decision(manifest.copy(minSupportedVersionCode = 44), 43))
        assertEquals(UpdateDecision.CURRENT, UpdatePolicy.decision(manifest, manifest.versionCode))
        assertFalse(UpdatePolicy.shouldPrompt(manifest, manifest.versionCode, settings, now, manual = true))
    }

    private fun browserOpenedSettings() = UpdateSettings(
        snoozedVersionCode = manifest.versionCode,
        snoozedUntil = now + UpdatePolicy.SNOOZE_MS,
        requiredDismissedVersionCode = manifest.versionCode,
        requiredDismissedUntil = now + UpdatePolicy.SNOOZE_MS
    )
}
