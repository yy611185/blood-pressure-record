package com.example.bloodpressurerecord.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.history.HistoryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    historyViewModel: HistoryViewModel,
    onAddMeasurement: () -> Unit
) {
    val historyState by historyViewModel.uiState.collectAsState()
    val allSessions = historyState.sessionsAll
    val today = LocalDate.now()
    val todaySessions = allSessions.filter {
        Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }
    val recentSession = allSessions.maxByOrNull { it.measuredAt }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "血压记录",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "今天也记一下，方便长期观察",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (recentSession != null) {
            RecentReadingCard(recentSession)
        }

        AppPrimaryButton(
            text = "新增测量",
            icon = Icons.Default.Add,
            onClick = onAddMeasurement,
            modifier = Modifier.fillMaxWidth()
        )

        TodayOverviewCard(todaySessions)
    }
}

@Composable
fun RecentReadingCard(session: SessionRecord) {
    val isAbnormal = session.category != "NORMAL"
    val categoryText = session.category.toChineseCategoryLabel()

    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("最近一次血压", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = Instant.ofEpochMilli(session.measuredAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${session.avgSystolic} / ${session.avgDiastolic}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "mmHg",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(text = categoryText, isAbnormal = isAbnormal)
                if (isAbnormal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "偏高，请注意休息",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TodayOverviewCard(sessions: List<SessionRecord>) {
    val count = sessions.size
    val avgSys = if (count > 0) sessions.sumOf { it.avgSystolic } / count else 0
    val avgDia = if (count > 0) sessions.sumOf { it.avgDiastolic } / count else 0

    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("今日概览", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("测量次数", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$count 次", fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("平均血压", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (count > 0) "$avgSys / $avgDia mmHg" else "--", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun String.toChineseCategoryLabel(): String = when (uppercase()) {
    "NORMAL" -> "正常"
    "ELEVATED" -> "偏高"
    "STAGE1" -> "1期偏高"
    "STAGE2" -> "2期偏高"
    "SEVERE" -> "重度偏高"
    else -> this
}
