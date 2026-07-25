package com.example.bloodpressurerecord.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDateTimePicker(
    measuredAtText: String,
    onMeasuredAtChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val epoch = DateTimeInputFormatter.parse(measuredAtText, zoneId) ?: System.currentTimeMillis()
    val localDateTime = Instant.ofEpochMilli(epoch).atZone(zoneId).toLocalDateTime()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var dateTimeError by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        val selectedMillis = localDateTime.toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            val text = date.format(DateTimeFormatter.ISO_LOCAL_DATE) +
                                " " + localDateTime.toLocalTime()
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                            val next = DateTimeInputFormatter.parse(text, zoneId)
                            if (next == null) {
                                dateTimeError = "所选本地时间无效，请重新选择。"
                            } else {
                                dateTimeError = null
                                onMeasuredAtChange(DateTimeInputFormatter.format(next, zoneId))
                            }
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = dateState, title = { Text("选择测量日期") })
        }
    }

    if (showTimePicker) {
        DigitalTimeInputDialog(
            title = "选择测量时间",
            initialHour = localDateTime.hour,
            initialMinute = localDateTime.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                val time = LocalTime.of(hour, minute)
                val text = localDateTime.toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE) +
                    " " + time.format(DateTimeFormatter.ofPattern("HH:mm"))
                val next = DateTimeInputFormatter.parse(text, zoneId)
                if (next == null) {
                    dateTimeError = "所选本地时间无效，请重新选择。"
                } else {
                    dateTimeError = null
                    onMeasuredAtChange(DateTimeInputFormatter.format(next, zoneId))
                }
                showTimePicker = false
            }
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(AppSpacing.xSmall))
            Text(localDateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
        }
        OutlinedButton(
            onClick = { showTimePicker = true },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(Modifier.width(AppSpacing.xSmall))
            Text(localDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
        }
    }
    dateTimeError?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalTimeInputDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timeState = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TimeInput(
                state = timeState,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "24小时制时间输入，小时和分钟"
                    }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timeState.hour, timeState.minute) }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun MeasurementReadingCard(
    index: Int,
    reading: SessionReadingInputUi,
    removable: Boolean,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit,
    onPulseChange: (String) -> Unit,
    onRemove: () -> Unit = {}
) {
    val systolic = reading.systolic.toIntOrNull()
    val diastolic = reading.diastolic.toIntOrNull()
    val relationError = systolic != null && diastolic != null && diastolic >= systolic

    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("第${index + 1}组", style = MaterialTheme.typography.titleMedium)
                if (removable) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "删除第${index + 1}组读数"
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                NumberField(
                    value = reading.systolic,
                    onValueChange = onSystolicChange,
                    label = "收缩压",
                    imeAction = ImeAction.Next,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = reading.diastolic,
                    onValueChange = onDiastolicChange,
                    label = "舒张压",
                    imeAction = ImeAction.Next,
                    isError = relationError,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = reading.pulse,
                    onValueChange = onPulseChange,
                    label = "脉搏",
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f)
                )
            }
            if (relationError) {
                Text(
                    "舒张压必须低于收缩压",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.all(Char::isDigit) && next.length <= 3) onValueChange(next)
        },
        label = { Text(label, maxLines = 1) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction
        ),
        isError = isError,
        singleLine = true,
        modifier = modifier
    )
}

@Composable
fun SessionSaveBottomBar(
    canSave: Boolean,
    disabledReason: String,
    isSaving: Boolean,
    buttonText: String,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(
                horizontal = AppDimensions.bottomActionPadding,
                vertical = AppSpacing.small
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
    ) {
        if (!canSave && disabledReason.isNotBlank()) {
            Text(
                disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppPrimaryButton(
            text = if (isSaving) "正在保存…" else buttonText,
            onClick = onSave,
            enabled = canSave && !isSaving,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun UnsavedChangesDialog(
    onContinueEditing: () -> Unit,
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinueEditing,
        title = { Text("保留未保存内容？") },
        text = { Text("你可以继续编辑、保存草稿后退出，或放弃本次修改。") },
        confirmButton = {
            TextButton(onClick = onContinueEditing) { Text("继续编辑") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSaveDraft) { Text("保存草稿") }
                TextButton(onClick = onDiscard) { Text("放弃") }
            }
        }
    )
}
