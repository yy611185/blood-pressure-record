package com.example.bloodpressurerecord.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.Terracotta700
import com.example.bloodpressurerecord.ui.theme.Terracotta800
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import java.time.Instant
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
            DatePicker(
                state = dateState,
                title = {
                    Text(
                        "选择测量日期",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }

    if (showTimePicker) {
        WheelTimePickerDialog(
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
        // 药丸日期/时间按钮。之前用默认 OutlinedButton（24dp 内边距 + 24dp 图标），
        // 在大字号下半宽按钮放不下完整日期导致文字被截断；
        // 这里收紧内边距、缩小图标并让文字自动缩排，保证完整显示。
        DateTimePillButton(
            icon = Icons.Default.CalendarMonth,
            text = localDateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1.25f)
        )
        DateTimePillButton(
            icon = Icons.Default.Schedule,
            text = localDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
            onClick = { showTimePicker = true },
            modifier = Modifier.weight(0.75f)
        )
    }
    dateTimeError?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DateTimePillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Terracotta700,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false
        )
    }
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
                Text("第 ${index + 1} 组", style = MaterialTheme.typography.titleMedium)
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
                    label = "收缩压（高压）",
                    imeAction = ImeAction.Next,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = reading.diastolic,
                    onValueChange = onDiastolicChange,
                    label = "舒张压（低压）",
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
                    "低压要小于高压，检查一下再保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (index == 0) {
                Text(
                    "建议连续测两次，间隔 1-2 分钟，取平均更准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                if (next.all(Char::isDigit) && next.length <= 3) onValueChange(next)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            isError = isError,
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            textStyle = TextStyle(
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                color = Terracotta800
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            label = null,
            placeholder = null
        )
    }
}

@Composable
fun SessionSaveBottomBar(
    canSave: Boolean,
    disabledReason: String,
    isSaving: Boolean,
    buttonText: String,
    onSave: () -> Unit,
    /** 嵌入滚动页面时使用完整圆角；默认仍作为固定底栏，仅保留顶部圆角。 */
    embedded: Boolean = false
) {
    val containerShape = if (embedded) {
        MaterialTheme.shapes.medium
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                containerShape
            )
            .navigationBarsPadding()
            .imePadding()
            .padding(
                horizontal = if (embedded) AppSpacing.medium else AppDimensions.bottomActionPadding,
                vertical = AppSpacing.medium
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
            modifier = Modifier.fillMaxWidth().height(AppDimensions.saveButtonHeight)
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
