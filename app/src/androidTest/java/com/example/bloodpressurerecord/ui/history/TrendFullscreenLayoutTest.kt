package com.example.bloodpressurerecord.ui.history

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SettingsBundle
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.TrendRepository
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 在横屏设备上验证主图占比和读数位置，截图只使用固定测试记录。 */
class TrendFullscreenLayoutTest {
    @get:Rule val composeRule = createComposeRule()
    private val zone = ZoneId.systemDefault()
    private val date = LocalDate.of(2026, 10, 1)
    private val now = date.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()
    private val records = (0..26).map { index ->
        TrendRecord(
            id = "fixture-$index",
            measuredAt = date.minusDays(8L - index / 3)
                .atTime(6 + index % 3 * 3, 19).atZone(zone).toInstant().toEpochMilli(),
            systolic = if (index == 26) 124 else 116 + (index * 7 % 17),
            diastolic = if (index == 26) 86 else 77 + (index * 3 % 12),
            pulse = if (index == 26) 67 else 64 + (index % 11),
            category = "NORMAL_HIGH"
        )
    }

    @Test fun landscapeChartDominatesAndReadoutStaysAtTopRight() {
        render(dark = false, fontScale = 1f)
        capture("trend-fullscreen-light")
        verifyLayout()
    }

    @Test fun landscapeDarkLargeTextKeepsChartAndReadoutVisible() {
        render(dark = true, fontScale = 1.4f)
        capture("trend-fullscreen-dark-large")
        verifyLayout()
    }

    private fun render(dark: Boolean, fontScale: Float) {
        val series = TrendSeriesCalculator.build(records, TrendRange.DAYS_30, now, zone)
        composeRule.setContent {
            val density = LocalDensity.current
            val vm = remember { TrendViewModel(FixtureRepository(), fixtureSettings(), { now }, zone) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BloodPressureRecordTheme(darkTheme = dark) {
                    Surface {
                        TrendFullscreenScreen(
                            TrendUiState(series = series, selectedPointId = "fixture-26", fullscreen = true),
                            vm,
                            onOpenRecord = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun verifyLayout() {
        val screen = composeRule.onNodeWithTag("fullscreenTrend").fetchSemanticsNode().boundsInRoot
        val chart = composeRule.onNodeWithTag("fullscreenPressureChart").fetchSemanticsNode().boundsInRoot
        val readout = composeRule.onNodeWithTag("fullscreenReadout").fetchSemanticsNode().boundsInRoot
        assertTrue("Run this test with a landscape emulator", screen.width > screen.height)
        assertTrue("The chart must occupy most available height: chart=${chart.height}, screen=${screen.height}",
            chart.height >= screen.height * 0.65f)
        assertTrue("Readout belongs on the right", readout.center.x > screen.center.x)
        assertTrue("Readout stays above the plot", readout.bottom <= chart.top + 1f)
        assertTrue("Readout must fit inside the screen", readout.right <= screen.right + 1f)
        composeRule.onNodeWithText("每日范围").assertDoesNotExist()
        composeRule.onNodeWithText("上一条").assertDoesNotExist()
        composeRule.onNodeWithText("恢复").assertDoesNotExist()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureTrendReview") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "$name.png").outputStream().use {
            composeRule.onNodeWithTag("fullscreenTrend").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private inner class FixtureRepository : TrendRepository {
        override fun observeRecords(startInclusive: Long, endExclusive: Long) =
            flowOf(records.filter { it.measuredAt in startInclusive until endExclusive })
        override fun observeStatistics(startInclusive: Long, endExclusive: Long) =
            flowOf(PeriodStatistics())
        override suspend fun getRecords(startInclusive: Long, endExclusive: Long) =
            records.filter { it.measuredAt in startInclusive until endExclusive }
    }

    private fun fixtureSettings() = Proxy.newProxyInstance(
        SettingsRepository::class.java.classLoader, arrayOf(SettingsRepository::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "observeSettings" -> flowOf(SettingsBundle())
            "toString" -> "FixtureSettingsRepository"
            else -> error("Unexpected settings call: ${method.name}")
        }
    } as SettingsRepository
}
