package com.example.bloodpressurerecord.ui.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.model.DayNightAverage
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.displayLabel
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppCompactActionPill
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.AppTopBarActionButton
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.common.ExpandableSection
import com.example.bloodpressurerecord.ui.common.SegmentedPillGroup
import com.example.bloodpressurerecord.ui.common.SegmentedPillSize
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.common.appMotionColor
import com.example.bloodpressurerecord.ui.common.appMotionFloat
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
import com.example.bloodpressurerecord.ui.common.focusRing
import com.example.bloodpressurerecord.ui.common.pressableScale
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.LocalBpGradePalette
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.TabularFeatureSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendScreen(
    viewModel: TrendViewModel,
    onBack: (() -> Unit)? = null,
    onAddMeasurement: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit = {},
    onOpenRecord: (String) -> Unit = {},
    onScrollBackwardChanged: (Boolean?) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chartController = viewModel.chartController
    BindTrendChartController(chartController)
    // 上一条 / 下一条在当前图表的 Session 代表值节点之间移动。
    // 不能按日期 ±1 天推算——有些日期根本没有记录。
    val displayPoints = uiState.series.points
    val selectedPoint = displayPoints.firstOrNull { it.id == uiState.selectedPointId }

    LaunchedEffect(uiState.fullscreen) { onFullscreenChanged(uiState.fullscreen) }
    DisposableEffect(Unit) { onDispose { onFullscreenChanged(false) } }
    TrendFullscreenOrientation(uiState.fullscreen)

    if (uiState.fullscreen) {
        TrendFullscreenScreen(uiState, viewModel, onOpenRecord)
        return
    }

    uiState.dayDetails?.let { details ->
        TrendDayDetailsSheet(
            details = details,
            onDismiss = viewModel::dismissDayDetails,
            onOpenRecord = onOpenRecord
        )
    }

    val series = uiState.series
    val hasData = series.points.isNotEmpty()
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.canScrollBackward, hasData, onBack) {
        if (onBack == null) {
            onScrollBackwardChanged(scrollState.canScrollBackward.takeIf { hasData })
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(
                top = statusBarTopPadding(),
                bottom = maxOf(dockContentBottomPadding(), AppDimensions.dockMinContentClearance)
            )
    ) {
        AppTopBar(
            title = "血压趋势",
            onBack = onBack,
            actions = {
                if (hasData) {
                    AppTopBarActionButton(
                        text = "全屏",
                        onClick = { viewModel.setFullscreen(true) }
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimensions.pageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrendRangeControl(
                selected = uiState.range,
                onSelected = viewModel::setRange
            )

            // UX-16 & 模块四：无数据时仅保留范围选择与单张引导卡，隐藏无效工具栏与空壳折叠统计
            if (!hasData) {
                TrendEmptySection(range = uiState.range, onAddMeasurement = onAddMeasurement)
            } else {
                TrendAggregationControl(
                    selected = uiState.aggregation,
                    onSelected = viewModel::setAggregation
                )

                TrendCompactSummary(
                    series = series,
                    insights = uiState.insights
                )

                // AUD-22 / D5-4：取消首张血压主图多余折叠外壳直接常驻，消除上下标题重复；下方次级区块保留折叠
                TrendCard(
                    series = series,
                    metric = uiState.metric,
                    selectedPoint = selectedPoint,
                    displayPoints = displayPoints,
                    targetSystolic = uiState.targetSystolic,
                    targetDiastolic = uiState.targetDiastolic,
                    chartController = chartController,
                    onMetricChange = viewModel::setMetric,
                    onPointSelected = viewModel::selectPoint,
                    onViewDayRecords = viewModel::openPointDetails,
                    onPrevious = viewModel::selectPrevious,
                    onNext = viewModel::selectNext,
                    onReset = viewModel::resetChart,
                    onLatest = viewModel::moveToLatest
                )

                if (uiState.showPulse) ExpandableSection(
                    title = "脉搏趋势",
                    summary = series.averagePulse?.let { "期间平均 $it 次/分" } ?: "暂无数据"
                ) {
                    PulseTrendCard(
                        series = series,
                        selectedPoint = selectedPoint,
                        chartController = chartController,
                        onPointSelected = viewModel::selectPoint
                    )
                }

                TrendPeriodOverview(
                    insights = uiState.insights,
                    targetSystolic = uiState.targetSystolic,
                    targetDiastolic = uiState.targetDiastolic
                )
            }
        }
    }
}

/** 紧凑摘要：不再用大卡片占掉图表上方的空间。 */
@Composable
private fun TrendCompactSummary(
    series: TrendSeries,
    insights: TrendInsights
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${series.range.title} · ${series.aggregation.displayLabel()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${insights.recordCount} 次测量 · ${insights.recordDays} 天记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                buildSampleRangeText(series),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 样本区间：明确写出实际有数据的日期，
 * 避免用户把「某天没有记录」误读成应用忽略了数据。
 */
private fun buildSampleRangeText(series: TrendSeries): String {
    val first = series.firstMeasuredAt ?: return "本周期还没有记录"
    val last = series.lastMeasuredAt ?: return "本周期还没有记录"
    val firstDate = formatSampleDate(first)
    val lastDate = formatSampleDate(last)
    val range = if (firstDate == lastDate) firstDate else "$firstDate – $lastDate"
    return "记录日期 $range"
}

private fun formatSampleDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

@Composable
private fun TrendEmptySection(range: TrendRange, onAddMeasurement: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("${range.title}暂无记录", style = MaterialTheme.typography.titleLarge)
            Text(
                "记录后会在这里显示变化曲线和范围统计。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppPrimaryButton(
                text = "记一次血压",
                onClick = onAddMeasurement,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendPeriodOverview(
    insights: TrendInsights,
    targetSystolic: Int?,
    targetDiastolic: Int?
) {
    ExpandableSection(
        title = "平均血压",
        summary = if (insights.periodAverage == null) "暂无数据" else
            "期间 ${insights.periodAverage.pressureText()} · 早间 ${insights.morningAverage.pressureText()} · 晚间 ${insights.eveningAverage.pressureText()}",
        initiallyExpanded = true
    ) {
        TrendStatCell(
            "期间平均",
            insights.periodAverage?.let { "${it.first}/${it.second}" } ?: "—",
            "mmHg · ${insights.recordCount}次/${insights.recordDays}天",
            Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrendStatCell(
                "早间平均",
                insights.morningAverage?.let { "${it.first}/${it.second}" } ?: "—",
                "${insights.morningCount}次/${insights.morningDays}天 · 05:00–11:59",
                Modifier.weight(1f)
            )
            TrendStatCell(
                "晚间平均",
                insights.eveningAverage?.let { "${it.first}/${it.second}" } ?: "—",
                "${insights.eveningCount}次/${insights.eveningDays}天 · 18:00–23:59",
                Modifier.weight(1f)
            )
        }
    }
    ExpandableSection(
        title = "昼夜统计",
        summary = if (insights.daytimeAverage.sessionCount + insights.nighttimeAverage.sessionCount == 0)
            "暂无数据" else "日间 ${insights.daytimeAverage.pressureText()} · 夜间 ${insights.nighttimeAverage.pressureText()}"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DayNightStatCell("日间平均", insights.daytimeAverage, "06:00–21:59", Modifier.weight(1f))
            DayNightStatCell("夜间平均", insights.nighttimeAverage, "22:00–05:59", Modifier.weight(1f))
        }
        Text(
            "夜间平均仅描述固定夜间时段内主动记录的家庭血压。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    // 高风险信息保持直接可见，避免折叠次级统计后遗漏。
    if (insights.highRiskCount > 0) {
        TrendStatCell(
            "高风险记录", "${insights.highRiskCount} 次",
            "按本次记录是否含高风险读数统计", Modifier.fillMaxWidth()
        )
    }
    ExpandableSection(
        title = "更多统计",
        summary = if (insights.recordCount == 0) "暂无数据" else
            "达标率 ${insights.targetRate?.let { "$it%" } ?: "—"} · 最高 ${insights.highestReading?.let { "${it.systolic}/${it.diastolic}" } ?: "—"}"
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val reducedMotion = LocalReducedMotion.current
                val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                val progressColor = MaterialTheme.colorScheme.secondary
                val animatedSweep = appMotionFloat(
                    targetValue = (insights.targetRate ?: 0) * 3.6f,
                    reducedMotion = reducedMotion,
                    spec = AppMotion.springGentle(reducedMotion = false),
                    label = "target_rate_sweep"
                )
                val targetText = if (targetSystolic != null && targetDiastolic != null) {
                    "按单次记录计算：低于个人目标 $targetSystolic/$targetDiastolic 且不偏低的占比。"
                } else "在「我的」设置个人达标值后可查看达标率。"
                val previous = insights.previousTargetRate
                val current = insights.targetRate
                val comparison = if (previous != null && current != null) {
                    when {
                        current - previous >= 5 -> "较上个周期（$previous%）提升 ${current - previous} 个百分点。"
                        previous - current >= 5 -> "较上个周期（$previous%）下降 ${previous - current} 个百分点。"
                        else -> "与上个周期（$previous%）基本持平。"
                    }
                } else ""
                val rateDisplay = insights.targetRate?.let { "$it%" } ?: "—"
                val targetRateSemanticsText = buildTargetRateSemanticsDescription(
                    targetRate = insights.targetRate,
                    targetText = targetText,
                    comparison = comparison
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trendTargetRateHeader")
                        .clearAndSetSemantics {
                            contentDescription = targetRateSemanticsText
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 11.dp.toPx())
                            )
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = animatedSweep,
                                useCenter = false,
                                style = Stroke(width = 11.dp.toPx())
                            )
                        }
                        Text(
                            rateDisplay,
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                            fontFamily = NumberFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("达标率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            targetText + comparison,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val order = listOf(
                    BloodPressureCategory.NORMAL,
                    BloodPressureCategory.HIGH_NORMAL,
                    BloodPressureCategory.STAGE1,
                    BloodPressureCategory.STAGE2,
                    BloodPressureCategory.STAGE3,
                    BloodPressureCategory.LOW
                )
                val categories = order.mapNotNull { category ->
                    insights.categoryCounts[category]?.takeIf { it > 0 }?.let { category to it }
                }
                if (categories.isNotEmpty()) {
                    val totalCategoryCount = categories.sumOf { it.second }
                    val segmentColors = categories.map { (category, _) -> trendGradeColor(category) }
                    val dividerColor = MaterialTheme.colorScheme.surface
                    // AUD-27 / S11：外层整条统一圆角裁剪，内部色块严格按 count/total 真实比例绘制，
                    // 严禁设置任何人为最小段宽；细分隔线仅在两侧色块均足够宽时绘制，绝不吞噬 1/184 等极小占比段。
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                    ) {
                        val segments = computeProportionalSegments(
                            counts = categories.map { it.second },
                            totalWidthPx = size.width
                        )
                        segments.forEachIndexed { idx, (startX, endX) ->
                            val segWidth = (endX - startX).coerceAtLeast(0f)
                            if (segWidth > 0f) {
                                drawRect(
                                    color = segmentColors[idx],
                                    topLeft = Offset(startX, 0f),
                                    size = Size(segWidth, size.height)
                                )
                            }
                        }
                        val minWidthForDivider = 3.dp.toPx()
                        val dividerStroke = 1.dp.toPx()
                        for (idx in 0 until segments.lastIndex) {
                            val leftWidth = segments[idx].second - segments[idx].first
                            val rightWidth = segments[idx + 1].second - segments[idx + 1].first
                            if (leftWidth >= minWidthForDivider && rightWidth >= minWidthForDivider) {
                                val boundaryX = segments[idx].second
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(boundaryX, 0f),
                                    end = Offset(boundaryX, size.height),
                                    strokeWidth = dividerStroke
                                )
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { (category, count) ->
                            val pct = count * 100f / totalCategoryCount.coerceAtLeast(1)
                            val pctLabel = if (pct > 0f && pct < 1f) "<1%" else "${pct.roundToInt()}%"
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(9.dp).background(trendGradeColor(category), CircleShape))
                                Text(
                                    "${trendCategoryLabel(category)} ${count}次（$pctLabel）",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularFeatureSettings),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val highest = insights.highestReading
                TrendStatCell(
                    "最高一次",
                    highest?.let { "${it.systolic}/${it.diastolic}" } ?: "—",
                    highest?.let {
                        Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                    } ?: "",
                    Modifier.weight(1f)
                )
                val lowest = insights.lowestReading
                TrendStatCell(
                    "最低一次",
                    lowest?.let { "${it.systolic}/${it.diastolic}" } ?: "—",
                    lowest?.let {
                        Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                    } ?: "",
                    Modifier.weight(1f)
                )
            }
            if (insights.highRiskCount == 0) TrendStatCell(
                "高风险记录",
                "${insights.highRiskCount} 次",
                "按本次记录是否含高风险读数统计",
                Modifier.fillMaxWidth()
            )
        }
    }
}

private fun Pair<Int, Int>?.pressureText(): String = this?.let { "${it.first}/${it.second}" } ?: "—"

private fun DayNightAverage.pressureText(): String =
    if (systolic != null && diastolic != null) "$systolic/$diastolic" else "—"

@Composable
private fun DayNightStatCell(title: String, average: DayNightAverage, timeRange: String, modifier: Modifier) {
    TrendStatCell(
        title, average.pressureText(),
        "${average.sessionCount}次 · ${average.recordDays}天\n$timeRange", modifier
    )
}

@Composable
private fun TrendStatCell(title: String, value: String, supporting: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TabularFeatureSettings),
                fontFamily = NumberFontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun trendGradeColor(category: BloodPressureCategory): Color =
    LocalBpGradePalette.current.forCategory(category.name).mid

private fun trendCategoryLabel(category: BloodPressureCategory): String = when (category) {
    BloodPressureCategory.NORMAL -> "正常"
    BloodPressureCategory.HIGH_NORMAL -> "正常高值"
    BloodPressureCategory.STAGE1 -> "1级"
    BloodPressureCategory.STAGE2 -> "2级"
    BloodPressureCategory.STAGE3 -> "3级"
    BloodPressureCategory.LOW -> "偏低"
}

@Composable
private fun TrendCard(
    series: TrendSeries,
    metric: TrendMetricType,
    selectedPoint: TrendPoint?,
    displayPoints: List<TrendPoint>,
    targetSystolic: Int?,
    targetDiastolic: Int?,
    chartController: TrendChartController,
    onMetricChange: (TrendMetricType) -> Unit,
    onPointSelected: (TrendPoint?) -> Unit,
    onViewDayRecords: (TrendPoint) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
    onLatest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 周期与粒度已由顶部摘要显示，图表直接从当前读数开始。
            // 顶部数据栏：长按数据检查 / 点击节点时显示当前选中记录，
            // 并承载「上一条 / 下一条 / 明细 / 恢复」四个操作。
            TrendSelectionReadout(
                points = displayPoints,
                selectedPoint = selectedPoint,
                onPointSelected = onPointSelected,
                onViewDayRecords = onViewDayRecords,
                onResetChart = onReset,
                onPrevious = onPrevious,
                onNext = onNext,
                onLatest = onLatest
            )
            // 指标切换置于图表正上方，复用公共 Compact 白滑块分段器与上方主分段拉开层次。
            val metricOptions = remember {
                listOf(TrendMetricType.BOTH, TrendMetricType.SYSTOLIC, TrendMetricType.DIASTOLIC)
            }
            val selectedMetricIndex = metricOptions.indexOf(metric).coerceAtLeast(0)
            SegmentedPillGroup(
                options = listOf("双指标", "收缩压", "舒张压"),
                selectedIndex = selectedMetricIndex,
                onSelect = { idx -> metricOptions.getOrNull(idx)?.let(onMetricChange) },
                size = SegmentedPillSize.Compact
            )
            // UX-15 & AUD-11：显式双指标颜色图例 + 诊室参考线与个人达标目标动态数值外置
            TrendChartLegendRow(
                showSystolic = metric != TrendMetricType.DIASTOLIC,
                showDiastolic = metric != TrendMetricType.SYSTOLIC,
                targetSystolic = targetSystolic,
                targetDiastolic = targetDiastolic
            )
            SessionTimeSeriesDualLineChart(
                series = series,
                selectedPoint = selectedPoint,
                onPointSelected = onPointSelected,
                controller = chartController,
                targetSystolic = targetSystolic,
                targetDiastolic = targetDiastolic,
                showSystolic = metric != TrendMetricType.DIASTOLIC,
                showDiastolic = metric != TrendMetricType.SYSTOLIC,
                emptyTitle = "${series.range.title} 暂无数据"
            )
        }
    }
}

internal fun buildTargetRateSemanticsDescription(
    targetRate: Int?,
    targetText: String,
    comparison: String
): String {
    val rateSpeech = targetRate?.let { "$it%" } ?: "暂无"
    return "达标率，$rateSpeech。$targetText$comparison"
}

internal fun computeProportionalSegments(
    counts: List<Int>,
    totalWidthPx: Float
): List<Pair<Float, Float>> {
    if (counts.isEmpty() || totalWidthPx <= 0f) return emptyList()
    val validCounts = counts.filter { it > 0 }
    if (validCounts.isEmpty()) return emptyList()
    val total = validCounts.sum().toFloat()
    var cumulative = 0
    return validCounts.mapIndexed { index, count ->
        val startX = totalWidthPx * (cumulative.toFloat() / total)
        cumulative += count
        val endX = if (index == validCounts.lastIndex) {
            totalWidthPx
        } else {
            totalWidthPx * (cumulative.toFloat() / total)
        }
        startX to endX
    }
}

internal fun buildTrendTargetSummary(
    showSystolic: Boolean,
    showDiastolic: Boolean,
    targetSystolic: Int?,
    targetDiastolic: Int?
): String? {
    val sys = targetSystolic.takeIf { showSystolic }
    val dia = targetDiastolic.takeIf { showDiastolic }
    return when {
        sys != null && dia != null -> "个人目标 $sys/$dia"
        sys != null -> "高压目标 $sys"
        dia != null -> "低压目标 $dia"
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendChartLegendRow(
    showSystolic: Boolean,
    showDiastolic: Boolean,
    targetSystolic: Int?,
    targetDiastolic: Int?
) {
    val palette = trendChartPalette()
    val referenceLabel = when {
        showSystolic && showDiastolic -> "诊室参考线 140/90"
        showSystolic -> "诊室参考线 140"
        else -> "诊室参考线 90"
    }
    val targetSummary = buildTrendTargetSummary(
        showSystolic = showSystolic,
        showDiastolic = showDiastolic,
        targetSystolic = targetSystolic,
        targetDiastolic = targetDiastolic
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showSystolic) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(9.dp).background(palette.systolic, CircleShape))
                Text("高压（收缩压）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showDiastolic) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(9.dp).background(palette.diastolic, CircleShape))
                Text("低压（舒张压）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Canvas(Modifier.width(14.dp).height(8.dp)) {
                val y = size.height / 2f
                drawLine(
                    color = palette.axis,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                )
            }
            Text(
                referenceLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularFeatureSettings),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (targetSummary != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.width(14.dp).height(8.dp)) {
                    val y = size.height / 2f
                    drawLine(
                        color = if (showSystolic) palette.systolic else palette.diastolic,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.5.dp.toPx()))
                    )
                }
                Text(
                    targetSummary,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularFeatureSettings),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PulseTrendCard(
    series: TrendSeries,
    selectedPoint: TrendPoint?,
    chartController: TrendChartController,
    onPointSelected: (TrendPoint?) -> Unit
) {
    val palette = trendChartPalette()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (series.averagePulse == null) {
                Text(
                    "这段时间暂无脉搏记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(9.dp).background(palette.pulse, CircleShape))
                    Text(
                        "脉搏（次/分） · 平均 ${series.averagePulse} 次/分",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SessionTimeSeriesPulseChart(
                    series = series,
                    selectedPoint = selectedPoint,
                    onPointSelected = onPointSelected,
                    controller = chartController
                )
            }
        }
    }
}

/**
 * 顶部数据栏：显示当前选中的记录（长按数据检查 / 点击节点 / 上一条 / 下一条）。
 *
 * 未选中时退回当前范围最近一次 Session（[points] 的最后一个点）。
 * 操作区提供节点移动、明细、恢复与回到最新：
 * - 上一条 / 下一条：在 [points] 上移动，不按日期 ±1 天推算；
 * - 明细：打开**当前显示点**所属日期的原始测量明细（真实数据库记录）；
 * - 恢复：图表回到该范围首次打开时的默认状态（与双击复位同一套重置逻辑）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrendSelectionReadout(
    points: List<TrendPoint>,
    selectedPoint: TrendPoint?,
    onPointSelected: (TrendPoint?) -> Unit,
    onViewDayRecords: (TrendPoint) -> Unit,
    onResetChart: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onLatest: (() -> Unit)? = null
) {
    if (points.isEmpty()) return
    val currentSelection = points.firstOrNull { it.id == selectedPoint?.id }
    val displayed = currentSelection ?: points.last()
    val index = points.indexOfFirst { it.id == displayed.id }.takeIf { it >= 0 } ?: points.lastIndex
    val readoutDescription = remember(displayed, currentSelection, index, points.size) {
        buildReadoutDescription(displayed, currentSelection != null, index, points.size) +
            if (displayed.containsHighRiskReading) "含高风险读数。" else ""
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 每日平均属于自然日统计，不把内部绘图时间显示成真实测量时分。
        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = readoutDescription
            },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = formatTrendPointDate(displayed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${if (displayed.aggregation == TrendAggregation.RAW) "" else "平均 "}${displayed.systolic}/${displayed.diastolic} mmHg",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                    fontFamily = NumberFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                StatusChip(
                    text = CategoryPresentation.label(displayed.category),
                    isAbnormal = !displayed.category.equals("NORMAL", ignoreCase = true),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    trendPointPulseText(displayed),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                if (displayed.aggregation != TrendAggregation.RAW) Text(
                    "${displayed.recordCount} 次测量",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                if (displayed.containsHighRiskReading) Text(
                    if (displayed.aggregation == TrendAggregation.RAW) "含高风险读数" else "当天含高风险记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        // 紧凑胶囊自适应布局：常规字号单行完整展示「复位视野」，大字号或极窄屏自然换行且热区 >= 48dp。
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("trendReadoutActions"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AppCompactActionPill(
                text = "上一条",
                onClick = { onPrevious?.invoke() ?: onPointSelected(points[index - 1]) },
                enabled = index > 0
            )
            AppCompactActionPill(
                text = "下一条",
                onClick = { onNext?.invoke() ?: onPointSelected(points[index + 1]) },
                enabled = index in 0 until points.lastIndex
            )
            AppCompactActionPill(
                text = "明细",
                onClick = { onViewDayRecords(displayed) }
            )
            if (onLatest != null) {
                AppCompactActionPill(
                    text = "最新",
                    onClick = onLatest
                )
            }
            AppCompactActionPill(
                text = "复位视野",
                onClick = onResetChart
            )
        }
    }
}
private fun buildReadoutDescription(point: TrendPoint, isSelected: Boolean, index: Int, total: Int): String {
    val daily = point.aggregation != TrendAggregation.RAW
    val context = if (isSelected) "已选中的数据点" else if (daily) "最近记录日" else "最近一次测量"
    val categoryLabel = CategoryPresentation.label(point.category)
    return "$context，${formatTrendPointDate(point)}，${if (daily) "当日平均" else ""}" +
        "高压 ${point.systolic}，低压 ${point.diastolic}，$categoryLabel，${trendPointPulseText(point)}，" +
        (if (daily) "${point.recordCount} 次测量，" else "") +
        "第 ${index + 1} 个，共 $total 个数据点。"
}

internal fun formatTrendPointDate(point: TrendPoint): String =
    Instant.ofEpochMilli(point.timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(
            if (point.aggregation == TrendAggregation.RAW) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd"
        ))

internal fun trendPointPulseText(point: TrendPoint): String {
    val label = if (point.aggregation == TrendAggregation.RAW) "脉搏" else "平均脉搏"
    return point.pulse?.let { "$label $it 次/分" } ?: "$label —"
}

@Composable
internal fun TrendRangeControl(
    selected: TrendRange,
    onSelected: (TrendRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = TrendRange.entries
    val selectedIndex = entries.indexOf(selected).coerceAtLeast(0)
    SegmentedPillGroup(
        options = entries.map { it.label },
        selectedIndex = selectedIndex,
        onSelect = { idx -> entries.getOrNull(idx)?.let(onSelected) },
        size = SegmentedPillSize.Compact,
        modifier = modifier
    )
}

@Composable
internal fun TrendAggregationControl(
    selected: TrendAggregation,
    onSelected: (TrendAggregation) -> Unit,
    modifier: Modifier = Modifier,
    size: SegmentedPillSize = SegmentedPillSize.Primary
) {
    val options = remember { listOf(TrendAggregation.RAW, TrendAggregation.DAILY) }
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    SegmentedPillGroup(
        options = options.map { it.displayLabel() },
        selectedIndex = selectedIndex,
        onSelect = { idx -> options.getOrNull(idx)?.let(onSelected) },
        size = size,
        modifier = modifier
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrendDayDetailsSheet(
    details: TrendDayDetails,
    onDismiss: () -> Unit,
    onOpenRecord: (String) -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(details.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TrendDayDetailsContent(details, onOpenRecord = { id -> onDismiss(); onOpenRecord(id) })
        }
    }
}

@Composable
internal fun TrendDayDetailsContent(details: TrendDayDetails, onOpenRecord: (String) -> Unit) {
    Text(
        buildDayDetailsSubtitle(details), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    when {
        details.loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        details.error != null -> Text(details.error, color = MaterialTheme.colorScheme.error)
        else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(details.records, key = TrendRecord::id) { record ->
                TrendDayRecordRow(
                    record, selected = record.id == details.selectedRecordId,
                    onClick = { onOpenRecord(record.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
/**
 * 当日明细的副标题：加载完成后用真实记录数与该日平均，
 * 避免单次测量节点显示成「当日 1 次」而掩盖同一天的其他记录。
 */
private fun buildDayDetailsSubtitle(details: TrendDayDetails): String {
    if (details.loading) return "正在读取当天记录"
    if (details.error != null) return "当天记录读取失败"
    val records = details.records
    if (records.isEmpty()) return "当天没有原始记录"
    val systolic = records.map { it.systolic }.average().roundToInt()
    val diastolic = records.map { it.diastolic }.average().roundToInt()
    val prefix = if (details.point.aggregation != TrendAggregation.RAW) "参与统计" else "当日"
    return "$prefix ${records.size} 次，平均 $systolic/$diastolic mmHg · 点击查看原记录"
}

@Composable
private fun TrendDayRecordRow(record: TrendRecord, selected: Boolean, onClick: () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme
    val background = appMotionColor(
        targetValue = when {
            selected -> colors.primaryContainer
            pressed -> colors.surfaceContainerHigh
            else -> Color.Transparent
        },
        reducedMotion = reducedMotion,
        label = "trendDayRecordBackground",
        duration = AppMotion.Micro
    )
    val rowShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .pressableScale(interactionSource, pressedScale = 0.985f)
            .clip(rowShape)
            .background(background)
            .focusRing(interactionSource, rowShape)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                (if (selected) "已选中 · " else "") + formatSessionTime(record.measuredAt),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (record.containsHighRiskReading) {
                    "${record.category.toChineseCategory()} · 含高风险读数"
                } else {
                    record.category.toChineseCategory()
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (record.containsHighRiskReading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${record.systolic} / ${record.diastolic} mmHg",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "脉搏 ${record.pulse?.let { "$it 次/分" } ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatFullDate(measuredAt: Long): String =
    Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

private fun formatSessionTime(measuredAt: Long): String {
    return Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun String.toChineseCategory(): String =
    com.example.bloodpressurerecord.ui.common.CategoryPresentation.label(this)
