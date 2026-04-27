package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.StatusChip

@Composable
fun HistoryScreenRevamp(
    viewModel: HistoryViewModel,
    onOpenDetail: (String) -> Unit,
    onAddMeasurement: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(title = "历史记录")

        HistoryPeriodSelector(
            selected = uiState.periodType,
            count = uiState.totalCountInPeriod,
            onSelect = viewModel::setPeriodType,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (uiState.groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                EmptyHistoryState(onAddMeasurement)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                uiState.groups.forEach { group ->
                    item {
                        Text(
                            text = group.dateLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(group.sessions) { session ->
                        HistorySessionCard(session = session, onClick = { onOpenDetail(session.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPeriodSelector(
    selected: HistoryPeriodType,
    count: Int,
    onSelect: (HistoryPeriodType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HistoryPeriodTab(
                label = "Week",
                selected = selected == HistoryPeriodType.WEEK,
                onClick = { onSelect(HistoryPeriodType.WEEK) },
                modifier = Modifier.weight(1f)
            )
            HistoryPeriodTab(
                label = "Month",
                selected = selected == HistoryPeriodType.MONTH,
                onClick = { onSelect(HistoryPeriodType.MONTH) },
                modifier = Modifier.weight(1f)
            )
            HistoryPeriodTab(
                label = "All",
                selected = selected == HistoryPeriodType.ALL,
                onClick = { onSelect(HistoryPeriodType.ALL) },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "${selected.toDisplayName()} · $count 条记录",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun HistoryPeriodTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        tonalElevation = if (selected) 2.dp else 0.dp,
        shadowElevation = if (selected) 1.dp else 0.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun HistorySessionCard(session: HistorySessionItemUi, onClick: () -> Unit) {
    val isAbnormal = session.categoryText.contains("高") && !session.categoryText.contains("正常")

    DataCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.measuredAtText,
                    style = MaterialTheme.typography.titleMedium
                )
                StatusChip(text = session.categoryText, isAbnormal = isAbnormal)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                val parts = session.avgBloodPressureText.split("/")
                Text(
                    text = "${parts.getOrNull(0) ?: "--"} / ${parts.getOrNull(1) ?: "--"}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("mmHg", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }

            if (session.noteSummary != HistoryViewModel.NO_NOTE_TEXT || isAbnormal) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (session.noteSummary == HistoryViewModel.NO_NOTE_TEXT) "" else session.noteSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isAbnormal) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("异常", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("当前范围暂无记录", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "可切换到 All 查看更早历史，或新增一次测量。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        AppPrimaryButton(text = "新增测量", onClick = onAdd)
    }
}

private fun HistoryPeriodType.toDisplayName(): String = when (this) {
    HistoryPeriodType.DAY -> "今天"
    HistoryPeriodType.WEEK -> "本周"
    HistoryPeriodType.MONTH -> "本月"
    HistoryPeriodType.ALL -> "全部"
}
