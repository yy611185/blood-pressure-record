package com.example.bloodpressurerecord.ui.home

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekRangeChartMathTest {
    private val monday = LocalDate.of(2026, 11, 23)

    @Test
    fun `normal readings focus y axis cleanly around data and reference cuts`() {
        val days = listOf(
            DashboardWeekDay(monday, averageSystolic = 112, averageDiastolic = 76, sessionCount = 2, recorded = true),
            DashboardWeekDay(monday.plusDays(1), averageSystolic = 111, averageDiastolic = 74, sessionCount = 2, recorded = true),
            DashboardWeekDay(monday.plusDays(2), averageSystolic = 105, averageDiastolic = 70, sessionCount = 3, recorded = true)
        )

        val axis = WeekRangeChartMath.computeAxis(days, normalSysCut = 120, normalDiaCut = 80)

        assertEquals(60, axis.min)
        assertEquals(130, axis.max)
        assertEquals(120, axis.refSystolic)
        assertEquals(80, axis.refDiastolic)

        // 112/76 应占据绘图区可观比例，不再被压扁在底部
        val capsule = WeekRangeChartMath.computeCapsuleGeometry(
            systolic = 112,
            diastolic = 76,
            axis = axis,
            plotTopPx = 20f,
            plotHeightPx = 100f,
            minCapsuleHeightPx = 16f
        )
        val capsuleHeight = capsule.bottomPx - capsule.topPx
        assertTrue("Capsule height should span ~51% of plot, was $capsuleHeight", capsuleHeight > 45f)
        assertTrue("Top of 112 should sit clearly below plot top", capsule.topPx > 20f && capsule.topPx < 60f)
    }

    @Test
    fun `hypertensive spike expands upper bound without clipping`() {
        val days = listOf(
            DashboardWeekDay(monday, averageSystolic = 172, averageDiastolic = 106, sessionCount = 2, recorded = true),
            DashboardWeekDay(monday.plusDays(1), averageSystolic = 122, averageDiastolic = 78, sessionCount = 1, recorded = true)
        )

        val axis = WeekRangeChartMath.computeAxis(days, normalSysCut = 120, normalDiaCut = 80)

        assertTrue("Max must include padding above 172", axis.max >= 178)
        assertTrue("Min must include padding below 78", axis.min <= 72)
        assertEquals(120, axis.refSystolic)
        assertEquals(80, axis.refDiastolic)
    }

    @Test
    fun `narrow pulse pressure enforces minimum capsule height inside plot bounds`() {
        val axis = WeekRangeAxis(min = 50, max = 180, refSystolic = 120, refDiastolic = 80)
        val geometry = WeekRangeChartMath.computeCapsuleGeometry(
            systolic = 104,
            diastolic = 96,
            axis = axis,
            plotTopPx = 10f,
            plotHeightPx = 100f,
            minCapsuleHeightPx = 20f
        )

        assertEquals(20f, geometry.bottomPx - geometry.topPx, 0.01f)
        assertTrue(geometry.topPx >= 10f)
        assertTrue(geometry.bottomPx <= 110f)
    }

    @Test
    fun `empty week produces sensible default axis`() {
        val days = (0L..6L).map { DashboardWeekDay(monday.plusDays(it), recorded = false) }
        val axis = WeekRangeChartMath.computeAxis(days, normalSysCut = 120, normalDiaCut = 80)

        assertEquals(65, axis.min)
        assertEquals(135, axis.max)
        assertEquals(120, axis.refSystolic)
        assertEquals(80, axis.refDiastolic)
    }

    @Test
    fun `resolveSelectedDate prefers current recorded day else falls back to latest recorded day`() {
        val days = listOf(
            DashboardWeekDay(monday, averageSystolic = 112, averageDiastolic = 76, recorded = true),
            DashboardWeekDay(monday.plusDays(1), recorded = false),
            DashboardWeekDay(monday.plusDays(2), averageSystolic = 105, averageDiastolic = 70, recorded = true)
        )

        // 无手动选中时默认取本周最近有记录的一天
        assertEquals(monday.plusDays(2), WeekRangeChartMath.resolveSelectedDate(days, null))
        // 用户点选周一后保持选中周一
        assertEquals(monday, WeekRangeChartMath.resolveSelectedDate(days, monday))
        // 选中无记录日期或跨周失效日期时自动回退到最近有记录日
        assertEquals(monday.plusDays(2), WeekRangeChartMath.resolveSelectedDate(days, monday.plusDays(1)))
        assertEquals(monday.plusDays(2), WeekRangeChartMath.resolveSelectedDate(days, monday.minusDays(7)))
        // 整周无记录返回 null
        assertNull(WeekRangeChartMath.resolveSelectedDate(listOf(DashboardWeekDay(monday)), null))
    }
}
