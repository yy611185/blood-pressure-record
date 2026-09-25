package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.displayLabel
import com.example.bloodpressurerecord.ui.common.AppBackButton
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendScreen(
    viewModel: TrendViewModel,
    onBack: (() -> Unit)? = null,
    onAddMeasurement: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 选中读数提升到页面层：范围切换时重置，不受图表重组影响。
    var selectedPoint by remember(uiState.range) { mutableStateOf<TrendPoint?>(null) }

    uiState.dayDetails?.let { details ->
        TrendDayDetailsSheet(
            details = details,
            onDismiss = viewModel::dismissDayDetails
        )
    }

    val series = uiState.series
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.pageHorizontalPadding)
            .padding(
                top = statusBarTopPadding(extra = 16.dp),
                bottom = dockContentBottomPadding()
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
                modifier = Modifier.padding(start = if (onBack != null) 4.dp else 0.dp)
            )
        }

        SegmentedControl(
            items = TrendRange.entries,
            selected = uiState.range,
            label = { it.label },
            onSelected = { range ->
                selectedPoint = null
                viewModel.setRange(range)
            }
        )

        if (series.points.isEmpty()) {
            TrendEmptySection(range = uiState.range, onAddMeasurement = onAddMeasurement)
            return@Column
        }

        // 紧凑摘要：周期、次数、平均值、样本区间与最近一次测量一次说完。
        TrendCompactSummary(
            series = series,
            summary = uiState.summary
        )

        TrendCard(
            series = series,
            metric = uiState.metric,
            selectedPoint = selectedPoint,
            targetSystolic = uiState.targetSystolic,
            targetDiastolic = uiState.targetDiastolic,
            onMetricChange = viewModel::setMetric,
            onPointSelected = { point -> selectedPoint = point },
            onViewDayRecords = viewModel::openPointDetails
        )

        TrendPeriodOverview(
            summary = uiState.summary,
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
    summary: TrendTextSummary
) {
    val averageText = if (summary.averageSystolic != null && summary.averageDiastolic != null) {
        "${summary.averageSystolic}/${summary.averageDiastolic}"
    } else {
        "—"
    }
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
                    "${summary.recordCount} 次测量" +
                        if (summary.highRiskCount > 0) " · ${summary.highRiskCount} 次高风险" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    averageText,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = NumberFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "mmHg 平均",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(Modifier.weight(1f))
                Text(
                    buildSampleRangeText(series),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

/**
 * 样本区间：明确写出实际有数据的日期以及最近一次测量日期，
 * 避免用户把「某天没有记录」误读成应用忽略了数据。
 */
private fun buildSampleRangeText(series: TrendSeries): String {
    val first = series.firstMeasuredAt ?: return "本周期还没有记录"
    val last = series.lastMeasuredAt ?: return "本周期还没有记录"
    val firstDate = formatSampleDate(first)
    val lastDate = formatSampleDate(last)
    val range = if (firstDate == lastDate) firstDate else "$firstDate – $lastDate"
    return "样本 $range · 最近一次 ${formatSampleDateTime(last)}"
}

private fun formatSampleDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日"))

private fun formatSampleDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))

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
    summary: TrendTextSummary,
    insights: TrendInsights,
    targetSystolic: Int?,
    targetDiastolic: Int?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("这段时间", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(
            "${summary.recordCount} 次测量" + if (summary.highRiskCount > 0) " · ${summary.highRiskCount} 次高风险" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
            TrendStatCell("平均", "${summary.averageSystolic ?: "—"}/${summary.averageDiastolic ?: "—"}", "mmHg", Modifier.weight(1f))
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
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrendStatCell(
                "上午平均",
                insights.morningAverage?.let { "${it.first}/${it.second}" } ?: "—",
                "${insights.morningCount} 次 · 12 点前",
                Modifier.weight(1f)
            )
            TrendStatCell(
                "下午及晚上",
                insights.afternoonAverage?.let { "${it.first}/${it.second}" } ?: "—",
                "${insights.afternoonCount} 次 · 12 点后",
                Modifier.weight(1f)
            )
        }
    }
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
    targetSystolic: Int?,
    targetDiastolic: Int?,
    onMetricChange: (TrendMetricType) -> Unit,
    onPointSelected: (TrendPoint?) -> Unit,
    onViewDayRecords: (TrendPoint) -> Unit
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
                onPointSelected = { point, _ -> onPointSelected(point) },
                targetSystolic = targetSystolic,
                targetDiastolic = targetDiastolic,
                showSystolic = metric != TrendMetricType.DIASTOLIC,
                showDiastolic = metric != TrendMetricType.SYSTOLIC,
                emptyTitle = "${series.range.title} 暂无数据"
            )
            // 逐点浏览整合在选中读数区域（原来是独立的大卡片）。
            TrendSelectedReadout(
                series = series,
                selectedPoint = selectedPoint,
                onPointSelected = onPointSelected,
                onViewDayRecords = onViewDayRecords
            )
        }
    }
}

/**
 * 选中读数区：显示完整日期时间、收缩压、舒张压、脉搏和状态，
 * 并集成「上一条 / 下一条」逐点浏览能力，对 TalkBack 同样可读可操作。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendSelectedReadout(
    series: TrendSeries,
    selectedPoint: TrendPoint?,
    onPointSelected: (TrendPoint?) -> Unit,
    onViewDayRecords: (TrendPoint) -> Unit
) {
    val points = series.points
    if (points.isEmpty()) return
    val displayed = selectedPoint ?: points.last()
    val isSelected = selectedPoint != null
    // 极端情况下选中点可能已经不在新序列里，索引兜底为最后一点，避免越界。
    val index = points.indexOfFirst { it.id == displayed.id }.takeIf { it >= 0 } ?: points.lastIndex
    val canGoPrevious = index > 0
    val canGoNext = index in 0 until points.lastIndex
    val canViewDayRecords = displayed.aggregation == TrendAggregation.DAILY
    val background = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val readoutDescription = remember(displayed.id, isSelected, index, points.size) {
        buildReadoutDescription(displayed, isSelected, index, points.size)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = readoutDescription
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = buildReadoutTitle(displayed, isSelected),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${displayed.systolic}/${displayed.diastolic} mmHg",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = NumberFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                StatusChip(
                    text = CategoryPresentation.label(displayed.category),
                    isAbnormal = !displayed.category.equals("NORMAL", true),
                    status = bloodPressureVisualStatus(displayed.category, false)
                )
                if (displayed.containsHighRiskReading) {
                    StatusChip(
                        "高风险",
                        true,
                        status = bloodPressureVisualStatus(displayed.category, true)
                    )
                }
            }
        }
        Text(
            "脉搏 ${displayed.pulse?.toString() ?: "未记录"}" +
                if (displayed.aggregation == TrendAggregation.DAILY) {
                    " · 当日 ${displayed.recordCount} 次"
                } else {
                    ""
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(
                onClick = { onPointSelected(points[index - 1]) },
                enabled = canGoPrevious
            ) { Text("上一条") }
            TextButton(
                onClick = { onPointSelected(points[index + 1]) },
                enabled = canGoNext
            ) { Text("下一条") }
            if (canViewDayRecords) {
                TextButton(onClick = { onViewDayRecords(displayed) }) { Text("查看当日记录") }
            }
            if (isSelected) {
                TextButton(onClick = { onPointSelected(null) }) { Text("取消选择") }
            }
        }
    }
}

private fun buildReadoutTitle(point: TrendPoint, isSelected: Boolean): String {
    val prefix = if (isSelected) "已选中" else "最近一次"
    return when (point.aggregation) {
        TrendAggregation.DAILY ->
            "$prefix · ${formatReadoutDay(point.timestamp)} · ${point.recordCount} 次平均"
        TrendAggregation.RAW ->
            "$prefix · ${formatReadoutFull(point.timestamp)}"
    }
}

private fun buildReadoutDescription(
    point: TrendPoint,
    isSelected: Boolean,
    index: Int,
    total: Int
): String {
    val position = if (index >= 0) "第 ${index + 1} 个，共 $total 个数据点" else "共 $total 个数据点"
    return when (point.aggregation) {
        TrendAggregation.DAILY ->
            "${if (isSelected) "已选中的每日平均" else "最近的每日平均"}，" +
                "${formatFullDate(point.timestamp)}，当日 ${point.recordCount} 次测量，平均收缩压 ${point.systolic}，" +
                "平均舒张压 ${point.diastolic}，脉搏 ${point.pulse ?: "未记录"}，$position。"
        TrendAggregation.RAW ->
            "${if (isSelected) "已选中的数据点" else "最近一次测量"}，" +
                "${formatReadoutFull(point.timestamp)}，收缩压 ${point.systolic}，舒张压 ${point.diastolic}，" +
                "脉搏 ${point.pulse ?: "未记录"}，$position。"
    }
}

private fun formatReadoutDay(measuredAt: Long): String =
    Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日"))

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
private fun TrendDayDetailsSheet(
    details: TrendDayDetails,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${formatFullDate(details.point.timestamp)} 测量明细",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "当日 ${details.point.recordCount} 次，平均 " +
                    "${details.point.systolic}/${details.point.diastolic} mmHg",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                details.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                details.error != null -> {
                    Text(details.error, color = MaterialTheme.colorScheme.error)
                }

                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(details.records, key = TrendRecord::id) { record ->
                            TrendDayRecordRow(record)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendDayRecordRow(record: TrendRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(formatSessionTime(record.measuredAt), fontWeight = FontWeight.SemiBold)
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
