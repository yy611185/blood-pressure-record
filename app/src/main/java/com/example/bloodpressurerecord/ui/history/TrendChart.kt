package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import com.example.bloodpressurerecord.domain.model.displayLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 趋势图的复位入口。
 *
 * 「双击图表」和操作区里的「恢复」按钮共用同一个复位信号：两条路径各写一套
 * 重置逻辑迟早会漏掉某项状态（缩放、平移、视窗、Inspect、选中高亮），
 * 因此这里只保留一个版本计数，由图表内部统一消费。
 */
class TrendChartController(
    initialViewport: Pair<Long, Long>? = null,
    onViewportChanged: (Long, Long) -> Unit = { _, _ -> }
) {
    private var range: TrendRange? = null
    private var pendingViewport = initialViewport
    private var initialized = false
    private var latestMeasuredAt: Long? = null
    private val sharedViewport = TrendTimeViewportState(onViewportChanged)

    @Suppress("UNUSED_PARAMETER")
    internal fun viewportFor(requestedRange: TrendRange): TrendTimeViewportState = sharedViewport

    /** 两个面板、布局重建和数据刷新共用一次绝对时间窗口更新路径。 */
    internal fun updateSeries(series: TrendSeries) {
        val defaults = TrendChartMath.defaultViewport(
            series.points, series.range, series.rangeStart, series.rangeEnd
        )
        latestMeasuredAt = series.lastMeasuredAt ?: series.points.maxOfOrNull { it.timestamp }
        if (!initialized || (range != null && range != series.range)) {
            sharedViewport.reset(
                series.rangeStart, series.rangeEnd, defaults.first, defaults.second,
                restoredViewport = pendingViewport
            )
            pendingViewport = null
            initialized = true
        } else {
            sharedViewport.updateDomain(
                series.rangeStart, series.rangeEnd, defaults.first, defaults.second
            )
        }
        range = series.range
    }

    internal var resetToken by mutableStateOf(0)
        private set

    fun reset() {
        if (initialized) sharedViewport.resetToDefault()
        resetToken++
    }

    fun moveToLatest() {
        if (initialized) latestMeasuredAt?.let(sharedViewport::moveToLatest)
    }

    fun ensureVisible(timestamp: Long) {
        if (initialized) sharedViewport.ensureVisible(timestamp)
    }

    fun clearSavedViewport() {
        pendingViewport = null
        initialized = false
    }
}

@Composable
fun SessionTimeSeriesDualLineChart(
    series: TrendSeries,
    selectedPoint: TrendPoint?,
    onPointSelected: (TrendPoint?) -> Unit,
    modifier: Modifier = Modifier,
    controller: TrendChartController? = null,
    targetSystolic: Int? = null,
    targetDiastolic: Int? = null,
    showSystolic: Boolean = true,
    showDiastolic: Boolean = true,
    emptyTitle: String = "暂无趋势数据",
    chartHeight: androidx.compose.ui.unit.Dp = 252.dp,
    showHint: Boolean = true,
    showTimeLabels: Boolean = false,
    denseYAxis: Boolean = false
) {
    val points = remember(series.points) {
        series.points.sortedWith(compareBy<TrendPoint> { it.timestamp }.thenBy { it.id })
    }
    if (points.isEmpty()) {
        TrendEmptyState(title = emptyTitle, modifier = modifier)
        return
    }

    val density = LocalDensity.current
    val palette = trendChartPalette()
    val zoneId = remember { ZoneId.systemDefault() }
    val textMeasurer = rememberTextMeasurer()
    val viewport = controller?.viewportFor(series.range)
        ?: remember(series.range) { TrendTimeViewportState() }
    val gestureConfig = rememberChartGestureConfig()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var claimScroll by remember { mutableStateOf(false) }
    val sysPath = remember { Path() }
    val diaPath = remember { Path() }
    val haptics = LocalHapticFeedback.current

    // 数据域：7/30 天是自然周期窗口，「全部」是首条记录到当前时刻。
    // 视窗夹取在域内，因此平移天然不会拖出真实数据。
    val domainStart = series.rangeStart
    val domainEnd = series.rangeEnd
    val domainSpanMillis = (domainEnd - domainStart).coerceAtLeast(1L)

    // 复位计数：图表内部只认这一套重置逻辑，范围切换、双击复位与「恢复」按钮
    // 都走同一个副作用，避免出现多份互不一致的重置代码。
    val activeResetToken = controller?.resetToken ?: 0

    var localInitialized by remember(series.range) { mutableStateOf(false) }
    var appliedResetToken by remember { mutableIntStateOf(activeResetToken) }
    LaunchedEffect(series, activeResetToken) {
        if (controller != null) {
            controller.updateSeries(series)
        } else {
            val defaults = TrendChartMath.defaultViewport(points, series.range, domainStart, domainEnd)
            if (!localInitialized) {
                viewport.reset(domainStart, domainEnd, defaults.first, defaults.second)
                localInitialized = true
            } else if (activeResetToken != appliedResetToken) {
                viewport.resetToDefault()
            } else {
                viewport.updateDomain(domainStart, domainEnd, defaults.first, defaults.second)
            }
        }
        if (activeResetToken != appliedResetToken) {
            onPointSelected(null)
            claimScroll = false
        }
        appliedResetToken = activeResetToken
    }

    val viewportStart = viewport.startMillis()
    val viewportEnd = viewport.endMillis()
    val visiblePoints = remember(points, viewportStart, viewportEnd) {
        TrendChartMath.visiblePoints(
            points = points,
            startInclusive = viewportStart,
            endInclusive = viewportEnd,
            alreadySorted = true
        )
    }
    // 严格落在可视窗口内的点：节点密度、命中测试与竖向指示线取它，
    // 避免把窗口外的点在边界处钳成一条竖直的点列。
    val viewportPoints = remember(visiblePoints, viewportStart, viewportEnd) {
        visiblePoints.filter { it.timestamp in viewportStart..viewportEnd }
    }
    // Y 轴随可视数据自适应：初始取该范围自身的轴，之后只由「可视点 + 上一帧轴」
    // 决定（带滞回，避免拖动时逐帧抖动），始终不裁切可视数据。
    // 用 range 作 key，范围切换时回到该范围的初始轴。
    var yAxis by remember(series.range) { mutableStateOf(series.yAxis) }
    LaunchedEffect(visiblePoints, targetSystolic, targetDiastolic) {
        if (visiblePoints.isNotEmpty()) {
            yAxis = TrendChartMath.stableYAxis(
                yAxis,
                visiblePoints,
                targetSystolic,
                targetDiastolic
            )
        }
    }
    // 手势回调不在组合作用域内，用 rememberUpdatedState 读取最新的 Y 轴与选中点，
    // 否则 pointerInput 协程会一直用首次组合时的值做命中测试 / 触觉节流。
    val latestYAxis by rememberUpdatedState(yAxis)
    val latestSelectedPoint by rememberUpdatedState(selectedPoint)
    val maxDrawPoints = remember(canvasSize.width) {
        (canvasSize.width / 2).coerceIn(MIN_DRAW_POINTS, MAX_DRAW_POINTS)
    }
    // 只降低绘制密度，不删除真实数据：极值点全部保留。
    val renderPoints = remember(visiblePoints, maxDrawPoints, selectedPoint?.id) {
        TrendChartMath.sampleShared(visiblePoints, maxDrawPoints, selectedPoint?.id)
    }
    val segmentIds = remember(visiblePoints) {
        TrendChartMath.bloodPressureSegmentIds(visiblePoints, GAP_MILLIS)
    }
    val maxTicks = remember(canvasSize.width, density) {
        val plotWidthPx = if (canvasSize.width > 0) {
            (canvasSize.width - 52f * density.density).coerceAtLeast(40f)
        } else {
            0f
        }
        TrendChartMath.maxTickCount((plotWidthPx / density.density).roundToInt())
    }
    val axisTicks = remember(viewportStart, viewportEnd, maxTicks, zoneId, showTimeLabels) {
        TrendChartMath.timeTicks(
            startMillis = viewportStart,
            endMillis = viewportEnd,
            zoneId = zoneId,
            maxTicks = maxTicks,
            showTimeLabels = showTimeLabels
        )
    }
    // 日期与时间作为一个完整文本块测量，行高、底部留白和横向避让使用同一尺寸。
    val axisLabelStyle = TextStyle(color = palette.axis, fontSize = 12.sp, lineHeight = 16.sp)
    val axisLabelLayouts = remember(axisTicks, axisLabelStyle) {
        axisTicks.map { tick ->
            textMeasurer.measure(
                text = listOfNotNull(tick.primary, tick.secondary).joinToString("\n"),
                style = axisLabelStyle,
                softWrap = false
            )
        }
    }
    // 始终预留两行，缩放切换小时/日期刻度时绘图区和手势坐标保持不变。
    val axisBottomPadding = remember(axisLabelStyle, density) {
        with(density) {
            textMeasurer.measure("00:00\n00-00", axisLabelStyle, softWrap = false).size.height +
                16.dp.toPx()
        }
    }
    val chartDescription = remember(series, showSystolic, showDiastolic) {
        buildChartDescription(series, showSystolic, showDiastolic)
    }
    val scrollClaim = rememberChartScrollClaim { claimScroll }

    val geometry = remember(canvasSize, density, axisBottomPadding) {
        ChartGeometry.create(
            size = canvasSize,
            density = density.density,
            bottomPadding = axisBottomPadding
        )
    }
    val nodeTouchRadiusPx = remember(density) {
        with(density) { NODE_TOUCH_RADIUS.dp.toPx() }
    }
    val minSpanRatio = remember(series.range, domainSpanMillis, geometry.plotWidth) {
        TrendChartMath.minSpanRatio(series.range, domainSpanMillis, geometry.plotWidth)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .background(palette.nodeBackground, RoundedCornerShape(18.dp))
                .onSizeChanged { canvasSize = it }
                .semantics { contentDescription = chartDescription }
                .nestedScroll(scrollClaim)
                .trendChartPointerInput(
                    points = points,
                    range = series.range,
                    domainStart = domainStart,
                    domainEnd = domainEnd,
                    viewport = viewport,
                    densityScale = density.density,
                    axisBottomPadding = axisBottomPadding,
                    yAxis = { latestYAxis },
                    selectedPoint = { latestSelectedPoint },
                    onPointSelected = onPointSelected,
                    controller = controller,
                    gestureConfig = gestureConfig,
                    haptics = haptics,
                    showSystolic = showSystolic,
                    showDiastolic = showDiastolic,
                    pulseMode = false,
                    nodeTouchRadiusPx = nodeTouchRadiusPx,
                    minSpanRatio = minSpanRatio,
                    setClaimScroll = { claimScroll = it }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val currentGeometry = ChartGeometry.create(
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    density = density.density,
                    bottomPadding = axisBottomPadding
                )
                val scaler = ChartProjection(
                    geometry = currentGeometry,
                    domainStart = domainStart,
                    domainEnd = domainEnd,
                    startRatio = viewport.startRatio,
                    endRatio = viewport.endRatio,
                    yAxis = yAxis
                )
                val thresholdLines = buildThresholdLines(
                    yAxis = yAxis,
                    targetSystolic = targetSystolic,
                    targetDiastolic = targetDiastolic,
                    showSystolic = showSystolic,
                    showDiastolic = showDiastolic
                )

                drawYAxisGrid(currentGeometry, scaler, yAxis, textMeasurer, palette, dense = denseYAxis)

                // —— 数据层：严格裁剪在真实 Plot Area 内 ——
                // 视野外邻点保持真实时间坐标，由裁剪限制可见部分，避免改变边缘曲线。
                // 轴文字不在此范围内：Y 轴刻度画在 left 左侧、X 轴日期画在 bottom
                // 下方，它们必须保持完整可见。
                clipRect(
                    left = currentGeometry.left,
                    top = currentGeometry.top,
                    right = currentGeometry.right,
                    bottom = currentGeometry.bottom
                ) {
                    drawThresholdLines(
                        geometry = currentGeometry,
                        scaler = scaler,
                        lines = thresholdLines,
                        palette = palette
                    )

                    // 手势期间照常绘制曲线，缩放/拖动有实时反馈。
                    if (showSystolic) {
                        drawSeriesLine(renderPoints, segmentIds, scaler, palette.systolic, systolic = true, path = sysPath)
                    }
                    if (showDiastolic) {
                        drawSeriesLine(renderPoints, segmentIds, scaler, palette.diastolic, systolic = false, path = diaPath)
                    }

                    val selectedVisible = selectedPoint?.takeIf { point ->
                        point.timestamp in viewportStart..viewportEnd
                    }
                    selectedVisible?.let { point ->
                        drawCrosshair(point, scaler, palette, textMeasurer, showSystolic, showDiastolic)
                    }

                    // 节点密度自适应：数据密时默认只保留选中/吸附节点，放大后逐步显示；
                    // 判定用真实可视点距，且不删除任何数据点（折线仍由全部点构成）。
                    val nodeSpacing = currentGeometry.plotWidth /
                        viewportPoints.size.coerceAtLeast(1)
                    val showAllNodes = viewportPoints.size <= MAX_NODE_POINTS &&
                        nodeSpacing >= MIN_NODE_SPACING_PX
                    TrendChartMath.nodePoints(
                        renderPoints, selectedPoint, showAllNodes, viewportStart, viewportEnd
                    ).forEach { point ->
                        val isPointSelected = selectedPoint?.id == point.id
                        val x = scaler.xOfTime(point.timestamp)
                        if (showSystolic) {
                            drawPointNode(
                                x = x,
                                y = scaler.yOfValue(point.systolic),
                                color = valueColor(
                                    point.systolic,
                                    palette.systolic,
                                    palette.outlier,
                                    TrendSeriesCalculator.CHART_SAFE_MIN..TrendSeriesCalculator.CHART_SAFE_MAX
                                ),
                                backgroundColor = palette.nodeBackground,
                                ringColor = palette.selectionRing,
                                selected = isPointSelected
                            )
                        }
                        if (showDiastolic) {
                            drawPointNode(
                                x = x,
                                y = scaler.yOfValue(point.diastolic),
                                color = valueColor(
                                    point.diastolic,
                                    palette.diastolic,
                                    palette.outlier,
                                    20..200
                                ),
                                backgroundColor = palette.nodeBackground,
                                ringColor = palette.selectionRing,
                                selected = isPointSelected
                            )
                        }
                    }
                }

                // 时间轴文字：位于 Plot Area 下方，不能被数据层裁剪连坐。
                drawTimeAxisLabels(axisTicks, axisLabelLayouts, scaler, currentGeometry, palette)
            }
        }

        if (showHint) Text(
            text = buildHint(series),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = palette.hint
        )
    }
}

/** 独立脉搏图，共用控制器时间窗口和图表手势，不把脉搏映射到血压 Y 轴。 */
@Composable
fun SessionTimeSeriesPulseChart(
    series: TrendSeries,
    selectedPoint: TrendPoint?,
    onPointSelected: (TrendPoint?) -> Unit,
    controller: TrendChartController,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 252.dp,
    showTimeLabels: Boolean = false
) {
    val points = remember(series.points) {
        series.points.sortedWith(compareBy<TrendPoint> { it.timestamp }.thenBy { it.id })
    }
    if (points.none { it.pulse != null }) {
        TrendEmptyState(title = "这段时间暂无脉搏记录", modifier = modifier)
        return
    }
    val density = LocalDensity.current
    val palette = trendChartPalette()
    val zoneId = remember { ZoneId.systemDefault() }
    val textMeasurer = rememberTextMeasurer()
    val viewport = controller.viewportFor(series.range)
    LaunchedEffect(series) { controller.updateSeries(series) }
    val gestureConfig = rememberChartGestureConfig()
    val haptics = LocalHapticFeedback.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var claimScroll by remember { mutableStateOf(false) }
    val scrollClaim = rememberChartScrollClaim { claimScroll }
    val pulsePath = remember { Path() }
    val viewportStart = viewport.startMillis()
    val viewportEnd = viewport.endMillis()
    val visiblePoints = remember(points, viewportStart, viewportEnd) {
        TrendChartMath.visiblePoints(points, viewportStart, viewportEnd, alreadySorted = true)
    }
    val viewportPoints = remember(visiblePoints, viewportStart, viewportEnd) {
        visiblePoints.filter { it.timestamp in viewportStart..viewportEnd }
    }
    var yAxis by remember(series.range) { mutableStateOf<TrendYAxis?>(null) }
    LaunchedEffect(viewportPoints) {
        yAxis = TrendChartMath.stablePulseYAxis(yAxis, viewportPoints)
    }
    val activeYAxis = yAxis ?: TrendChartMath.pulseYAxis(
        points.firstNotNullOf { it.pulse }, points.firstNotNullOf { it.pulse }
    )
    val latestYAxis by rememberUpdatedState(activeYAxis)
    val latestSelectedPoint by rememberUpdatedState(selectedPoint)
    val maxDrawPoints = remember(canvasSize.width) {
        (canvasSize.width / 2).coerceIn(MIN_DRAW_POINTS, MAX_DRAW_POINTS)
    }
    val renderPoints = remember(visiblePoints, maxDrawPoints, selectedPoint?.id) {
        TrendChartMath.samplePulse(visiblePoints, maxDrawPoints, selectedPoint?.id)
    }
    val segmentIds = remember(visiblePoints) {
        TrendChartMath.pulseSegmentIds(visiblePoints, GAP_MILLIS)
    }
    val maxTicks = remember(canvasSize.width, density) {
        val plotWidthPx = if (canvasSize.width > 0) {
            (canvasSize.width - 52f * density.density).coerceAtLeast(40f)
        } else 0f
        TrendChartMath.maxTickCount((plotWidthPx / density.density).roundToInt())
    }
    val axisTicks = remember(viewportStart, viewportEnd, maxTicks, zoneId, showTimeLabels) {
        TrendChartMath.timeTicks(viewportStart, viewportEnd, zoneId, maxTicks, showTimeLabels)
    }
    val axisLabelStyle = TextStyle(color = palette.axis, fontSize = 12.sp, lineHeight = 16.sp)
    val axisLabelLayouts = remember(axisTicks, axisLabelStyle) {
        axisTicks.map { tick ->
            textMeasurer.measure(
                text = listOfNotNull(tick.primary, tick.secondary).joinToString("\n"),
                style = axisLabelStyle,
                softWrap = false
            )
        }
    }
    val axisBottomPadding = remember(axisLabelStyle, density) {
        with(density) {
            textMeasurer.measure("00:00\n00-00", axisLabelStyle, softWrap = false).size.height +
                16.dp.toPx()
        }
    }
    val geometry = remember(canvasSize, density, axisBottomPadding) {
        ChartGeometry.create(canvasSize, density.density, axisBottomPadding)
    }
    val minSpanRatio = remember(series.range, series.rangeStart, series.rangeEnd, geometry.plotWidth) {
        TrendChartMath.minSpanRatio(
            series.range,
            (series.rangeEnd - series.rangeStart).coerceAtLeast(1L),
            geometry.plotWidth
        )
    }
    val nodeTouchRadiusPx = remember(density) {
        with(density) { NODE_TOUCH_RADIUS.dp.toPx() }
    }
    val description = remember(series) {
        "脉搏趋势图，${series.range.title}，${series.points.count { it.pulse != null }} 个脉搏数据点，双指缩放，放大后横向拖动，长按查看数据，双击恢复"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .background(palette.nodeBackground, RoundedCornerShape(18.dp))
            .onSizeChanged { canvasSize = it }
            .semantics { contentDescription = description }
            .nestedScroll(scrollClaim)
            .trendChartPointerInput(
                points = points,
                range = series.range,
                domainStart = series.rangeStart,
                domainEnd = series.rangeEnd,
                viewport = viewport,
                densityScale = density.density,
                axisBottomPadding = axisBottomPadding,
                yAxis = { latestYAxis },
                selectedPoint = { latestSelectedPoint },
                onPointSelected = onPointSelected,
                controller = controller,
                gestureConfig = gestureConfig,
                haptics = haptics,
                showSystolic = false,
                showDiastolic = false,
                pulseMode = true,
                nodeTouchRadiusPx = nodeTouchRadiusPx,
                minSpanRatio = minSpanRatio,
                setClaimScroll = { claimScroll = it }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val currentGeometry = ChartGeometry.create(
                IntSize(size.width.roundToInt(), size.height.roundToInt()),
                density.density,
                axisBottomPadding
            )
            val scaler = ChartProjection(
                geometry = currentGeometry,
                domainStart = series.rangeStart,
                domainEnd = series.rangeEnd,
                startRatio = viewport.startRatio,
                endRatio = viewport.endRatio,
                yAxis = activeYAxis
            )
            drawYAxisGrid(currentGeometry, scaler, activeYAxis, textMeasurer, palette)
            clipRect(currentGeometry.left, currentGeometry.top, currentGeometry.right, currentGeometry.bottom) {
                drawPulseSeriesLine(renderPoints, segmentIds, scaler, palette.systolic, pulsePath)
                selectedPoint?.takeIf { it.timestamp in viewportStart..viewportEnd }?.let { point ->
                    drawCrosshair(point, scaler, palette, textMeasurer, false, false, pulseMode = true)
                }

                val showAllNodes = viewportPoints.size <= MAX_NODE_POINTS &&
                    currentGeometry.plotWidth / viewportPoints.size.coerceAtLeast(1) >= MIN_NODE_SPACING_PX
                TrendChartMath.nodePoints(
                    renderPoints, selectedPoint, showAllNodes, viewportStart, viewportEnd
                ).filter { it.pulse != null }.forEach { point ->
                    drawPointNode(
                        x = scaler.xOfTime(point.timestamp),
                        y = scaler.yOfValue(point.pulse!!),
                        color = palette.systolic,
                        backgroundColor = palette.nodeBackground,
                        ringColor = palette.selectionRing,
                        selected = point.id == selectedPoint?.id
                    )
                }
            }
            drawTimeAxisLabels(axisTicks, axisLabelLayouts, scaler, currentGeometry, palette)
        }
        Text(
            text = "次/分",
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = palette.axis
        )
    }
}

private fun buildHint(series: TrendSeries): String {
    val granularity = series.aggregation.displayLabel()
    return "双指缩放，放大后单指拖动，双击复位；长按滑动逐点查看 · $granularity"
}

private fun buildChartDescription(
    series: TrendSeries,
    showSystolic: Boolean,
    showDiastolic: Boolean
): String {
    val metric = when {
        showSystolic && showDiastolic -> "收缩压与舒张压"
        showSystolic -> "收缩压"
        else -> "舒张压"
    }
    val granularity = series.aggregation.displayLabel()
    val first = series.firstMeasuredAt?.let(::formatReadoutDateTime) ?: "--"
    val last = series.lastMeasuredAt?.let(::formatReadoutDateTime) ?: "--"
    return "血压趋势折线图，$metric，$granularity，共 ${series.points.size} 个数据点，" +
        "样本区间 $first 至 $last。可双指缩放、放大后单指拖动、双击恢复默认视野，长按滑动逐点读数。"
}

private fun formatReadoutDateTime(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

@Composable
private fun TrendEmptyState(title: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "保持每天固定时间记录，积累记录后即可查看趋势。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 触点命中节点的矩形热区半径：不要求精确点中视觉圆点。 */
private const val NODE_TOUCH_RADIUS = 22

/** 单击判定允许的最大移动（点按抖动容忍）。 */

/** 收缩压/舒张压折线宽度：2dp 兼顾精细度与高 DPI 屏的可读性。 */

private const val MIN_DRAW_POINTS = 60
private const val MAX_DRAW_POINTS = 900
private const val MIN_NODE_SPACING_PX = 14f
private const val MAX_NODE_POINTS = 80
private const val GAP_MILLIS = 2L * 24L * 60L * 60L * 1000L
