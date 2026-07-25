package com.example.bloodpressurerecord.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddMeasurement: () -> Unit,
    onViewTodayRecords: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(AppDimensions.pageHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
    ) {
        Column {
            Text("血压记录", style = MaterialTheme.typography.headlineMedium)
            Text(
                "记录用于帮助观察变化，不替代医疗诊断。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            state.loading -> DataCard { Text("正在读取最近记录…") }
            state.latest == null -> FirstMeasurementCard(onAddMeasurement)
            else -> RecentReadingCard(state.latest!!)
        }

        AppPrimaryButton(
            text = "新增测量",
            icon = Icons.Default.Add,
            onClick = onAddMeasurement,
            modifier = Modifier.fillMaxWidth()
        )

        TodayOverviewCard(
            state = state,
            onViewTodayRecords = onViewTodayRecords
        )
    }
}
@Composable
private fun FirstMeasurementCard(onAdd: () -> Unit) {
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Text("还没有血压记录", style = MaterialTheme.typography.titleLarge)
            Text(
                "完成第一次测量后，这里会优先显示最近血压和今天的测量情况。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onAdd) { Text("开始第一次测量") }
        }
    }
}

@Composable
fun RecentReadingCard(session: LatestSessionSummary) {
    val visualStatus = bloodPressureVisualStatus(
        session.category,
        session.containsHighRiskReading
    )
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("最近一次血压", style = MaterialTheme.typography.titleMedium)
                Text(
                    Instant.ofEpochMilli(session.measuredAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${session.avgSystolic} / ${session.avgDiastolic}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Spacer(Modifier.width(AppSpacing.small))
                Text("mmHg", style = MaterialTheme.typography.bodyLarge)
            }
            StatusChip(
                text = visualStatus.styleLabel(session.category),
                isAbnormal = visualStatus.name != "NORMAL",
                status = visualStatus
            )
        }
    }
}

@Composable
private fun TodayOverviewCard(
    state: DashboardUiState,
    onViewTodayRecords: () -> Unit
) {
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Text("今日概览", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.todayCount > 0) {
                    "今天已测量 ${state.todayCount} 次"
                } else {
                    "今天还没有测量"
                },
                fontWeight = FontWeight.SemiBold
            )
            if (state.todayAverageSystolic != null && state.todayAverageDiastolic != null) {
                Text(
                    "今日平均 ${state.todayAverageSystolic} / ${state.todayAverageDiastolic} mmHg",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = onViewTodayRecords) {
                androidx.compose.material3.Icon(Icons.Default.CalendarToday, contentDescription = null)
                Spacer(Modifier.width(AppSpacing.xSmall))
                Text(if (state.todayCount > 0) "查看今天记录" else "查看本月日历")
            }
        }
    }
}

private fun com.example.bloodpressurerecord.ui.theme.BloodPressureVisualStatus.styleLabel(
    category: String
): String = when (this) {
    com.example.bloodpressurerecord.ui.theme.BloodPressureVisualStatus.NORMAL -> "正常"
    com.example.bloodpressurerecord.ui.theme.BloodPressureVisualStatus.ELEVATED -> "偏高"
    com.example.bloodpressurerecord.ui.theme.BloodPressureVisualStatus.HIGH ->
        when (category.uppercase()) {
            "STAGE1" -> "1级偏高"
            "STAGE2" -> "2级偏高"
            else -> "严重偏高"
        }
    com.example.bloodpressurerecord.ui.theme.BloodPressureVisualStatus.HIGH_RISK -> "含高风险读数"
}
