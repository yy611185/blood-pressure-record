package com.example.bloodpressurerecord.ui.history

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TrendChartInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.of("Asia/Taipei")
    private val now = LocalDate.of(2026, 9, 26).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val records = listOf(
        TrendRecord("today", now - 3_600_000L, 125, 82, 72, "HIGH_NORMAL")
    )

    @Test
    fun allRangeRendersForFirstMeasurementToday() {
        val series = TrendSeriesCalculator.build(records, TrendRange.ALL, now, zone)
        composeRule.setContent {
            BloodPressureRecordTheme {
                SessionTimeSeriesDualLineChart(
                    series = series,
                    selectedPoint = null,
                    onPointSelected = {},
                    modifier = Modifier.testTag("chart")
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chart").assertIsDisplayed()
    }

    @Test
    fun doubleTapUsesResetControllerAndClearsSelection() {
        val series = TrendSeriesCalculator.build(records, TrendRange.DAYS_7, now, zone)
        val selection = mutableStateOf<TrendPoint?>(series.points.single())
        val controller = TrendChartController()
        composeRule.setContent {
            BloodPressureRecordTheme {
                SessionTimeSeriesDualLineChart(
                    series = series,
                    selectedPoint = selection.value,
                    onPointSelected = { selection.value = it },
                    controller = controller,
                    modifier = Modifier.testTag("chart")
                )
            }
        }
        composeRule.onNodeWithTag("chart").performTouchInput { doubleClick() }
        composeRule.runOnIdle {
            assertEquals(1, controller.resetToken)
            assertNull(selection.value)
        }
    }
}
