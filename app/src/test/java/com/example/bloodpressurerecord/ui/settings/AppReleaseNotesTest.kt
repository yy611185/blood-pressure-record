package com.example.bloodpressurerecord.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseNotesTest {
    @Test
    fun releaseNotes_containCurrentVersion() {
        assertTrue(AppReleaseNotes.hasCurrentVersion())
    }
}
