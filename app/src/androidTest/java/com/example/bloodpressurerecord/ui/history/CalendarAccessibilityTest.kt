package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun no_record_day_is_selectable_and_announces_today() {
        val date = LocalDate.of(2026, 7, 25)
        composeRule.setContent {
            BloodPressureRecordTheme(darkTheme = true) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 1.5f)
                ) {
                    CalendarDay(
                        date = date,
                        summary = null,
                        selected = false,
                        today = true,
                        currentDate = date,
                        onClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(
            "7月25日，今天，无记录，可选择"
        ).assertIsEnabled()
    }

    @Test
    fun today_and_selected_day_preserves_today_announcement_and_clicks_immediately() {
        val date = LocalDate.of(2026, 7, 25)
        var clicks = 0
        composeRule.setContent {
            BloodPressureRecordTheme {
                CalendarDay(
                    date = date,
                    summary = CalendarDaySummary(date, recordCount = 3, containsHighRisk = false),
                    selected = true,
                    today = true,
                    currentDate = date,
                    onClick = { clicks += 1 }
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "7月25日，今天，有3次测量，可选择，已选择"
        )
            .assertIsEnabled()
            .assertIsSelected()
            .performClick()
        // 移除 combinedClickable 后单击立即响应，无需等待双击判定超时。
        composeRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    @Test
    fun noted_day_singleClickSelects_andExplicitLocateNoteEntryInvokesCallback() {
        val date = LocalDate.of(2026, 7, 25)
        var dayClicks = 0
        var locateNoteClicks = 0
        val sampleSession = HistorySessionItemUi(
            id = "s-note-1",
            measuredAt = 1_790_340_000_000L,
            measuredDate = date,
            measuredAtText = "08:30",
            avgBloodPressureText = "122/78",
            avgPulseText = "68",
            scene = "清晨",
            category = "NORMAL",
            categoryText = "正常",
            noteSummary = "昨晚睡得很好",
            containsHighRiskReading = false
        )
        composeRule.setContent {
            BloodPressureRecordTheme {
                Column {
                    CalendarDay(
                        date = date,
                        summary = CalendarDaySummary(
                            date, recordCount = 1, containsHighRisk = false, hasNote = true
                        ),
                        selected = true,
                        today = date == LocalDate.of(2026, 7, 25),
                        currentDate = date,
                        onClick = { dayClicks += 1 }
                    )
                    SelectedDaySummary(
                        state = HistoryUiState(
                            selectedDate = date,
                            today = date,
                            selectedDayRecords = listOf(sampleSession)
                        ),
                        showLocateNote = true,
                        onLocateNote = { locateNoteClicks += 1 }
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(
            "7月25日，今天，有1次测量，可选择，含备注，已选择"
        ).performClick()
        composeRule.runOnIdle { assertEquals(1, dayClicks) }

        composeRule.onNodeWithText("7月25日 星期六 · 今天").assertIsDisplayed()
        composeRule.onNodeWithText("定位备注")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, locateNoteClicks) }
    }
}
