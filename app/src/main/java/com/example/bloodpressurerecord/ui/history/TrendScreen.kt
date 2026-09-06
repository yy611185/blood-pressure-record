package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.ui.common.AppBackButton
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendScreen(
    viewModel: TrendViewModel,
    onBack: (() -> Unit)? = null
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
            .padding(16.dp),
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
            "横轴按真实时间排列；可用图表手势，也可用图表下方按钮逐点查看。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrendTextSummaryCard(uiState.summary, uiState.range)

        TrendCard(
            series = uiState.series,
            metric = uiState.metric,
            targetSystolic = uiState.targetSystolic,
            targetDiastolic = uiState.targetDiastolic,
            selectedRange = uiState.range,
            onRangeChange = viewModel::setRange,
            onMetricChange = viewModel::setMetric,
            onPointActivated = viewModel::openPointDetails
        )
        AccessibleTrendControls(
            series = uiState.series,
            onOpenDetails = viewModel::openPointDetails
        )
        Spacer(Modifier.height(10.dp))
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
    onRangeChange: (TrendRange) -> Unit,
    onMetricChange: (TrendMetricType) -> Unit,
    onPointActivated: (com.example.bloodpressurerecord.domain.model.TrendPoint) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SegmentedControl(
                items = TrendRange.entries,
                selected = selectedRange,
                label = { it.label },
                onSelected = onRangeChange,
                accent = true
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    selectedRange.title,
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
            SegmentedControl(
                items = listOf(
                    TrendMetricType.SYSTOLIC,
                    TrendMetricType.DIASTOLIC,
                    TrendMetricType.BOTH
                ),
                selected = metric,
                label = {
                    when (it) {
                        TrendMetricType.SYSTOLIC -> "收缩压"
                        TrendMetricType.DIASTOLIC -> "舒张压"
                        TrendMetricType.BOTH -> "双曲线"
                    }
                },
                onSelected = onMetricChange
            )
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
    // 暖阳设计：范围分段用陶土橙实底（accent），指标分段用 surface + 投影。
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
                    .heightIn(min = 44.dp)
                    .shadow(
                        if (isSelected && !accent) 2.dp else 0.dp,
                        MaterialTheme.shapes.large,
                        clip = false
                    )
                    .background(
                        when {
                            isSelected && accent -> MaterialTheme.colorScheme.primary
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
                        isSelected && accent -> MaterialTheme.colorScheme.onPrimary
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
