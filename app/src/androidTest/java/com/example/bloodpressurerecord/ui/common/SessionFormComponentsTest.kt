package com.example.bloodpressurerecord.ui.common

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionFormComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wheel_time_options_are_selectable_and_submit_the_tapped_value() {
        var submitted: Pair<Int, Int>? = null
        composeRule.setContent {
            BloodPressureRecordTheme {
                WheelTimePickerDialog("选择时间", 12, 30, {}, { hour, minute ->
                    submitted = hour to minute
                })
            }
        }
        composeRule.onNodeWithContentDescription("12 时").assertIsSelected()
        composeRule.onNodeWithContentDescription("13 时").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("13 时").assertIsSelected()
        composeRule.onNodeWithText("确定").performClick()
        assertEquals(13 to 30, submitted)
    }

    @Test
    fun wheel_time_large_text_fits_inside_selected_touch_target() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BloodPressureRecordTheme(darkTheme = true) {
                    WheelTimePickerDialog("选择时间", 23, 59, {}, { _, _ -> })
                }
            }
        }
        val option = composeRule.onNodeWithContentDescription("23 时")
        option.assertIsSelected().assertHeightIsAtLeast(48.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        val text = composeRule.onNodeWithText("23", useUnmergedTree = true)
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertTextFits(layouts)
        val textBounds = text.fetchSemanticsNode().boundsInRoot
        val optionBounds = option.fetchSemanticsNode().boundsInRoot
        assertTrue(textBounds.top >= optionBounds.top - 1f && textBounds.bottom <= optionBounds.bottom + 1f)
    }

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
        composeRule.onNodeWithContentDescription("24小时制时间选择，滑动滚轮选择小时和分钟")
            .assertIsDisplayed()
    }

    @Test
    fun wheel_time_cancel_does_not_submit() {
        var submitted = false
        composeRule.setContent {
            BloodPressureRecordTheme {
                WheelTimePickerDialog(
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
    fun wheel_time_confirm_submits_initial_24_hour_value_once() {
        var submitted: Pair<Int, Int>? = null
        composeRule.setContent {
            BloodPressureRecordTheme {
                WheelTimePickerDialog(
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
    fun measurement_fields_keep_minimum_height_for_three_digit_values() {
        composeRule.setContent {
            BloodPressureRecordTheme {
                MeasurementReadingCard(
                    index = 0,
                    reading = SessionReadingInputUi("111", "106", "186"),
                    removable = false,
                    onSystolicChange = {},
                    onDiastolicChange = {},
                    onPulseChange = {}
                )
            }
        }

        listOf("收缩压（高压）", "舒张压（低压）", "脉搏（选填）").forEach { label ->
            composeRule.onNodeWithContentDescription("第 1 组$label")
                .assertHeightIsAtLeast(64.dp)
        }
    }

    @Test
    fun measurement_fields_keep_minimum_height_with_large_font_scale() {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 1.5f)
            ) {
                BloodPressureRecordTheme {
                    MeasurementReadingCard(
                        index = 1,
                        reading = SessionReadingInputUi("156", "107", "110"),
                        removable = false,
                        onSystolicChange = {},
                        onDiastolicChange = {},
                        onPulseChange = {}
                    )
                }
            }
        }

        listOf("收缩压（高压）", "舒张压（低压）", "脉搏（选填）").forEach { label ->
            composeRule.onNodeWithContentDescription("第 2 组$label")
                .assertHeightIsAtLeast(64.dp)
        }
    }

    @Test
    fun reading_summary_and_three_digit_input_fit_on_narrow_large_text_screen() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BloodPressureRecordTheme(darkTheme = true) {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        MeasurementReadingCard(0, SessionReadingInputUi("156", "107", "110"),
                            false, {}, {}, {})
                    }
                }
            }
        }
        val input = composeRule.onNodeWithContentDescription("第 1 组收缩压（高压）")
        input.performScrollTo().assertIsDisplayed()
        // Measure the actual glyph layout: Android 14+ scales large text nonlinearly.
        val inputLayouts = mutableListOf<TextLayoutResult>()
        input.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(inputLayouts) }
        assertTrue(inputLayouts.isNotEmpty())
        val inputHeight = input.fetchSemanticsNode().boundsInRoot.height
        val minimumVerticalSpace = with(composeRule.density) { 24.dp.toPx() }
        assertTrue(inputLayouts.all { inputHeight >= it.size.height + minimumVerticalSpace })
        listOf("本组：156 / 107 mmHg", "2级高血压").forEach { label ->
            val node = composeRule.onNodeWithText(label, useUnmergedTree = true)
            node.performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertTextFits(layouts)
        }
    }

    private fun assertTextFits(layouts: List<TextLayoutResult>) {
        layouts.forEach { layout ->
            assertTrue("All text lines must be retained", !layout.multiParagraph.didExceedMaxLines)
            repeat(layout.lineCount) { line ->
                // Layout sizes use integer pixels; line metrics retain subpixel positions.
                assertTrue("Line right ${layout.getLineRight(line)} exceeds ${layout.size.width}",
                    layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue("Line bottom ${layout.getLineBottom(line)} exceeds ${layout.size.height}",
                    layout.getLineBottom(line) <= layout.size.height + 1f)
                assertTrue("Text must not be ellipsized", !layout.isLineEllipsized(line))
            }
        }
    }

    @Test
    fun save_button_is_disabled_when_readings_are_incomplete() {
        composeRule.setContent {
            BloodPressureRecordTheme {
                InlineSessionAction(
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
