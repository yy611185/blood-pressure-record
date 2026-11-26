package com.example.bloodpressurerecord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.data.repository.MarkMedicationTakenResult
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import com.example.bloodpressurerecord.reminder.MedicationReminderConfirmationRequest
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal sealed interface MedicationConfirmationState {
    data object Loading : MedicationConfirmationState
    data class Ready(val slot: MedicationSlot, val date: LocalDate) : MedicationConfirmationState
    data class AlreadyTaken(val slot: MedicationSlot, val date: LocalDate) : MedicationConfirmationState
    data class Expired(val date: LocalDate) : MedicationConfirmationState
    data object LoadFailed : MedicationConfirmationState
}

@Composable
internal fun MedicationReminderConfirmationDialog(
    request: MedicationReminderConfirmationRequest,
    medicationRepository: MedicationRepository,
    onDismissNotification: (timeId: Long, epochDay: Long) -> Unit,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember(request) { mutableStateOf<MedicationConfirmationState>(MedicationConfirmationState.Loading) }
    var reloadToken by remember(request) { mutableIntStateOf(0) }
    var submitting by remember(request) { mutableStateOf(false) }
    var submitError by remember(request) { mutableStateOf<String?>(null) }

    LaunchedEffect(request, reloadToken) {
        state = MedicationConfirmationState.Loading
        submitError = null
        val date = LocalDate.ofEpochDay(request.dateEpochDay)
        state = try {
            val slot = medicationRepository.getSlotsForDay(date)
                .firstOrNull { it.medicationId == request.medicationId && it.timeId == request.timeId }
            when {
                slot == null -> MedicationConfirmationState.Expired(date)
                slot.taken -> MedicationConfirmationState.AlreadyTaken(slot, date)
                else -> MedicationConfirmationState.Ready(slot, date)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            MedicationConfirmationState.LoadFailed
        }
    }

    when (val current = state) {
        MedicationConfirmationState.Loading -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("正在读取服药计划") },
                text = { Text("请稍候，正在核对这条服药提醒对应的药品与时间。") },
                confirmButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("取消") }
                }
            )
        }

        is MedicationConfirmationState.Ready -> {
            val scheduleLabel = formatReminderScheduleLabel(current.date, current.slot.timeText)
            AlertDialog(
                onDismissRequest = { if (!submitting) onDismiss() },
                modifier = Modifier.testTag("medicationConfirmDialog"),
                title = { Text("确认服药打卡") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = current.slot.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (current.slot.dosage.isNotBlank()) {
                            Text(
                                text = "每次用量：${current.slot.dosage}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = "计划时间：$scheduleLabel",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "请核对以上药品与时间后再确认打卡；打卡仅作为个人记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        submitError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !submitting,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("medicationConfirmButton"),
                        onClick = {
                            if (submitting) return@TextButton
                            submitting = true
                            submitError = null
                            scope.launch {
                                val result = medicationRepository.markTakenFromReminder(
                                    medicationId = current.slot.medicationId,
                                    timeId = current.slot.timeId,
                                    date = current.date,
                                    expectedSlot = current.slot
                                )
                                result.onSuccess { markResult ->
                                    onDismissNotification(current.slot.timeId, current.date.toEpochDay())
                                    submitting = false
                                    onDismiss()
                                    when (markResult) {
                                        MarkMedicationTakenResult.RECORDED ->
                                            onFeedback("已打卡 · ${current.slot.name}（${current.slot.timeText}）")
                                        MarkMedicationTakenResult.ALREADY_TAKEN ->
                                            onFeedback("该服药计划已打卡。")
                                        MarkMedicationTakenResult.EXPIRED_OR_REMOVED ->
                                            onFeedback("该服药计划已修改或删除，未写入打卡。")
                                    }
                                }.onFailure { throwable ->
                                    if (throwable is CancellationException) throw throwable
                                    submitting = false
                                    submitError = "保存打卡失败，请重试。"
                                }
                            }
                        }
                    ) { Text(if (submitting) "正在打卡…" else "确认已打卡") }
                },
                dismissButton = {
                    TextButton(
                        enabled = !submitting,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("medicationDismissButton"),
                        onClick = onDismiss
                    ) { Text("暂不打卡") }
                }
            )
        }

        is MedicationConfirmationState.AlreadyTaken -> {
            val scheduleLabel = formatReminderScheduleLabel(current.date, current.slot.timeText)
            val dosagePart = if (current.slot.dosage.isNotBlank()) "（${current.slot.dosage}）" else ""
            val acknowledge = {
                onDismissNotification(current.slot.timeId, current.date.toEpochDay())
                onDismiss()
            }
            AlertDialog(
                onDismissRequest = acknowledge,
                modifier = Modifier.testTag("medicationAlreadyTakenDialog"),
                title = { Text("该服药计划已打卡") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "「${current.slot.name}」$dosagePart · $scheduleLabel 已完成打卡，无需重复记录。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = acknowledge,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("知道了") }
                }
            )
        }

        is MedicationConfirmationState.Expired -> {
            val acknowledge = {
                onDismissNotification(request.timeId, request.dateEpochDay)
                onDismiss()
            }
            AlertDialog(
                onDismissRequest = acknowledge,
                modifier = Modifier.testTag("medicationExpiredDialog"),
                title = { Text("提醒已失效") },
                text = {
                    Text("这条服药提醒对应的药品或时间点已修改或删除，未写入打卡。")
                },
                confirmButton = {
                    TextButton(
                        onClick = acknowledge,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("知道了") }
                }
            )
        }

        MedicationConfirmationState.LoadFailed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("暂时无法读取服药计划") },
                text = { Text("读取该提醒对应的药品信息失败，请重试或在首页手动核对打卡。") },
                confirmButton = {
                    TextButton(
                        onClick = { reloadToken += 1 },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("重试") }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("关闭") }
                }
            )
        }
    }
}

internal fun formatReminderScheduleLabel(
    date: LocalDate,
    timeText: String,
    today: LocalDate = LocalDate.now(ZoneId.systemDefault())
): String = when (date) {
    today -> "今天 $timeText"
    today.minusDays(1) -> "昨天（${date.monthValue}月${date.dayOfMonth}日）$timeText"
    else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日 $timeText"
}
