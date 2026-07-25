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
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.ui.common.AppBackButton
import com.example.bloodpressurerecord.ui.common.DataCard
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
                text = "血压趋势分析",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = if (onBack != null) 4.dp else 0.dp)
            )
        }
        Text(
            "横轴按真实时间排列；双指缩放、拖动平移，双击图表恢复完整范围。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrendTextSummaryCard(uiState.summary)

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
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun TrendTextSummaryCard(summary: TrendTextSummary) {
    DataCard {
        if (summary.recordCount == 0) {
            Text("当前周期暂无足够数据生成文字摘要。")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("趋势文字摘要", style = MaterialTheme.typography.titleMedium)
                Text("测量次数：${summary.recordCount} 次")
                Text("周期平均：${summary.averageSystolic} / ${summary.averageDiastolic} mmHg")
                Text("最高值：${summary.highestSystolic} / ${summary.highestDiastolic} mmHg")
                Text("最低值：${summary.lowestSystolic} / ${summary.lowestDiastolic} mmHg")
                Text("高风险次数：${summary.highRiskCount} 次")
                val changeText = if (summary.systolicChange == null || summary.diastolicChange == null) {
                    "与上一周期相比：数据不足"
                } else {
                    "与上一周期平均相比：" +
                        "${summary.systolicChange.withSign()} / ${summary.diastolicChange.withSign()} mmHg"
                }
                Text(changeText)
                Text(
                    "短期变化仅供记录观察，不代表医学结论。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Int.withSign(): String = if (this > 0) "+$this" else toString()

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
                onSelected = onRangeChange
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
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .shadow(if (isSelected) 2.dp else 0.dp, RoundedCornerShape(14.dp), clip = false)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelected(item) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
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

private fun String.toChineseCategory(): String = when (uppercase()) {
    "NORMAL" -> "正常"
    "ELEVATED" -> "偏高"
    "STAGE1" -> "1期偏高"
    "STAGE2" -> "2期偏高"
    "SEVERE" -> "重度偏高"
    else -> this
}
