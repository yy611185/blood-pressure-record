package com.example.bloodpressurerecord.ui.settings

import com.example.bloodpressurerecord.data.repository.UserProfile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseNotesTest {
    @Test
    fun releaseNotes_keepPublishedHistoryAndLoadOnlyMatchingVersion() {
        val versions = AppReleaseNotes.notes.map { it.version }
        assertEquals(listOf("2.3.12", "2.3.11"), versions.take(2))
        assertEquals(versions.size, versions.distinct().size)
        val assets = File("src/main/assets")
        val load = { path: String ->
            assets.resolve(path).takeIf { it.isFile }?.readText()
        }
        assertEquals("2.3.11", AppReleaseNotes.forInstalledVersion("2.3.11-debug", load).first().version)
        val next = AppReleaseNotes.forInstalledVersion("2.3.13", load).first()
        assertEquals("2.3.13", next.version)
        assertTrue(next.changes.any { it.contains("确认打卡") })
    }

    @Test
    fun appInfoGuide_matchesNullDefaultTargetAndInAppMedicationConfirmation() {
        val defaultProfile = UserProfile()
        assertNull(defaultProfile.targetSystolic)
        assertNull(defaultProfile.targetDiastolic)

        val allGuideText = AppInfoContent.featureSections.flatMap { it.items }.joinToString("\n")
        assertFalse(
            "使用指南不得把家庭参考值 <135/85 mmHg 误写为默认个人达标值",
            allGuideText.contains("默认 <135/85")
        )
        assertTrue(allGuideText.contains("个人达标值默认未设置"))
        assertFalse(
            "使用指南不得宣称通知栏可直接盲打一键打卡",
            allGuideText.contains("通知栏均支持一键打卡")
        )
        assertTrue(allGuideText.contains("点按通知可在应用内核对具体药品"))
    }
}
