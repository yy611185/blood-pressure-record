package com.example.bloodpressurerecord.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.data.datastore.AppSettings
import com.example.bloodpressurerecord.data.datastore.StartupSettingsState
import com.example.bloodpressurerecord.data.datastore.observeStartupSettings
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesignTokensPhase1Test {

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    @Test
    fun lightAndDarkOutlineMeetNonTextContrastAndPreserveDarkHex() {
        assertEquals(Color(0xFF8A8177), LightColors.outline)
        assertEquals(Color(0xFFE3DDD5), LightColors.outlineVariant)
        assertEquals(Color(0xFF7F7973), DarkColors.outline)

        val lightVsSurface = contrastRatio(LightColors.outline, LightColors.surface)
        val lightVsBackground = contrastRatio(LightColors.outline, LightColors.background)
        val lightVsSurfaceVariant = contrastRatio(LightColors.outline, LightColors.surfaceVariant)
        assertTrue("Light outline vs surface=$lightVsSurface", lightVsSurface >= 3.0)
        assertTrue("Light outline vs background=$lightVsBackground", lightVsBackground >= 3.0)
        assertTrue("Light outline vs surfaceVariant=$lightVsSurfaceVariant", lightVsSurfaceVariant >= 3.0)

        val darkVsSurface = contrastRatio(DarkColors.outline, DarkColors.surface)
        val darkVsSurfaceVariant = contrastRatio(DarkColors.outline, DarkColors.surfaceVariant)
        assertTrue("Dark outline vs surface=$darkVsSurface", darkVsSurface >= 3.0)
        assertTrue("Dark outline vs surfaceVariant=$darkVsSurfaceVariant", darkVsSurfaceVariant >= 3.0)
    }

    @Test
    fun darkLowGradeInkUsesApprovedHexAndAllSixGradesMeetTextContrast() {
        assertEquals(Color(0xFFA3A0DE), DarkBpGradePalette.low.ink)
        assertEquals(Color(0xFF8E8BC8), DarkBpGradePalette.low.mid)

        val darkVisuals = listOf(
            "low" to DarkBpGradePalette.low,
            "normal" to DarkBpGradePalette.normal,
            "highNormal" to DarkBpGradePalette.highNormal,
            "stage1" to DarkBpGradePalette.stage1,
            "stage2" to DarkBpGradePalette.stage2,
            "stage3" to DarkBpGradePalette.stage3
        )
        for ((name, visual) in darkVisuals) {
            val blendedOnCard = visual.soft.compositeOver(DarkColors.surface)
            val blendedOnBg = visual.soft.compositeOver(DarkColors.background)
            val ratioCard = contrastRatio(visual.ink, blendedOnCard)
            val ratioBg = contrastRatio(visual.ink, blendedOnBg)
            assertTrue("Dark $name ink vs soft-on-surface=$ratioCard", ratioCard >= 4.5)
            assertTrue("Dark $name ink vs soft-on-background=$ratioBg", ratioBg >= 4.5)
        }
    }

    @Test
    fun appShapesFollowMonotonicLadder() {
        val size = Size(200f, 200f)
        val density = Density(1f)
        val xs = AppShapes.extraSmall.topStart.toPx(size, density)
        val sm = AppShapes.small.topStart.toPx(size, density)
        val md = AppShapes.medium.topStart.toPx(size, density)
        val lg = AppShapes.large.topStart.toPx(size, density)
        val xl = AppShapes.extraLarge.topStart.toPx(size, density)

        assertEquals(12f, xs, 0.01f)
        assertEquals(16f, sm, 0.01f)
        assertEquals(20f, md, 0.01f)
        assertEquals(26f, lg, 0.01f)
        assertEquals(32f, xl, 0.01f)
        assertTrue(xs < sm && sm < md && md < lg && lg < xl)
    }

    @Test
    fun appTypographyDefinesAllFifteenSlotsWithZeroChineseLetterSpacing() {
        assertEquals(58.sp, AppTypography.displayLarge.fontSize)
        assertEquals(62.sp, AppTypography.displayLarge.lineHeight)
        assertEquals((-1).sp, AppTypography.displayLarge.letterSpacing)
        assertEquals(NumberFontFamily, AppTypography.displayLarge.fontFamily)

        assertEquals(54.sp, AppTypography.displayMedium.fontSize)
        assertEquals(54.sp, AppTypography.displayMedium.lineHeight)

        assertEquals(34.sp, AppTypography.displaySmall.fontSize)
        assertEquals(40.sp, AppTypography.displaySmall.lineHeight)
        assertEquals(0.sp, AppTypography.displaySmall.letterSpacing)
        assertEquals(NumberFontFamily, AppTypography.displaySmall.fontFamily)

        assertEquals(30.sp, AppTypography.headlineLarge.fontSize)
        assertEquals(30.sp, AppTypography.headlineMedium.fontSize)
        assertEquals(24.sp, AppTypography.headlineSmall.fontSize)
        assertEquals(32.sp, AppTypography.headlineSmall.lineHeight)

        assertEquals(22.sp, AppTypography.titleLarge.fontSize)
        assertEquals(17.sp, AppTypography.titleMedium.fontSize)
        assertEquals(15.sp, AppTypography.titleSmall.fontSize)
        assertEquals(21.sp, AppTypography.titleSmall.lineHeight)

        assertEquals(16.sp, AppTypography.bodyLarge.fontSize)
        assertEquals(14.sp, AppTypography.bodyMedium.fontSize)
        assertEquals(12.sp, AppTypography.bodySmall.fontSize)

        assertEquals(14.sp, AppTypography.labelLarge.fontSize)
        assertEquals(13.sp, AppTypography.labelMedium.fontSize)
        assertEquals(12.sp, AppTypography.labelSmall.fontSize)
        assertEquals(16.sp, AppTypography.labelSmall.lineHeight)
        assertEquals(0.sp, AppTypography.labelSmall.letterSpacing)

        assertEquals(12.sp, BpRangeDimensions.labelSize)
    }

    @Test
    fun startupSettingsGateEmitsRealSettingsAndRecoversOnFailureOrTimeout() = runTest {
        val custom = AppSettings(
            largeTextEnabled = false,
            appearanceMode = "dark",
            showTrendChart = false
        )
        val normalState = observeStartupSettings(flowOf(custom)).first()
        assertTrue(normalState.isReady)
        assertFalse(normalState.usedFallback)
        assertEquals(custom, normalState.settings)

        val failedState = observeStartupSettings(
            flow { throw IOException("corrupted preferences") }
        ).first()
        assertTrue(failedState.isReady)
        assertTrue(failedState.usedFallback)
        assertEquals(AppSettings(), failedState.settings)

        var attempts = 0
        val recovered = observeStartupSettings(flow {
            if (++attempts == 1) throw IOException("temporary read failure")
            emit(custom)
        }).take(2).toList()
        assertTrue(recovered.first().usedFallback)
        assertEquals(StartupSettingsState(isReady = true, settings = custom), recovered.last())

        val collected = mutableListOf<StartupSettingsState>()
        val job = launch {
            observeStartupSettings(
                source = flow {
                    delay(3_000L)
                    emit(custom)
                },
                fallbackTimeoutMillis = 1_500L
            ).toList(collected)
        }
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(1, collected.size)
        assertTrue(collected[0].isReady)
        assertTrue(collected[0].usedFallback)

        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(2, collected.size)
        assertTrue(collected[1].isReady)
        assertFalse(collected[1].usedFallback)
        assertEquals(custom, collected[1].settings)
        job.cancel()
    }
}
