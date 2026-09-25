package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.ui.common.AppBackButton
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
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

    uiState.dayDetails?.let { details ->
        TrendDayDetailsSheet(
            details = details,
            onDismiss = viewModel::dismissDayDetails
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.pageHorizontalPadding)
            .padding(
                top = statusBarTopPadding(extra = 16.dp),
                bottom = dockContentBottomPadding()
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
        Text(
            "点选查看读数，双指缩放；下方按钮可逐点浏览。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SegmentedControl(
            items = TrendRange.entries,
            selected = uiState.range,
            label = { it.label },
            onSelected = viewModel::setRange
        )

        if (uiState.series.points.isEmpty()) {
            TrendEmptySection(range = uiState.range, onAddMeasurement = onAddMeasurement)
        } else {
            TrendCard(
                series = uiState.series,
                metric = uiState.metric,
                targetSystolic = uiState.targetSystolic,
                targetDiastolic = uiState.targetDiastolic,
                selectedRange = uiState.range,
                onMetricChange = viewModel::setMetric,
                onPointActivated = viewModel::openPointDetails
            )
            TrendPeriodOverview(
                summary = uiState.summary,
                insights = uiState.insights,
                targetSystolic = uiState.targetSystolic,
                targetDiastolic = uiState.targetDiastolic
            )
            AccessibleTrendControls(
                series = uiState.series,
                onOpenDetails = viewModel::openPointDetails
            )
        }
    }
}

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

@Composable
private fun AccessibleTrendControls(
    series: TrendSeries,
    onOpenDetails: (com.example.bloodpressurerecord.domain.model.TrendPoint) -> Unit
) {
    val points = series.points
    if (points.isEmpty()) return
    var index by remember(points) { mutableIntStateOf(points.lastIndex) }
    val point = points[index.coerceIn(points.indices)]
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "图表数据 ${index + 1}/${points.size}：${formatTrendPointForAccessibility(point)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { index -= 1 }, enabled = index > 0) { Text("前一点") }
                    TextButton(onClick = { index += 1 }, enabled = index < points.lastIndex) { Text("后一点") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { index = points.lastIndex }) { Text("回到最新") }
                    if (point.aggregation == TrendAggregation.DAILY) {
                        TextButton(onClick = { onOpenDetails(point) }) { Text("查看明细") }
                    }
                }
            }
        }
    }
}

private fun formatTrendPointForAccessibility(
    point: com.example.bloodpressurerecord.domain.model.TrendPoint
): String {
    val date = Instant.ofEpochMilli(point.timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    return "$date，收缩压 ${point.systolic}，舒张压 ${point.diastolic}，" +
        "脉搏 ${point.pulse?.toString() ?: "未记录"}，${point.recordCount} 次记录"
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
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
private fun TrendTextSummaryCard(summary: TrendTextSummary, range: TrendRange) {
    // 暖阳设计 3d：口语化摘要（鼠尾草绿底）+ 最高/最低两列统计卡。
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (summary.recordCount == 0) {
                Text(
                    "这个周期还没有记录，先测一次吧。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                Text(
                    buildString {
                        append(range.title)
                        append("测了 ${summary.recordCount} 次，平均 ")
                        append("${summary.averageSystolic} / ${summary.averageDiastolic} mmHg")
                        val sysChange = summary.systolicChange
                        val diaChange = summary.diastolicChange
                        if (sysChange != null && diaChange != null) {
                            append("，比上一周期")
                            append(metricChangeText("收缩压", sysChange))
                            append("，")
                            append(metricChangeText("舒张压", diaChange))
                            append("。")
                        } else {
                            append("，整体情况以图表为准。")
                        }
                        if (summary.highRiskCount > 0) {
                            append("其中 ${summary.highRiskCount} 次含高风险读数。")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    if (summary.recordCount > 0) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExtremeStatCard(
                title = "收缩压范围",
                value = "${summary.lowestSystolic}–${summary.highestSystolic} mmHg",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            ExtremeStatCard(
                title = "舒张压范围",
                value = "${summary.lowestDiastolic}–${summary.highestDiastolic} mmHg",
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun metricChangeText(label: String, change: Int): String = when {
    change > 0 -> "$label 上升 $change mmHg"
    change < 0 -> "$label 下降 ${-change} mmHg"
    else -> "$label 持平"
}

@Composable
private fun ExtremeStatCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontSize = 22.sp,
                fontFamily = NumberFontFamily,
                color = valueColor
            )
        }
    }
}

@Composable
private fun TrendCard(
    series: TrendSeries,
    metric: TrendMetricType,
    targetSystolic: Int?,
    targetDiastolic: Int?,
    selectedRange: TrendRange,
    onMetricChange: (TrendMetricType) -> Unit,
    onPointActivated: (com.example.bloodpressurerecord.domain.model.TrendPoint) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    selectedRange.title + if (selectedRange == TrendRange.ALL) " · 每日平均" else " · 每次测量",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (series.rawRecordCount > 0) {
                    Text(
                        "${series.rawRecordCount} 次测量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SessionTimeSeriesDualLineChart(
                series = series,
                targetSystolic = targetSystolic,
                targetDiastolic = targetDiastolic,
                showSystolic = metric != TrendMetricType.DIASTOLIC,
                showDiastolic = metric != TrendMetricType.SYSTOLIC,
                emptyTitle = "${selectedRange.title} 暂无数据",
                averageLabel = "${selectedRange.title}平均",
                onPointActivated = onPointActivated
            )
            SegmentedControl(
                items = listOf(TrendMetricType.BOTH, TrendMetricType.SYSTOLIC, TrendMetricType.DIASTOLIC),
                selected = metric,
                label = {
                    when (it) {
                        TrendMetricType.SYSTOLIC -> "收缩压"
                        TrendMetricType.DIASTOLIC -> "舒张压"
                        TrendMetricType.BOTH -> "双曲线"
                    }
                },
                onSelected = onMetricChange,
                accent = true
            )
            if (series.points.isNotEmpty()) {
                val first = formatSessionDate(series.points.first().timestamp)
                val last = formatSessionDate(series.points.last().timestamp)
                Text(
                    "样本区间：$first - $last",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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

private fun formatSessionDate(measuredAt: Long): String {
    return Instant.ofEpochMilli(measuredAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}

private fun formatFullDate(measuredAt: Long): String = formatSessionDate(measuredAt)

private fun formatSessionTime(measuredAt: Long): String {
    return Instant.ofEpochMilli(measuredAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun String.toChineseCategory(): String =
    com.example.bloodpressurerecord.ui.common.CategoryPresentation.label(this)
