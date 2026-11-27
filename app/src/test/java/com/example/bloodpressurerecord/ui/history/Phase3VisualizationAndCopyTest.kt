package com.example.bloodpressurerecord.ui.history

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.bloodpressurerecord.ui.theme.ChartPulseDark
import com.example.bloodpressurerecord.ui.theme.ChartPulseLight
import com.example.bloodpressurerecord.ui.theme.DarkBpGradePalette
import com.example.bloodpressurerecord.ui.theme.LightBpGradePalette
import com.example.bloodpressurerecord.ui.theme.Sage200
import com.example.bloodpressurerecord.ui.theme.Sage600
import com.example.bloodpressurerecord.ui.theme.Terracotta400
import com.example.bloodpressurerecord.ui.theme.TerracottaAction
import com.example.bloodpressurerecord.ui.theme.WarmNeutral900
import com.example.bloodpressurerecord.ui.theme.WarmSurface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3VisualizationAndCopyTest {

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val l1 = fg.luminance()
        val l2 = bg.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    @Test
    fun proportionalSegments_preserveExactCountRatiosWithoutArtificialMinWidth() {
        assertTrue(computeProportionalSegments(emptyList(), 360f).isEmpty())
        assertTrue(computeProportionalSegments(listOf(10, 20), 0f).isEmpty())

        val single = computeProportionalSegments(listOf(42), 300f)
        assertEquals(listOf(0f to 300f), single)

        // AUD-27 红线：1/184 极小占比段严禁被拉伸到 3dp/8dp 等人工最小宽度，宽度严格等于 368 * (1/184) = 2.0f
        val extreme = computeProportionalSegments(listOf(183, 1), 368f)
        assertEquals(2, extreme.size)
        assertEquals(0f, extreme[0].first, 0.001f)
        assertEquals(366f, extreme[0].second, 0.01f)
        assertEquals(extreme[0].second, extreme[1].first, 0.0001f)
        assertEquals(368f, extreme[1].second, 0.0001f)
        assertEquals(2f, extreme[1].second - extreme[1].first, 0.01f)

        // 多分类累计比例首尾严丝合缝，末端严格对齐容器总宽
        val thirds = computeProportionalSegments(listOf(1, 1, 1), 100f)
        assertEquals(0f, thirds.first().first, 0.0001f)
        assertEquals(100f, thirds.last().second, 0.0001f)
        assertEquals(thirds[0].second, thirds[1].first, 0.0001f)
        assertEquals(thirds[1].second, thirds[2].first, 0.0001f)
        val sumWidths = thirds.sumOf { (it.second - it.first).toDouble() }.toFloat()
        assertEquals(100f, sumWidths, 0.001f)
    }

    @Test
    fun targetRateSemantics_startsWithTitleInNaturalChineseOrder() {
        val withRate = buildTargetRateSemanticsDescription(
            targetRate = 78,
            targetText = "按单次记录计算：低于个人目标 135/85 且不偏低的占比。",
            comparison = "较上个周期（70%）提升 8 个百分点。"
        )
        assertTrue(withRate.startsWith("达标率，78%"))
        assertTrue(withRate.contains("135/85"))
        assertTrue(withRate.contains("提升 8 个百分点"))

        val withoutRate = buildTargetRateSemanticsDescription(
            targetRate = null,
            targetText = "在「我的」设置个人达标值后可查看达标率。",
            comparison = ""
        )
        assertTrue(withoutRate.startsWith("达标率，暂无"))
    }

    @Test
    fun trendTargetSummary_adaptsDynamicallyToMetricFilterAndTargets() {
        assertEquals("个人目标 135/85", buildTrendTargetSummary(true, true, 135, 85))
        assertEquals("高压目标 135", buildTrendTargetSummary(true, false, 135, 85))
        assertEquals("低压目标 85", buildTrendTargetSummary(false, true, 135, 85))
        assertEquals("高压目标 130", buildTrendTargetSummary(true, true, 130, null))
        assertEquals("低压目标 80", buildTrendTargetSummary(true, true, null, 80))
        assertNull(buildTrendTargetSummary(true, true, null, null))
    }

    @Test
    fun chartPulseToken_isDistinctFromSystolicAndHealthySage_andMeetsContrast() {
        assertEquals(ChartPulseLight, LightBpGradePalette.chartPulse)
        assertEquals(ChartPulseDark, DarkBpGradePalette.chartPulse)

        // 不绑定收缩压红与鼠尾草绿
        assertNotEquals(TerracottaAction, ChartPulseLight)
        assertNotEquals(Sage600, ChartPulseLight)
        assertNotEquals(Terracotta400, ChartPulseDark)
        assertNotEquals(Sage200, ChartPulseDark)
        assertTrue("Light pulse must differ clearly in blue channel from sage", abs(ChartPulseLight.blue - Sage600.blue) > 0.1f)

        // FRZ-02 静态对比度预核验（均远超非文本 3:1 与常规可读线 >= 4.5:1）
        val lightContrast = contrastRatio(ChartPulseLight, WarmSurface)
        val darkContrast = contrastRatio(ChartPulseDark, WarmNeutral900)
        assertTrue("ChartPulseLight on WarmSurface contrast=$lightContrast must be >= 4.5", lightContrast >= 4.5f)
        assertTrue("ChartPulseDark on DarkSurface contrast=$darkContrast must be >= 4.5", darkContrast >= 4.5f)
    }
}
