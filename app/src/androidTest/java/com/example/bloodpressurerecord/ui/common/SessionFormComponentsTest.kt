package com.example.bloodpressurerecord.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionFormComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun date_picker_opens_from_localized_date_button() {
        composeRule.setContent {
            BloodPressureRecordTheme {
                MeasurementDateTimePicker(
                    measuredAtText = "2026-07-25 09:30",
                    onMeasuredAtChange = {}
                )
            }
        }

        composeRule.onNodeWithText("2026年7月25日").performClick()
        composeRule.onNodeWithText("选择测量日期").assertIsDisplayed()
    }

    @Test
    fun time_picker_opens_from_time_button() {
        composeRule.setContent {
            BloodPressureRecordTheme {
                MeasurementDateTimePicker(
                    measuredAtText = "2026-07-25 09:30",
                    onMeasuredAtChange = {}
                )
            }
        }

        composeRule.onNodeWithText("09:30").performClick()
        composeRule.onNodeWithText("选择测量时间").assertIsDisplayed()
    }

    @Test
    fun extra_reading_delete_action_is_accessible() {
        var removed = false
        composeRule.setContent {
            BloodPressureRecordTheme {
                MeasurementReadingCard(
                    index = 2,
                    reading = SessionReadingInputUi("120", "80", "70"),
                    removable = true,
                    onSystolicChange = {},
                    onDiastolicChange = {},
                    onPulseChange = {},
                    onRemove = { removed = true }
                )
            }
        }

        composeRule.onNodeWithContentDescription("删除第3组读数").performClick()
        assertTrue(removed)
    }

    @Test
    fun save_button_is_disabled_when_readings_are_incomplete() {
        composeRule.setContent {
            BloodPressureRecordTheme {
                SessionSaveBottomBar(
                    canSave = false,
                    disabledReason = "至少填写两组有效读数后才能保存。",
                    isSaving = false,
                    buttonText = "保存记录",
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithText("保存记录").assertIsNotEnabled()
        composeRule.onNodeWithText("至少填写两组有效读数后才能保存。").assertIsDisplayed()
    }
}

