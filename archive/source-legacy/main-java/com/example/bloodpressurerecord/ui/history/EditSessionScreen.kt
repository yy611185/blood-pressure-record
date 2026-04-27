package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi

private val editScenes = listOf("晨起", "睡前", "其他")
private val editSymptoms = listOf("无症状", "头痛", "头晕", "心悸", "胸闷/胸痛", "视物模糊", "其他")

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
            title = { Text("数值异常提醒") },
            text = { Text(uiState.abnormalConfirmMessage) },
            confirmButton = { TextButton(onClick = viewModel::confirmAbnormalAndContinue) { Text("继续保存") } },
            dismissButton = { TextButton(onClick = viewModel::dismissAbnormalDialog) { Text("返回修改") } }
        )
    }
    if (uiState.showHighRiskDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHighRiskDialog,
            title = { Text("高优先级提醒") },
            text = { Text("检测到超过 180/120 的高值，是否仍继续保存编辑结果？") },
            confirmButton = { TextButton(onClick = viewModel::confirmHighRiskAndSave) { Text("确认继续") } },
            dismissButton = { TextButton(onClick = viewModel::dismissHighRiskDialog) { Text("返回修改") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("编辑会话", style = MaterialTheme.typography.headlineMedium)
        if (uiState.loading) {
            Text("加载中...")
            return@Column
        }
        OutlinedTextField(
            value = uiState.measuredAtText,
            onValueChange = viewModel::updateMeasuredAtText,
            label = { Text("测量时间（yyyy-MM-dd HH:mm）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editScenes.forEach { scene ->
                FilterChip(
                    selected = uiState.scene == scene,
                    onClick = { viewModel.updateScene(scene) },
                    label = { Text(scene) }
                )
            }
        }

        EditReadingBlock(
            title = "第1组读数",
            input = uiState.reading1,
            onSystolic = viewModel::updateReading1Systolic,
            onDiastolic = viewModel::updateReading1Diastolic,
            onPulse = viewModel::updateReading1Pulse
        )
        EditReadingBlock(
            title = "第2组读数",
            input = uiState.reading2,
            onSystolic = viewModel::updateReading2Systolic,
            onDiastolic = viewModel::updateReading2Diastolic,
            onPulse = viewModel::updateReading2Pulse
        )
        if (uiState.showThirdReading) {
            EditReadingBlock(
                title = "第3组读数（可选）",
                input = uiState.reading3,
                onSystolic = viewModel::updateReading3Systolic,
                onDiastolic = viewModel::updateReading3Diastolic,
                onPulse = viewModel::updateReading3Pulse
            )
            TextButton(onClick = { viewModel.toggleThirdReading(false) }) { Text("收起第3组") }
        } else {
            TextButton(onClick = { viewModel.toggleThirdReading(true) }) { Text("展开第3组（可选）") }
        }

        OutlinedTextField(
            value = uiState.note,
            onValueChange = viewModel::updateNote,
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Text("症状标签（可多选）", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editSymptoms.take(4).forEach { symptom ->
                FilterChip(
                    selected = uiState.selectedSymptoms.contains(symptom),
                    onClick = { viewModel.toggleSymptom(symptom) },
                    label = { Text(symptom) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editSymptoms.drop(4).forEach { symptom ->
                FilterChip(
                    selected = uiState.selectedSymptoms.contains(symptom),
                    onClick = { viewModel.toggleSymptom(symptom) },
                    label = { Text(symptom) }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("自动计算结果", style = MaterialTheme.typography.titleLarge)
                Text("平均收缩压：${uiState.avgSystolic ?: "--"}")
                Text("平均舒张压：${uiState.avgDiastolic ?: "--"}")
                Text("平均脉搏：${uiState.avgPulse ?: "--"}")
                Text("本次读数分级：${uiState.categoryLabel}")
            }
        }
        Button(onClick = viewModel::onSaveClicked, modifier = Modifier.fillMaxWidth()) {
            Text("保存编辑")
        }
        if (uiState.message.isNotBlank()) {
            Text(
                text = uiState.message,
                color = if (uiState.message.contains("成功")) Color(0xFF1B5E20) else Color(0xFFB71C1C)
            )
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
        Text(title, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = input.systolic,
            onValueChange = onSystolic,
            label = { Text("收缩压") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = input.diastolic,
            onValueChange = onDiastolic,
            label = { Text("舒张压") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = input.pulse,
            onValueChange = onPulse,
            label = { Text("脉搏（可选）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
