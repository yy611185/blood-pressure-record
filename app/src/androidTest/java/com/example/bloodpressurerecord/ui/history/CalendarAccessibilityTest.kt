package com.example.bloodpressurerecord.ui.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun no_record_day_is_disabled_and_announces_today() {
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
                        onClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(
            "7月25日，今天，无记录，不可选择"
        ).assertIsNotEnabled()
    }

    @Test
    fun recorded_day_announces_count_can_click_and_selected_state() {
        val date = LocalDate.of(2026, 7, 25)
        var clicks = 0
        composeRule.setContent {
            BloodPressureRecordTheme {
                CalendarDay(
                    date = date,
                    summary = CalendarDaySummary(date, recordCount = 3, containsHighRisk = false),
                    selected = true,
                    today = false,
                    onClick = { clicks += 1 }
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "7月25日，有3条记录，可选择，已选择"
        )
            .assertIsEnabled()
            .assertIsSelected()
            .performClick()
        assertEquals(1, clicks)
    }
}

