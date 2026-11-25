package com.example.bloodpressurerecord.ui.home

import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 首页「这一周」高低压悬浮区间图的自适应纵轴区间。
 *
 * 将纵轴收紧到本周真实高低压与正常参考线附近，避免正常血压（如 105/70–112/76）
 * 被压扁在图底并留下大片空白。
 */
internal data class WeekRangeAxis(
    val min: Int,
    val max: Int,
    val refSystolic: Int?,
    val refDiastolic: Int?
) {
    val span: Int
        get() = (max - min).coerceAtLeast(1)
}

internal data class CapsuleGeometry(
    val topPx: Float,
    val bottomPx: Float
)

internal object WeekRangeChartMath {
    private const val AXIS_PADDING_MMHG = 6
    private const val AXIS_STEP_MMHG = 5
    private const val MIN_AXIS_SPAN_MMHG = 50
    private const val ABSOLUTE_MIN_MMHG = 30

    fun computeAxis(
        days: List<DashboardWeekDay>,
        normalSysCut: Int = BpZoneConfig.Default.sys.cuts.normal,
        normalDiaCut: Int = BpZoneConfig.Default.dia.cuts.normal
    ): WeekRangeAxis {
        val validRefs = normalSysCut > normalDiaCut && normalDiaCut > 0
        val recorded = days.filter { it.hasReadings }
        if (recorded.isEmpty()) {
            val defaultMin = if (validRefs) (normalDiaCut - 15).coerceAtLeast(ABSOLUTE_MIN_MMHG) else 55
            val defaultMax = if (validRefs) normalSysCut + 15 else 135
            return WeekRangeAxis(
                min = defaultMin,
                max = defaultMax.coerceAtLeast(defaultMin + MIN_AXIS_SPAN_MMHG),
                refSystolic = normalSysCut.takeIf { validRefs },
                refDiastolic = normalDiaCut.takeIf { validRefs }
            )
        }

        val dataMin = recorded.minOf { minOf(it.averageSystolic!!, it.averageDiastolic!!) }
        val dataMax = recorded.maxOf { maxOf(it.averageSystolic!!, it.averageDiastolic!!) }

        // 将正常参考界值（如 120/80）纳入视窗，既能让参考虚线稳定可见，又避免极端偏离时把数据压扁。
        val rawMin = if (validRefs) minOf(dataMin, normalDiaCut) else dataMin
        val rawMax = if (validRefs) maxOf(dataMax, normalSysCut) else dataMax

        var min = floorToStep((rawMin - AXIS_PADDING_MMHG).coerceAtLeast(ABSOLUTE_MIN_MMHG))
        var max = ceilToStep(rawMax + AXIS_PADDING_MMHG)

        if (max - min < MIN_AXIS_SPAN_MMHG) {
            val missing = MIN_AXIS_SPAN_MMHG - (max - min)
            val expandDown = missing / 2
            val expandUp = missing - expandDown
            min = floorToStep((min - expandDown).coerceAtLeast(ABSOLUTE_MIN_MMHG))
            max = ceilToStep(max + expandUp)
        }

        return WeekRangeAxis(
            min = min,
            max = max,
            refSystolic = normalSysCut.takeIf { validRefs && it in (min + 1) until max },
            refDiastolic = normalDiaCut.takeIf { validRefs && it in (min + 1) until max }
        )
    }

    /** 将血压值映射到绘图区内的 Y 像素坐标（值越大越靠上）。 */
    fun valueToY(
        value: Int,
        axis: WeekRangeAxis,
        plotTopPx: Float,
        plotHeightPx: Float
    ): Float {
        if (plotHeightPx <= 0f) return plotTopPx
        val fraction = ((axis.max - value).toFloat() / axis.span.toFloat()).coerceIn(0f, 1f)
        return plotTopPx + fraction * plotHeightPx
    }

    /**
     * 计算单日高低压悬浮胶囊的上下像素边界。
     * 当脉压差极小时以中点对称撑开到 [minCapsuleHeightPx]，确保实心高压点与空心低压环不重叠。
     */
    fun computeCapsuleGeometry(
        systolic: Int,
        diastolic: Int,
        axis: WeekRangeAxis,
        plotTopPx: Float,
        plotHeightPx: Float,
        minCapsuleHeightPx: Float
    ): CapsuleGeometry {
        val high = maxOf(systolic, diastolic)
        val low = minOf(systolic, diastolic)
        val rawTop = valueToY(high, axis, plotTopPx, plotHeightPx)
        val rawBottom = valueToY(low, axis, plotTopPx, plotHeightPx)
        val clampedMinHeight = minCapsuleHeightPx.coerceIn(0f, plotHeightPx.coerceAtLeast(0f))
        if (rawBottom - rawTop >= clampedMinHeight) {
            return CapsuleGeometry(topPx = rawTop, bottomPx = rawBottom)
        }
        val mid = (rawTop + rawBottom) / 2f
        val half = clampedMinHeight / 2f
        val plotBottomPx = plotTopPx + plotHeightPx
        val top = (mid - half).coerceIn(plotTopPx, (plotBottomPx - clampedMinHeight).coerceAtLeast(plotTopPx))
        val bottom = (top + clampedMinHeight).coerceAtMost(plotBottomPx)
        return CapsuleGeometry(topPx = top, bottomPx = bottom)
    }

    /**
     * 解析当前选中的日期：优先维持用户已选且仍有数据的日期，否则自动选中本周最近一个有记录的日期。
     */
    fun resolveSelectedDate(
        week: List<DashboardWeekDay>,
        currentSelected: LocalDate?
    ): LocalDate? {
        if (currentSelected != null && week.any { it.date == currentSelected && it.hasReadings }) {
            return currentSelected
        }
        return week.lastOrNull { it.hasReadings }?.date
    }

    private fun floorToStep(value: Int): Int =
        (floor(value.toDouble() / AXIS_STEP_MMHG).toInt()) * AXIS_STEP_MMHG

    private fun ceilToStep(value: Int): Int =
        (ceil(value.toDouble() / AXIS_STEP_MMHG).toInt()) * AXIS_STEP_MMHG
}
