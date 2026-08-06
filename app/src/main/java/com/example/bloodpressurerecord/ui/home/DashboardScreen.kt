package com.example.bloodpressurerecord.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.Sage200
import com.example.bloodpressurerecord.ui.theme.Sage500
import com.example.bloodpressurerecord.ui.theme.Sage600
import com.example.bloodpressurerecord.ui.theme.Sage900
import com.example.bloodpressurerecord.ui.theme.Terracotta600
import com.example.bloodpressurerecord.ui.theme.Terracotta700
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddMeasurement: () -> Unit,
    onViewTodayRecords: () -> Unit,
    onOpenMedicationSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.pageHorizontalPadding)
            .padding(top = AppSpacing.large, bottom = AppSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
    ) {
        GreetingHeader(state)

        when {
            state.loading -> DataCard { Text("正在读取最近记录…") }
            state.latest == null -> FirstMeasurementCard(onAddMeasurement)
            else -> RecentReadingCard(state.latest!!)
        }

        if (state.latest != null) {
            StreakCard(streakDays = state.streakDays, weekRecorded = state.weekRecorded)
        }

        AppPrimaryButton(
            text = "记一次血压",
            icon = Icons.Default.Add,
            onClick = onAddMeasurement,
            modifier = Modifier.fillMaxWidth()
        )

        TodayOverviewCard(
            state = state,
            onViewTodayRecords = onViewTodayRecords
        )

        MedicationTodayCard(
            slots = state.medicationSlots,
            pendingTimeIds = state.pendingMedicationTimeIds,
            onToggle = viewModel::toggleMedicationTaken,
            onOpenSettings = onOpenMedicationSettings
        )
    }
}

@Composable
private fun MedicationTodayCard(
    slots: List<MedicationSlot>,
    pendingTimeIds: Set<Long>,
    onToggle: (MedicationSlot, Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("今日服药", style = MaterialTheme.typography.titleMedium)
                if (slots.isNotEmpty()) {
                    Text(
                        "已服 ${slots.count { it.taken }}/${slots.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (slots.all { it.taken }) {
                            Sage600
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (slots.isEmpty()) {
                Text(
                    "还没有添加药品。设置好每天的服药时间，这里就能打卡。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        "去添加药品和提醒 →",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Terracotta700
                    )
                }
            } else {
                slots.forEach { slot ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            slot.timeText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (slot.taken) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                Terracotta700
                            }
                        )
                        Spacer(Modifier.width(AppSpacing.medium))
                        Text(
                            "${slot.name} ${slot.dosage}".trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (slot.taken) TextDecoration.LineThrough else null,
                            color = if (slot.taken) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = slot.taken,
                            enabled = slot.timeId !in pendingTimeIds,
                            onCheckedChange = { checked -> onToggle(slot, checked) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingHeader(state: DashboardUiState) {
    val now = LocalTime.now()
    val (greeting, period) = when {
        now.hour < 5 -> "夜深了" to "深夜"
        now.hour < 11 -> "早上好" to "早晨"
        now.hour < 13 -> "中午好" to "中午"
        now.hour < 18 -> "下午好" to "下午"
        else -> "晚上好" to "晚上"
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
        Text(
            state.today.format(DateTimeFormatter.ofPattern("M月d日 EEEE")) + " · " + period,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(greeting, style = MaterialTheme.typography.headlineLarge)
        Text(
            "记录是为了帮你观察变化，身体的感觉你最懂。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${session.avgSystolic} / ${session.avgDiastolic}",
                    style = MaterialTheme.typography.displayMedium,
                    color = Terracotta700,
                    maxLines = 1
                )
                Spacer(Modifier.width(AppSpacing.small))
                Text(
                    "mmHg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            StatusChip(
                text = friendlyStatusText(session),
                isAbnormal = visualStatus.name != "NORMAL",
                status = visualStatus
            )
        }
    }
}

private fun friendlyStatusText(session: LatestSessionSummary): String = when {
    session.containsHighRiskReading -> "含高风险读数，注意休息"
    session.category.uppercase() == "NORMAL" -> "血压平稳，继续保持"
    session.category.uppercase() == "LOW" -> "这次偏低，留意身体感觉"
    session.category.uppercase() == "HIGH_NORMAL" -> "比理想值稍高，放松一下"
    else -> "这次偏高，注意休息"
}

@Composable
private fun StreakCard(streakDays: Int, weekRecorded: List<Boolean>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Sage200),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = Terracotta600,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(AppSpacing.medium))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (streakDays > 0) "已连续记录 $streakDays 天" else "今天测一次，开始连续打卡",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Sage900
                )
                Text(
                    if (streakDays > 0) "坚持得很好，为自己鼓个掌" else "一天一次，慢慢来就好",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sage600
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                weekRecorded.forEach { recorded ->
                    if (recorded) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Sage600, CircleShape)
                        )
                    } else {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(10.dp)
                                .border(1.5.dp, Sage500, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayOverviewCard(
    state: DashboardUiState,
    onViewTodayRecords: () -> Unit
) {
    // 紧凑版：标题行 + 一行数据，占地更小；无记录时不再显示日历入口。
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("今日概览", style = MaterialTheme.typography.titleMedium)
                if (state.todayCount > 0) {
                    TextButton(
                        onClick = onViewTodayRecords,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            "看看今天的记录 →",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Terracotta700
                        )
                    }
                }
            }
            if (state.todayCount > 0 &&
                state.todayAverageSystolic != null &&
                state.todayAverageDiastolic != null
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("今天已经测了 ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${state.todayCount}",
                        fontSize = 20.sp,
                        color = Terracotta700,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = com.example.bloodpressurerecord.ui.theme.NumberFontFamily
                        )
                    )
                    Text(" 次，平均 ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${state.todayAverageSystolic} / ${state.todayAverageDiastolic}",
                        fontSize = 20.sp,
                        color = Terracotta700,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = com.example.bloodpressurerecord.ui.theme.NumberFontFamily
                        )
                    )
                    Text(" mmHg", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    "今天还没有测量，找个安静的时间歇五分钟再测。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
