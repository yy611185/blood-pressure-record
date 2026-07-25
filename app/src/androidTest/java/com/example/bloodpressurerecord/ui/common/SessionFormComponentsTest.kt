package com.example.bloodpressurerecord.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
        composeRule.onNodeWithContentDescription("24小时制时间输入，小时和分钟")
            .assertIsDisplayed()
    }

    @Test
    fun digital_time_cancel_does_not_submit() {
        var submitted = false
        composeRule.setContent {
            BloodPressureRecordTheme {
                DigitalTimeInputDialog(
                    title = "选择时间",
                    initialHour = 0,
                    initialMinute = 0,
                    onDismiss = {},
                    onConfirm = { _, _ -> submitted = true }
                )
            }
        }

        composeRule.onNodeWithText("取消").performClick()
        assertTrue(!submitted)
    }

    @Test
    fun digital_time_confirm_submits_initial_24_hour_value_once() {
        var submitted: Pair<Int, Int>? = null
        composeRule.setContent {
            BloodPressureRecordTheme {
                DigitalTimeInputDialog(
                    title = "选择时间",
                    initialHour = 23,
                    initialMinute = 59,
                    onDismiss = {},
                    onConfirm = { hour, minute -> submitted = hour to minute }
                )
            }
        }

        composeRule.onNodeWithText("确定").performClick()
        assertEquals(23 to 59, submitted)
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
