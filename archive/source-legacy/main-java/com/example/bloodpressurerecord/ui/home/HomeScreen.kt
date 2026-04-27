package com.example.bloodpressurerecord.ui.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.ui.LocalAppFontScale

private val scenes = listOf("晨起", "睡前", "其他")
private val symptoms = listOf("无症状", "头痛", "头晕", "心悸", "胸闷/胸痛", "视物模糊", "其他")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val totalCount by viewModel.measurementCount.collectAsState()
    val fontScale = LocalAppFontScale.current

    if (uiState.showAbnormalConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAbnormalDialog,
            title = { Text("数值异常提醒") },
            text = { Text(uiState.abnormalConfirmMessage) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAbnormalAndContinue) {
                    Text("继续保存")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAbnormalDialog) {
                    Text("返回修改")
                }
            }
        )
    }

    if (uiState.showHighRiskDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHighRiskDialog,
            title = { Text("高优先级提醒") },
            text = { Text("检测到超过 180/120 的高值，请优先关注家人状态。是否仍继续保存本次记录？") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmHighRiskAndSave) {
                    Text("确认继续")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissHighRiskDialog) {
                    Text("返回修改")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "新增测量",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = MaterialTheme.typography.headlineMedium.fontSize * fontScale
            )
        )
        Text(
            text = "至少填写前两组读数后才能保存。分级由系统自动计算，仅供记录参考。",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale
            )
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.measuredAtText,
                    onValueChange = viewModel::updateMeasuredAtText,
                    label = { Text("测量时间（yyyy-MM-dd HH:mm）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("测量场景", style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scenes.forEach { scene ->
                        FilterChip(
                            selected = uiState.scene == scene,
                            onClick = { viewModel.updateScene(scene) },
                            label = { Text(scene) }
                        )
                    }
                }

                ReadingBlock(
                    title = "第1组读数",
                    input = uiState.reading1,
                    onSystolicChange = viewModel::updateReading1Systolic,
                    onDiastolicChange = viewModel::updateReading1Diastolic,
                    onPulseChange = viewModel::updateReading1Pulse
                )
                ReadingBlock(
                    title = "第2组读数",
                    input = uiState.reading2,
                    onSystolicChange = viewModel::updateReading2Systolic,
                    onDiastolicChange = viewModel::updateReading2Diastolic,
                    onPulseChange = viewModel::updateReading2Pulse
                )

                if (uiState.showThirdReading) {
                    ReadingBlock(
                        title = "第3组读数（可选）",
                        input = uiState.reading3,
                        onSystolicChange = viewModel::updateReading3Systolic,
                        onDiastolicChange = viewModel::updateReading3Diastolic,
                        onPulseChange = viewModel::updateReading3Pulse
                    )
                    TextButton(onClick = { viewModel.toggleThirdReading(false) }) {
                        Text("收起第3组")
                    }
                } else {
                    TextButton(onClick = { viewModel.toggleThirdReading(true) }) {
                        Text("展开第3组（可选）")
                    }
                }

                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = viewModel::updateNote,
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Text("症状标签（可多选）", style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    symptoms.forEach { symptom ->
                        FilterChip(
                            selected = uiState.selectedSymptoms.contains(symptom),
                            onClick = { viewModel.toggleSymptom(symptom) },
                            label = { Text(symptom) }
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("自动计算结果", style = MaterialTheme.typography.titleLarge)
                        Text("平均收缩压：${uiState.avgSystolic?.toString() ?: "--"}")
                        Text("平均舒张压：${uiState.avgDiastolic?.toString() ?: "--"}")
                        Text("平均脉搏：${uiState.avgPulse?.toString() ?: "--"}")
                        Text("本次读数分级：${uiState.categoryLabel}")
                    }
                }

                Button(onClick = viewModel::onSaveClicked, modifier = Modifier.fillMaxWidth()) {
                    Text("保存")
                }
                Button(onClick = viewModel::clearForm, modifier = Modifier.fillMaxWidth()) {
                    Text("清空")
                }

                if (uiState.formMessage.isNotBlank()) {
                    Text(
                        text = uiState.formMessage,
                        color = if (uiState.formMessage.contains("成功")) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    )
                }
            }
        }

        Text(
            text = "当前已保存会话数：$totalCount",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ReadingBlock(
    title: String,
    input: SessionReadingInputUi,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit,
    onPulseChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = input.systolic,
            onValueChange = onSystolicChange,
            label = { Text("收缩压") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = input.diastolic,
            onValueChange = onDiastolicChange,
            label = { Text("舒张压") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = input.pulse,
            onValueChange = onPulseChange,
            label = { Text("脉搏（可选）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
