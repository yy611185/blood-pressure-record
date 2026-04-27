package com.example.bloodpressurerecord.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.*
import com.example.bloodpressurerecord.ui.home.HomeViewModel

@Composable
fun AddMeasurementScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Form validation check to enable/disable auto calculation result card
    val allReadings = listOf(uiState.reading1, uiState.reading2) + uiState.extraReadings
    val validReadings = allReadings.filter { it.systolic.toIntOrNull() != null && it.diastolic.toIntOrNull() != null }
    val isAbnormal = uiState.categoryLabel.contains("高") && !uiState.categoryLabel.contains("正常")

    // Handle dialogs
    if (uiState.showAbnormalConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAbnormalDialog,
            title = { Text("数值异常提示") },
            text = { Text(uiState.abnormalConfirmMessage) },
            confirmButton = { TextButton(onClick = viewModel::confirmAbnormalAndContinue) { Text("继续保存") } },
            dismissButton = { TextButton(onClick = viewModel::dismissAbnormalDialog) { Text("返回修改") } }
        )
    }
    if (uiState.showHighRiskDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHighRiskDialog,
            title = { Text("高风险提醒") },
            text = { Text("检测到读数超过 180/120，请注意休息，必要时及时就医。是否继续保存？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmHighRiskAndSave()
                    onSaved()
                }) { Text("确认保存") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissHighRiskDialog) { Text("返回修改") } }
        )
    }
    
    // Auto back when save is completely successful and no dialogs
    LaunchedEffect(uiState.formMessage) {
        if (uiState.formMessage == "保存成功。") {
            onSaved()
            viewModel.clearForm()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(title = "新增测量", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MeasurementInputCard(
                title = "第 1 组",
                systolic = uiState.reading1.systolic,
                diastolic = uiState.reading1.diastolic,
                pulse = uiState.reading1.pulse,
                onSystolicChange = viewModel::updateReading1Systolic,
                onDiastolicChange = viewModel::updateReading1Diastolic,
                onPulseChange = viewModel::updateReading1Pulse
            )

            MeasurementInputCard(
                title = "第 2 组",
                systolic = uiState.reading2.systolic,
                diastolic = uiState.reading2.diastolic,
                pulse = uiState.reading2.pulse,
                onSystolicChange = viewModel::updateReading2Systolic,
                onDiastolicChange = viewModel::updateReading2Diastolic,
                onPulseChange = viewModel::updateReading2Pulse
            )

            if (uiState.showExtraReadings) {
                uiState.extraReadings.forEachIndexed { index, reading ->
                    MeasurementInputCard(
                        title = "第 ${index + 3} 组",
                        systolic = reading.systolic,
                        diastolic = reading.diastolic,
                        pulse = reading.pulse,
                        onSystolicChange = { viewModel.updateExtraReadingSystolic(index, it) },
                        onDiastolicChange = { viewModel.updateExtraReadingDiastolic(index, it) },
                        onPulseChange = { viewModel.updateExtraReadingPulse(index, it) }
                    )
                }
            }

            TextButton(
                onClick = { viewModel.addNextReadingGroup() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加下一组")
            }

            if (validReadings.size >= 2) {
                DataCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("自动计算结果", style = MaterialTheme.typography.titleMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("平均血压", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${uiState.avgSystolic ?: "--"} / ${uiState.avgDiastolic ?: "--"} mmHg", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("读数分级", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StatusChip(text = uiState.categoryLabel, isAbnormal = isAbnormal)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注 (例如：饭后、睡前)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            AppPrimaryButton(
                text = "保存记录",
                onClick = viewModel::onSaveClicked,
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.formMessage.isNotBlank() && uiState.formMessage != "保存成功。") {
                Text(
                    text = uiState.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun MeasurementInputCard(
    title: String,
    systolic: String,
    diastolic: String,
    pulse: String,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit,
    onPulseChange: (String) -> Unit
) {
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = systolic,
                    onValueChange = onSystolicChange,
                    label = { Text("收缩压") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = diastolic,
                    onValueChange = onDiastolicChange,
                    label = { Text("舒张压") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = pulse,
                onValueChange = onPulseChange,
                label = { Text("脉搏 (可选)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
