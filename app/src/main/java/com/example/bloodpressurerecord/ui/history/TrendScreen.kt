package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.bloodpressurerecord.ui.common.AppBackButton
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.ExpandableSection
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
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
    onOpenRecord: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chartController = viewModel.chartController
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.pageHorizontalPadding)
            .padding(
                // 标题必须落在状态栏下方：顶部安全区取自 WindowInsets（刘海/挖孔屏
                // 已包含在 statusBars 内），不写任何机型专用的固定高度。
                top = statusBarTopPadding(extra = 12.dp),
                // 底部一次给足：导航栏安全区 + 悬浮 Dock 胶囊 + 胶囊下边距 + 收尾间距。
                // 页面内容因此总能完整滚到 Dock 上方，且不会多出一大块空白。
                bottom = maxOf(dockContentBottomPadding(), AppDimensions.dockMinContentClearance)
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                AppBackButton(onClick = onBack)
            }
            Text(
                text = "血压趋势",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp)
            )
            TextButton(onClick = { viewModel.setFullscreen(true) }) { Text("全屏") }
        }

        SegmentedControl(
            items = TrendRange.entries,
            selected = uiState.range,
            label = { it.label },
            onSelected = viewModel::setRange
        )

        if (series.points.isEmpty()) {
            TrendEmptySection(range = uiState.range, onAddMeasurement = onAddMeasurement)
        }

        // 顶部只展示周期与样本摘要，平均值集中在下方统计区。
        if (series.points.isNotEmpty()) {
            TrendCompactSummary(
                series = series,
                insights = uiState.insights
            )
        }

        ExpandableSection(
            title = "血压趋势",
            summary = uiState.insights.periodAverage.pressureText().let {
                if (it == "—") "暂无数据" else "期间平均 $it mmHg"
            },
            initiallyExpanded = true
        ) {
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
        }

        if (uiState.showPulse) ExpandableSection(
            title = "脉搏趋势",
            summary = series.averagePulse?.let { "最近平均 $it 次/分" } ?: "暂无数据"
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
                    series.range.title,
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
        shape = MaterialTheme.shapes.medium,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sweep = (insights.targetRate ?: 0) * 3.6f
                            drawArc(
                                color = Color(0xFFDDE8DF),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 11.dp.toPx())
                            )
                            drawArc(
                                color = Color(0xFF70B78D),
                                startAngle = -90f,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = 11.dp.toPx())
                            )
                        }
                        Text(
                            insights.targetRate?.let { "$it%" } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = NumberFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("达标率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val targetText = if (targetSystolic != null && targetDiastolic != null) {
                            "低于目标 $targetSystolic/$targetDiastolic 且不偏低的占比。"
                        } else "在“我的”设置目标后可查看达标率。"
                        val previous = insights.previousTargetRate
                        val current = insights.targetRate
                        val comparison = if (previous != null && current != null) {
                            when {
                                current - previous >= 5 -> "比上个周期（$previous%）更稳了。"
                                previous - current >= 5 -> "比上个周期（$previous%）有所下降。"
                                else -> "和上个周期（$previous%）接近。"
                            }
                        } else ""
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
                    Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        categories.forEach { (category, count) ->
                            Box(Modifier.weight(count.toFloat()).fillMaxSize()
                                .background(trendGradeColor(category), RoundedCornerShape(5.dp)))
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { (category, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(9.dp).background(trendGradeColor(category), CircleShape))
                                Text(
                                    "${trendCategoryLabel(category)} $count",
                                    style = MaterialTheme.typography.labelSmall,
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
                style = MaterialTheme.typography.titleLarge,
                fontFamily = NumberFontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun trendGradeColor(category: BloodPressureCategory): Color = when (category) {
    BloodPressureCategory.NORMAL -> Color(0xFF70B78D)
    BloodPressureCategory.HIGH_NORMAL -> Color(0xFFB0BE70)
    BloodPressureCategory.STAGE1 -> Color(0xFFE9C16F)
    BloodPressureCategory.STAGE2 -> Color(0xFFEE9B63)
    BloodPressureCategory.STAGE3 -> Color(0xFFE47674)
    BloodPressureCategory.LOW -> Color(0xFF7AA9D4)
}

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
            // 页面标题：周期 + 数据粒度，和实际聚合方式同源。
            Text(
                text = "${series.range.title} · ${series.aggregation.displayLabel()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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
            // 指标切换移动到图表正上方；「双曲线」改名为「双指标」。
            SegmentedControl(
                items = listOf(TrendMetricType.BOTH, TrendMetricType.SYSTOLIC, TrendMetricType.DIASTOLIC),
                selected = metric,
                label = {
                    when (it) {
                        TrendMetricType.SYSTOLIC -> "收缩压"
                        TrendMetricType.DIASTOLIC -> "舒张压"
                        TrendMetricType.BOTH -> "双指标"
                    }
                },
                onSelected = onMetricChange,
                accent = true
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

@Composable
private fun PulseTrendCard(
    series: TrendSeries,
    selectedPoint: TrendPoint?,
    chartController: TrendChartController,
    onPointSelected: (TrendPoint?) -> Unit
) {
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
                Text(
                    "平均 ${series.averagePulse} 次/分",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        // 无彩色大卡片和重复说明；日期始终保留准确时分。
        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = readoutDescription
            },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = formatReadoutFull(displayed.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${displayed.systolic}/${displayed.diastolic} mmHg",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = NumberFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    displayed.pulse?.let { "脉搏 $it 次/分" } ?: "脉搏 —",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (displayed.containsHighRiskReading) Text(
                    "高风险", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        // 字体放大或小屏时水平滚动，恢复与其他操作始终在同一行。
        Row(
            modifier = Modifier.fillMaxWidth().testTag("trendReadoutActions")
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReadoutAction(
                text = "上一条",
                onClick = { onPrevious?.invoke() ?: onPointSelected(points[index - 1]) },
                enabled = index > 0
            )
            ReadoutAction(
                text = "下一条",
                onClick = { onNext?.invoke() ?: onPointSelected(points[index + 1]) },
                enabled = index in 0 until points.lastIndex
            )
            ReadoutAction("明细", { onViewDayRecords(displayed) })
            if (onLatest != null) ReadoutAction("最新", onLatest)
            ReadoutAction("恢复", onResetChart)
        }
    }
}

/** 同行可滚动操作，触控高度至少 48dp，文字不换行。 */
@Composable
private fun ReadoutAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}
private fun buildReadoutDescription(point: TrendPoint, isSelected: Boolean, index: Int, total: Int): String {
    val pulse = point.pulse?.let { "脉搏 $it 次每分" } ?: "脉搏未记录"
    return "${if (isSelected) "已选中的数据点" else "最近一次测量"}，" +
        "${formatReadoutFull(point.timestamp)}，收缩压 ${point.systolic}，舒张压 ${point.diastolic}，" +
        "$pulse，第 ${index + 1} 个，共 $total 个数据点。"
}

private fun formatReadoutFull(measuredAt: Long): String =
    Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

@Composable
private fun <T> SegmentedControl(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    accent: Boolean = false
) {
    // 暖阳设计：选中态统一用暖色 primaryContainer（不再出现深色块），
    // 未选中的指标分段保留 surface + 轻投影的悬浮感。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.shapes.large
            )
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .shadow(
                        if (isSelected && !accent) 2.dp else 0.dp,
                        MaterialTheme.shapes.large,
                        clip = false
                    )
                    .background(
                        when {
                            isSelected && accent -> MaterialTheme.colorScheme.primaryContainer
                            isSelected -> MaterialTheme.colorScheme.surface
                            else -> Color.Transparent
                        },
                        MaterialTheme.shapes.large
                    )
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                    }
                    .clickable { onSelected(item) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isSelected && accent -> MaterialTheme.colorScheme.onPrimaryContainer
                        isSelected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
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
 * 避免「7/30 天单次测量节点」显示成「当日 1 次」而掩盖同一天的其他记录。
 */
private fun buildDayDetailsSubtitle(details: TrendDayDetails): String {
    if (details.loading) return "正在读取当天记录"
    if (details.error != null) return "当天记录读取失败"
    val records = details.records
    if (records.isEmpty()) return "当天没有原始记录"
    val systolic = records.map { it.systolic }.average().roundToInt()
    val diastolic = records.map { it.diastolic }.average().roundToInt()
    val prefix = if (details.point.aggregation == TrendAggregation.DAILY_RANGE) "参与统计" else "当日"
    return "$prefix ${records.size} 次，平均 $systolic/$diastolic mmHg · 点击查看原记录"
}

@Composable
private fun TrendDayRecordRow(record: TrendRecord, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                (if (selected) "已选中 · " else "") + formatSessionTime(record.measuredAt),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (record.containsHighRiskReading) "含高风险读数" else record.category.toChineseCategory(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${record.systolic} / ${record.diastolic} mmHg",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "脉搏 ${record.pulse?.toString() ?: "--"}",
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
