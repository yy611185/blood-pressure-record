package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi

private val editScenes = listOf("晨起", "睡前", "其他")
private val editSymptoms = listOf("无症状", "头痛", "头晕", "心悸", "胸闷/胸痛", "视物模糊", "其他")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSessionScreen(
    viewModel: EditSessionViewModel,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

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
            text = { Text("读数超过 180/120，是否继续保存本次编辑？") },
            confirmButton = { TextButton(onClick = viewModel::confirmHighRiskAndSave) { Text("确认保存") } },
            dismissButton = { TextButton(onClick = viewModel::dismissHighRiskDialog) { Text("返回修改") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("编辑会话", style = MaterialTheme.typography.headlineMedium)
        if (uiState.loading) {
            Text("正在加载...")
            return@Column
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = uiState.measuredAtText,
                    onValueChange = viewModel::updateMeasuredAtText,
                    label = { Text("测量时间（yyyy-MM-dd HH:mm）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    editScenes.forEach { scene ->
                        FilterChip(selected = uiState.scene == scene, onClick = { viewModel.updateScene(scene) }, label = { Text(scene) })
                    }
                }
                EditReadingBlock("第1组", uiState.reading1, viewModel::updateReading1Systolic, viewModel::updateReading1Diastolic, viewModel::updateReading1Pulse)
                EditReadingBlock("第2组", uiState.reading2, viewModel::updateReading2Systolic, viewModel::updateReading2Diastolic, viewModel::updateReading2Pulse)
                if (uiState.showExtraReadings) {
                    uiState.extraReadings.forEachIndexed { index, input ->
                        val group = index + 3
                        EditReadingBlock(
                            "第${group}组",
                            input,
                            onSystolic = { viewModel.updateExtraReadingSystolic(index, it) },
                            onDiastolic = { viewModel.updateExtraReadingDiastolic(index, it) },
                            onPulse = { viewModel.updateExtraReadingPulse(index, it) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.toggleThirdReading(false) }) { Text("收起第3组") }
                        TextButton(onClick = viewModel::addNextReadingGroup) { Text("添加下一组") }
                    }
                } else {
                    TextButton(onClick = { viewModel.toggleThirdReading(true) }) { Text("展开第3组") }
                }
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = viewModel::updateNote,
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    editSymptoms.forEach { symptom ->
                        FilterChip(
                            selected = uiState.selectedSymptoms.contains(symptom),
                            onClick = { viewModel.toggleSymptom(symptom) },
                            label = { Text(symptom) }
                        )
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("自动计算结果", style = MaterialTheme.typography.titleMedium)
                        Text("平均收缩压：${uiState.avgSystolic ?: "--"}")
                        Text("平均舒张压：${uiState.avgDiastolic ?: "--"}")
                        Text("平均脉搏：${uiState.avgPulse ?: "--"}")
                        Text("本次读数分级（只读）：${uiState.categoryLabel}")
                    }
                }
                Button(onClick = viewModel::onSaveClicked, modifier = Modifier.fillMaxWidth()) { Text("保存修改") }
                if (uiState.message.isNotBlank()) {
                    val ok = uiState.message.contains("成功") || uiState.message.contains("已保存")
                    Text(uiState.message, color = if (ok) Color(0xFF1B5E20) else Color(0xFFB71C1C))
                }
            }
        }
    }
}

@Composable
private fun EditReadingBlock(
    title: String,
    input: SessionReadingInputUi,
    onSystolic: (String) -> Unit,
    onDiastolic: (String) -> Unit,
    onPulse: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = input.systolic, onValueChange = onSystolic, label = { Text("收缩压（高压）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = input.diastolic, onValueChange = onDiastolic, label = { Text("舒张压（低压）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = input.pulse, onValueChange = onPulse, label = { Text("脉搏（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

