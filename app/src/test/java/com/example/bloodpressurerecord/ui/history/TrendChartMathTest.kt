package com.example.bloodpressurerecord.ui.history

import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendChartMathTest {
    @Test
    fun xAxis_usesRealTimeDistanceInsteadOfOrdinalIndex() {
        val start = 0L
        val end = 10_000L

        val earlyX = TrendChartMath.xOfTime(1_000L, 0f, 100f, start, end)
        val lateX = TrendChartMath.xOfTime(9_000L, 0f, 100f, start, end)

        assertEquals(10f, earlyX, 0.001f)
        assertEquals(90f, lateX, 0.001f)
    }

    /**
     * 可视窗口边界仍然按「排序后的时间」二分：窗口外的点只多带左右各一个，
     * 用来让折线连到绘图区边界。
     */
    @Test
    fun visiblePoints_usesSortedTimeWindowBoundaries() {
        val points = (0 until 10).map { point(timestamp = it * 1_000L) }

        val visible = TrendChartMath.visiblePoints(points, 2_500L, 6_000L)

        // 窗口内 3_000–6_000，外加左右各一个边界点。
        assertEquals(listOf(2_000L, 3_000L, 4_000L, 5_000L, 6_000L, 7_000L), visible.map { it.timestamp })
    }

    @Test
    fun visiblePoints_keepsDuplicateTimestampsAtWindowBoundary() {
        val points = listOf(
            point(timestamp = 1_000L, id = "a"),
            point(timestamp = 2_000L, id = "b"),
            point(timestamp = 2_000L, id = "c"),
            point(timestamp = 3_000L, id = "d")
        )

        val visible = TrendChartMath.visiblePoints(points, 2_000L, 2_000L)

        // 同刻度的点一个都不能漏，窗口外同样各带一个边界点。
        assertEquals(listOf("a", "b", "c", "d"), visible.map { it.id })
    }

    @Test
    fun visiblePoints_sortsUnorderedInputBeforeBinarySearchAndRendering() {
        val points = listOf(
            point(timestamp = 3_000L),
            point(timestamp = 1_000L),
            point(timestamp = 2_000L)
        )

        val visible = TrendChartMath.visiblePoints(points, 1_000L, 3_000L)

        assertEquals(listOf(1_000L, 2_000L, 3_000L), visible.map { it.timestamp })
    }

    @Test
    fun sharedSampling_keepsOneAndTwoSparsePointsUnchanged() {
        val one = listOf(point(timestamp = 1_000L))
        val two = one + point(timestamp = 2_000L)

        assertEquals(one, TrendChartMath.sampleShared(one, maxPoints = 60))
        assertEquals(two, TrendChartMath.sampleShared(two, maxPoints = 60))
    }

    @Test
    fun sharedSampling_preservesFirstLastAndBoundedPointCount() {
        val points = (0 until 10_000).map { index ->
            point(
                timestamp = index.toLong(),
                systolic = 110 + index % 70,
                diastolic = 60 + index % 45
            )
        }

        val sampled = TrendChartMath.sampleShared(points, maxPoints = 600)

        assertEquals(points.first(), sampled.first())
        assertEquals(points.last(), sampled.last())
        assertTrue(sampled.size <= 600)
        assertTrue(sampled.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun viewport_handlesSinglePointRangeWithoutInvalidBounds() {
        val viewport = TrendTimeViewportState()

        viewport.reset(
            domainStart = 5_000L,
            domainEnd = 5_001L,
            defaultStart = 5_000L,
            defaultEnd = 5_001L
        )
        viewport.zoomBy(zoomChange = 2f, focusRatio = 0.0, minSpanRatio = 0.01)

        assertEquals(5_000L, viewport.startMillis())
        assertEquals(5_001L, viewport.endMillis())
    }

    @Test
    fun viewport_panClampsAtBothDomainBoundaries() {
        val viewport = TrendTimeViewportState()
        viewport.reset(
            domainStart = 0L,
            domainEnd = 10_000L,
            defaultStart = 0L,
            defaultEnd = 10_000L
        )
        viewport.zoomBy(zoomChange = 2f, focusRatio = 0.5, minSpanRatio = 0.01)

        // 放大后向左拖过头：窗口停在数据域起点，不会拖出真实数据范围。
        viewport.panBy(deltaRatio = -100.0)
        assertEquals(0L, viewport.startMillis())
        // 向右拖过头：窗口右端停在数据域终点。
        viewport.panBy(deltaRatio = 100.0)
        assertEquals(10_000L, viewport.endMillis())
        // 平移不改变缩放倍数，窗口仍然是放大后的宽度。
        assertEquals(2f, viewport.zoom, 0.001f)
    }

    @Test
    fun viewport_zoomKeepsGestureCentreAnchored() {
        val viewport = TrendTimeViewportState()
        viewport.reset(
            domainStart = 0L,
            domainEnd = 10_000L,
            defaultStart = 0L,
            defaultEnd = 10_000L
        )
        // 以视野中心为缩放中心放大一倍：该点对应的时间不应移动。
        val focusRatio = 0.5
        val anchorBefore = viewport.startMillis() +
            (viewport.endMillis() - viewport.startMillis()) * focusRatio

        viewport.zoomBy(zoomChange = 2f, focusRatio = focusRatio, minSpanRatio = 0.01)

        assertEquals(2f, viewport.zoom, 0.05f)
        val anchorAfter = viewport.startMillis() +
            (viewport.endMillis() - viewport.startMillis()) * focusRatio
        assertTrue("缩放中心应保持不动", abs(anchorAfter - anchorBefore) <= 5L)
    }

    @Test
    fun viewport_zoomIsMonotonicAndBoundedByMaxZoom() {
        val viewport = TrendTimeViewportState()
        val oneYear = 365L * 24L * 60L * 60L * 1_000L
        viewport.reset(
            domainStart = 0L,
            domainEnd = oneYear,
            defaultStart = 0L,
            defaultEnd = oneYear
        )

        // 反复放大会单调收紧窗口，并最终被 MAX_ZOOM 拦住。
        var previousZoom = viewport.zoom
        repeat(40) {
            viewport.zoomBy(zoomChange = 5f, focusRatio = 0.5, minSpanRatio = 0.0000001)
            assertTrue("缩放应单调不降", viewport.zoom >= previousZoom - 0.001f)
            previousZoom = viewport.zoom
        }

        assertTrue("缩放倍数必须有上限", viewport.zoom <= TrendChartMath.MAX_ZOOM)
        assertEquals(TrendChartMath.MAX_ZOOM, viewport.zoom, 0.5f)
        assertTrue(viewport.endMillis() > viewport.startMillis())
        assertTrue(viewport.startMillis() >= 0L)
        assertTrue(viewport.endMillis() <= oneYear)
    }

    @Test
    fun viewport_doubleTapRestoresDefaultWindow() {
        val viewport = TrendTimeViewportState()
        viewport.reset(
            domainStart = 0L,
            domainEnd = 10_000L,
            defaultStart = 4_000L,
            defaultEnd = 6_000L
        )
        viewport.zoomBy(zoomChange = 3f, focusRatio = 0.5, minSpanRatio = 0.001)
        viewport.panBy(deltaRatio = 0.3)

        assertTrue(!viewport.isAtDefault)

        viewport.resetToDefault()

        assertTrue(viewport.isAtDefault)
        assertEquals(4_000L, viewport.startMillis())
        assertEquals(6_000L, viewport.endMillis())
    }

    @Test
    fun panIsNoOpAtDefaultZoom() {
        val viewport = TrendTimeViewportState()
        viewport.reset(
            domainStart = 0L,
            domainEnd = 10_000L,
            defaultStart = 0L,
            defaultEnd = 10_000L
        )

        viewport.panBy(deltaRatio = 0.4)

        assertEquals(0L, viewport.startMillis())
        assertEquals(10_000L, viewport.endMillis())
    }

    /**
     * 「上一条 / 下一条」把选中点带回视野：窗口宽度不变，目标时间落在正中，
     * 并且靠近数据域边界时不会把窗口拖出域外。
     */
    @Test
    fun centerOnRatio_keepsSpanAndClampsAtDomainEdges() {
        val viewport = TrendTimeViewportState()
        viewport.reset(
            domainStart = 0L,
            domainEnd = 1_000L,
            defaultStart = 0L,
            defaultEnd = 1_000L
        )
        viewport.zoomBy(zoomChange = 5f, focusRatio = 0.5, minSpanRatio = 0.05)
        val span = viewport.endMillis() - viewport.startMillis()
        assertEquals(200L, span)

        viewport.centerOnRatio(500L)
        assertEquals(400L, viewport.startMillis())
        assertEquals(600L, viewport.endMillis())

        viewport.centerOnRatio(900L)
        assertEquals(1_000L, viewport.endMillis())
        assertEquals(1_000L - span, viewport.startMillis())
    }

    /**
     * 视窗版本号只在窗口真的变化时自增：图表靠它区分「用户自己在缩放/平移」
     * 和「上一条 / 下一条换了选点」，避免选中一个点之后再也拖不动图表。
     */
    @Test
    fun viewportRevisionOnlyChangesWhenWindowActuallyMoves() {
        val viewport = TrendTimeViewportState()
        viewport.reset(
            domainStart = 0L,
            domainEnd = 10_000L,
            defaultStart = 0L,
            defaultEnd = 10_000L
        )

        viewport.zoomBy(zoomChange = 2f, focusRatio = 0.5, minSpanRatio = 0.01)
        val afterZoom = viewport.revision
        assertTrue(afterZoom > 0)

        // 已经在数据域边界还继续往外拖：窗口没动，版本号不应该再涨。
        viewport.panBy(deltaRatio = -100.0)
        viewport.panBy(deltaRatio = -100.0)
        val afterClampedPan = viewport.revision

        // 真正拖得动的一次平移必须让版本号变化。
        viewport.panBy(deltaRatio = 0.2)
        assertTrue(viewport.revision > afterClampedPan)

        // 「恢复默认」同样会改变窗口内容。
        val beforeReset = viewport.revision
        viewport.resetToDefault()
        assertTrue(viewport.revision > beforeReset)
        assertTrue(viewport.isAtDefault)
    }

    /**
     * 折线取「窗口内的点 + 左右各一个点」：曲线在缩放/平移时能连到绘图区边界，
     * 又不会像旧写法那样把窗口之外直到样本首尾的点全部喂给绘制层——那些点会被
     * 钳在 ±5% 处，在 Plot Area 之外堆成一条竖直的点列（折线越界的来源）。
     */
    @Test
    fun visiblePoints_extendsWindowByExactlyOnePointOnEachSide() {
        val points = (0L..50L).map { index -> point(timestamp = index * 20_000L) }

        val visible = TrendChartMath.visiblePoints(points, 300_000L, 500_000L)

        // 窗口内 11 个点（300s–500s），左右各外扩一个点。
        assertEquals(13, visible.size)
        assertEquals(280_000L, visible.first().timestamp)
        assertEquals(520_000L, visible.last().timestamp)
        assertTrue(visible.count { it.timestamp in 300_000L..500_000L } == 11)

        // 窗口远离样本边界时，不再吃进边界附近的点。
        assertEquals(points.size, TrendChartMath.visiblePoints(points, 0L, 1_000_000L).size)
        assertEquals(
            listOf(0L, 20_000L),
            TrendChartMath.visiblePoints(points, 0L, 0L).map { it.timestamp }
        )
    }

    @Test
    fun ticks_areChronologicalAndInsideViewport() {
        val start = 1_700_000_000_000L
        val end = start + 30L * 24L * 60L * 60L * 1_000L

        val ticks = TrendChartMath.timeTicks(start, end, ZoneId.of("Asia/Taipei"))

        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it.timestamp in start..end })
        assertTrue(ticks.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun tickCount_isDynamicAndBoundedByCanvasWidth() {
        assertEquals(3, TrendChartMath.maxTickCount(120))
        assertEquals(5, TrendChartMath.maxTickCount(400))
        assertEquals(7, TrendChartMath.maxTickCount(2_000))
    }

    @Test
    fun thirtyDayTicks_keepViewportStartAndEndVisible() {
        val zone = ZoneId.of("Asia/Taipei")
        val start = java.time.LocalDate.of(2026, 8, 23)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = java.time.LocalDate.of(2026, 9, 21)
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val ticks = TrendChartMath.timeTicks(start, end, zone, maxTicks = 7)

        assertEquals("08-23", ticks.first().primary)
        assertEquals("09-21", ticks.last().primary)
    }

    @Test
    fun monthlyTicks_useCompactLabelsAndKeepEveryMonthWhenTheyFit() {
        val zone = ZoneId.of("Asia/Taipei")
        val start = java.time.LocalDate.of(2026, 5, 1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = java.time.LocalDate.of(2026, 9, 21)
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val ticks = TrendChartMath.timeTicks(start, end, zone, maxTicks = 7)

        assertEquals(listOf("05月", "06月", "07月", "08月", "09月"), ticks.map { it.primary })
    }

    @Test
    fun tickCollisionFilter_keepsLabelsInsideBoundsAndSeparated() {
        val selected = TrendChartMath.nonOverlappingTickIndices(
            centers = listOf(0f, 25f, 50f, 75f, 100f),
            widths = listOf(30f, 30f, 30f, 30f, 30f),
            left = 0f,
            right = 100f,
            minimumGap = 4f
        )

        assertEquals(listOf(0, 2, 4), selected)
    }

    @Test
    fun tickCollisionFilter_keepsOnlyEndWhenBoundaryLabelsCannotFitTogether() {
        val selected = TrendChartMath.nonOverlappingTickIndices(
            centers = listOf(0f, 40f, 80f),
            widths = listOf(48f, 48f, 48f),
            left = 0f,
            right = 80f,
            minimumGap = 8f
        )

        assertEquals(listOf(2), selected)
    }

    /**
     * 最近 7 天只有 9/19–9/21 有记录时，默认视野应聚焦这三天，
     * 而不是把数据留在左侧、右侧空出一大片。
     */
    @Test
    fun defaultViewportFocusesSparseThreeDaySampleInsideSevenDayWindow() {
        val zone = ZoneId.of("Asia/Taipei")
        val windowStart = LocalDate.of(2026, 9, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = LocalDate.of(2026, 9, 25).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val points = listOf(
            point(timestamp = LocalDate.of(2026, 9, 19).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()),
            point(timestamp = LocalDate.of(2026, 9, 20).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()),
            point(timestamp = LocalDate.of(2026, 9, 21).atTime(20, 0).atZone(zone).toInstant().toEpochMilli())
        )

        val (start, end) = TrendChartMath.defaultViewport(
            points = points,
            range = TrendRange.DAYS_7,
            windowStart = windowStart,
            windowEndInclusive = windowEnd
        )

        val spanDays = (end - start) / (24.0 * 60 * 60 * 1000)
        val minSpanDays = TrendChartMath.minDefaultViewportSpan(TrendRange.DAYS_7) /
            (24.0 * 60 * 60 * 1000)
        // 聚焦到样本附近（最多 5 天），又不是把三天压成一条线。
        assertTrue("默认视野跨度应小于 5 天，实际 $spanDays", spanDays < 5.0)
        assertTrue(
            "默认视野不应小于可辨识窗口 $minSpanDays 天，实际 $spanDays",
            spanDays >= minSpanDays
        )
        assertTrue(start <= points.first().timestamp)
        assertTrue(end >= points.last().timestamp)
        assertTrue(start >= windowStart)
        assertTrue(end <= windowEnd)
        // 左右 padding 对称且有限，数据不会被挤到某一侧。
        val leftPadding = points.first().timestamp - start
        val rightPadding = end - points.last().timestamp
        assertTrue(abs(leftPadding - rightPadding) <= 60_000L)
        assertTrue(leftPadding in 1L..(24L * 60L * 60L * 1000L))
    }

    @Test
    fun defaultViewportKeepsFullWindowWhenSampleCoversWholeMonth() {
        val zone = ZoneId.of("Asia/Taipei")
        val windowStart = LocalDate.of(2026, 8, 23).atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = LocalDate.of(2026, 9, 21).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val points = (0L until 30L).map { day ->
            point(
                timestamp = windowStart + day * 24L * 60L * 60L * 1000L + 8L * 60L * 60L * 1000L
            )
        }

        val (start, end) = TrendChartMath.defaultViewport(
            points = points,
            range = TrendRange.DAYS_30,
            windowStart = windowStart,
            windowEndInclusive = windowEnd
        )

        assertEquals(windowStart, start)
        assertEquals(windowEnd, end)
    }

    @Test
    fun defaultViewportHandlesEmptyAndSinglePointWithoutInvalidBounds() {
        val zone = ZoneId.of("Asia/Taipei")
        val windowStart = LocalDate.of(2026, 9, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = LocalDate.of(2026, 9, 25).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

        val empty = TrendChartMath.defaultViewport(
            points = emptyList(),
            range = TrendRange.DAYS_7,
            windowStart = windowStart,
            windowEndInclusive = windowEnd
        )
        assertEquals(windowStart, empty.first)
        assertEquals(windowEnd, empty.second)

        val singleMillis = LocalDate.of(2026, 9, 24).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val single = TrendChartMath.defaultViewport(
            points = listOf(point(timestamp = singleMillis)),
            range = TrendRange.DAYS_7,
            windowStart = windowStart,
            windowEndInclusive = windowEnd
        )
        assertTrue(single.first < single.second)
        assertTrue(single.first <= singleMillis)
        assertTrue(single.second >= singleMillis)
        assertTrue(single.first >= windowStart)
        assertTrue(single.second <= windowEnd)
    }

    /** 跨月、跨年的聚焦视野仍能给出可读刻度。 */
    @Test
    fun focusedViewportProducesReadableTicksAcrossMonthAndYearBoundaries() {
        val zone = ZoneId.of("Asia/Taipei")
        val crossMonthStart = LocalDate.of(2026, 8, 30).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        val crossMonthEnd = LocalDate.of(2026, 9, 3).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        val crossMonthTicks = TrendChartMath.timeTicks(crossMonthStart, crossMonthEnd, zone, maxTicks = 7)

        assertTrue(crossMonthTicks.size >= 2)
        assertTrue(crossMonthTicks.all { it.timestamp in crossMonthStart..crossMonthEnd })
        assertTrue(crossMonthTicks.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })

        val crossYearStart = LocalDate.of(2025, 12, 30).atStartOfDay(zone).toInstant().toEpochMilli()
        val crossYearEnd = LocalDate.of(2026, 1, 3).atStartOfDay(zone).toInstant().toEpochMilli()
        val crossYearTicks = TrendChartMath.timeTicks(crossYearStart, crossYearEnd, zone, maxTicks = 7)

        assertTrue(crossYearTicks.size >= 2)
        assertTrue(crossYearTicks.all { it.timestamp in crossYearStart..crossYearEnd })
    }

    @Test
    fun timeAtXAndXOfTimeKeepRealTimeProportionsInsideFocusedViewport() {
        val start = 1_700_000_000_000L
        val end = start + 3L * 24L * 60L * 60L * 1000L
        val middle = start + (end - start) / 2L

        val middleX = TrendChartMath.xOfTime(middle, 0f, 200f, start, end)

        assertEquals(100f, middleX, 0.01f)
        assertEquals(middle.toFloat(), TrendChartMath.timeAtX(middleX, 0f, 200f, start, end).toFloat(), 1f)
    }

    @Test
    fun hitTest_prefersNodeInsideTouchRadiusAndFallsBackToNearestX() {
        val visible = listOf(
            point(timestamp = 0L, id = "a", systolic = 120, diastolic = 80),
            point(timestamp = 5_000L, id = "b", systolic = 150, diastolic = 95)
        )
        val chart = projection(visibleStart = 0L, visibleEnd = 5_000L)

        // 触点落在 b 的收缩压节点附近（热区内），应命中 b。
        val nodeX = chart.xOfTime(5_000L)
        val nodeY = chart.yOfValue(150)
        assertEquals(
            "b",
            TrendChartMath.hitTest(
                projection = chart,
                visible = visible,
                x = nodeX + 6f,
                y = nodeY + 6f,
                showSystolic = true,
                showDiastolic = true,
                touchRadiusPx = 22f
            )?.id
        )

        // 触点远离任何节点：退化到「按 X 最近的可见点」。
        assertEquals(
            "a",
            TrendChartMath.hitTest(
                projection = chart,
                visible = visible,
                x = chart.geometry.left + 1f,
                y = chart.geometry.top,
                showSystolic = true,
                showDiastolic = true,
                touchRadiusPx = 22f
            )?.id
        )
    }

    @Test
    fun stableYAxis_neverClipsVisibleDataAndKeepsTenMillimetreSteps() {
        val previous = TrendYAxis(min = 60, max = 160, tickStep = 10)

        // 常规数据：刻度间隔保持 10 mmHg。
        val normal = listOf(
            point(timestamp = 0L, systolic = 128, diastolic = 82),
            point(timestamp = 1_000L, systolic = 134, diastolic = 86)
        )
        val normalAxis = TrendChartMath.stableYAxis(previous, normal)
        assertEquals(10, normalAxis.tickStep)
        assertTrue(normal.all { it.systolic in normalAxis.min..normalAxis.max })
        assertTrue(normal.all { it.diastolic in normalAxis.min..normalAxis.max })
        // 参考线 90 / 140 落在默认视野内，参考虚线仍然可见。
        assertTrue(140 in normalAxis.min..normalAxis.max)
        assertTrue(90 in normalAxis.min..normalAxis.max)

        // 超出默认范围的数据必须自动扩展，不能被裁切。
        val extreme = listOf(
            point(timestamp = 0L, systolic = 280, diastolic = 170),
            point(timestamp = 1_000L, systolic = 95, diastolic = 45)
        )
        val extremeAxis = TrendChartMath.stableYAxis(previous, extreme)
        assertTrue(extreme.all { it.systolic in extremeAxis.min..extremeAxis.max })
        assertTrue(extreme.all { it.diastolic in extremeAxis.min..extremeAxis.max })
        assertTrue(extremeAxis.max > previous.max)
        assertTrue(extremeAxis.min < previous.min)
    }

    @Test
    fun stableYAxis_keepsPreviousAxisWhileDataBarelyChanges() {
        val previous = TrendYAxis(min = 60, max = 160, tickStep = 10)
        val slightlyDifferent = listOf(
            point(timestamp = 0L, systolic = 126, diastolic = 80)
        )

        val axis = TrendChartMath.stableYAxis(previous, slightlyDifferent)

        // 变化未超过稳定带：沿用旧轴，拖动时不会逐帧抖动。
        assertEquals(previous, axis)
    }

    private fun point(
        timestamp: Long,
        id: String = "p-$timestamp",
        systolic: Int = 120,
        diastolic: Int = 80
    ): TrendPoint {
        return TrendPoint(
            id = id,
            timestamp = timestamp,
            intervalStart = timestamp,
            intervalEndExclusive = timestamp + 1,
            systolic = systolic,
            diastolic = diastolic,
            pulse = null,
            category = "NORMAL",
            containsHighRiskReading = false,
            recordCount = 1,
            aggregation = TrendAggregation.RAW
        )
    }

    private fun projection(
        visibleStart: Long,
        visibleEnd: Long,
        yAxis: TrendYAxis = TrendYAxis(min = 60, max = 160, tickStep = 10)
    ): ChartProjection {
        return ChartProjection(
            geometry = ChartGeometry(
                width = 400f,
                height = 260f,
                left = 40f,
                right = 380f,
                top = 20f,
                bottom = 220f
            ),
            domainStart = visibleStart,
            domainEnd = visibleEnd,
            startRatio = 0.0,
            endRatio = 1.0,
            yAxis = yAxis
        )
    }
}
