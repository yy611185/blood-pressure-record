package com.example.bloodpressurerecord.ui.history

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun slowHorizontalDragWithEveryFrameBelowSlopActuallyPansZoomedWindow() {
        val series = denseSeries()
        val controller = TrendChartController()
        var touchSlop = 0f
        composeRule.setContent {
            BloodPressureRecordTheme {
                touchSlop = LocalViewConfiguration.current.touchSlop
                SessionTimeSeriesDualLineChart(
                    series, null, {}, controller = controller,
                    modifier = Modifier.testTag("chart")
                )
            }
        }
        var before = 0L to 0L
        composeRule.runOnIdle {
            val viewport = controller.viewportFor(series.range)
            viewport.zoomBy(4f, 0.5, 0.001)
            before = viewport.startMillis() to viewport.endMillis()
        }
        composeRule.onNodeWithTag("chart").performTouchInput {
            val origin = center
            down(origin)
            repeat(24) { frame ->
                advanceEventTime(8L)
                moveTo(origin + Offset(touchSlop / 8f * (frame + 1), 0f))
            }
            up()
        }
        composeRule.runOnIdle {
            val viewport = controller.viewportFor(series.range)
            assertTrue("累计横移应平移视窗", viewport.startMillis() < before.first)
            assertEquals(before.second - before.first, viewport.endMillis() - viewport.startMillis())
        }
    }

    @Test
    fun verticalDragScrollsParentAndLeavesChartWindowUnchanged() {
        val series = denseSeries()
        val controller = TrendChartController()
        val parentScroll = ScrollState(0)
        var touchSlop = 0f
        composeRule.setContent {
            BloodPressureRecordTheme {
                touchSlop = LocalViewConfiguration.current.touchSlop
                Column(Modifier.fillMaxSize().verticalScroll(parentScroll)) {
                    SessionTimeSeriesDualLineChart(
                        series, null, {}, controller = controller,
                        modifier = Modifier.testTag("chart")
                    )
                    Spacer(Modifier.height(2_000.dp))
                }
            }
        }
        var before = 0L to 0L
        composeRule.runOnIdle {
            val viewport = controller.viewportFor(series.range)
            viewport.zoomBy(4f, 0.5, 0.001)
            before = viewport.startMillis() to viewport.endMillis()
        }
        composeRule.onNodeWithTag("chart").performTouchInput {
            val origin = center
            down(origin)
            repeat(12) { frame ->
                advanceEventTime(10L)
                moveTo(origin - Offset(0f, touchSlop / 3f * (frame + 1)))
            }
            up()
        }
        composeRule.runOnIdle {
            val viewport = controller.viewportFor(series.range)
            assertTrue("纵向拖动应让父页面滚动", parentScroll.value > 0)
            assertEquals(before, viewport.startMillis() to viewport.endMillis())
        }
    }

    private fun denseSeries() = TrendSeriesCalculator.build(
        (0 until 240).map { index ->
            TrendRecord("slow-$index", now - (240L - index) * 30L * 60L * 1_000L,
                120 + index % 10, 80 + index % 5, 70, "NORMAL")
        }, TrendRange.DAYS_7, now, zone
    )
}
