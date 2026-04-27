package com.example.bloodpressurerecord.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi

private val scenes = listOf("晨起", "睡前", "其他")
private val symptoms = listOf("无症状", "头痛", "头晕", "心悸", "胸闷/胸痛", "视物模糊", "其他")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val totalCount by viewModel.measurementCount.collectAsState()

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
            text = { Text("检测到读数超过 180/120。请注意休息，必要时及时就医。是否继续保存？") },
            confirmButton = { TextButton(onClick = viewModel::confirmHighRiskAndSave) { Text("确认保存") } },
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
        Text("新增测量", style = MaterialTheme.typography.headlineMedium)
        SummaryCard(uiState = uiState, totalCount = totalCount)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = uiState.measuredAtText,
                    onValueChange = viewModel::updateMeasuredAtText,
                    label = { Text("测量时间（yyyy-MM-dd HH:mm）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scenes.forEach { scene ->
                        FilterChip(selected = uiState.scene == scene, onClick = { viewModel.updateScene(scene) }, label = { Text(scene) })
                    }
                }

                ReadingCard("第1组", uiState.reading1, viewModel::updateReading1Systolic, viewModel::updateReading1Diastolic, viewModel::updateReading1Pulse)
                ReadingCard("第2组", uiState.reading2, viewModel::updateReading2Systolic, viewModel::updateReading2Diastolic, viewModel::updateReading2Pulse)

                if (uiState.showExtraReadings) {
                    uiState.extraReadings.forEachIndexed { index, input ->
                        val group = index + 3
                        ReadingCard(
                            "第${group}组",
                            input,
                            onSystolicChange = { viewModel.updateExtraReadingSystolic(index, it) },
                            onDiastolicChange = { viewModel.updateExtraReadingDiastolic(index, it) },
                            onPulseChange = { viewModel.updateExtraReadingPulse(index, it) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { viewModel.toggleThirdReading(false) }) { Text("收起第3组") }
                        TextButton(onClick = viewModel::addNextReadingGroup) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text("添加下一组", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                } else {
                    TextButton(onClick = { viewModel.toggleThirdReading(true) }) { Text("展开第3组") }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    symptoms.forEach { symptom ->
                        FilterChip(
                            selected = uiState.selectedSymptoms.contains(symptom),
                            onClick = { viewModel.toggleSymptom(symptom) },
                            label = { Text(symptom) }
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = viewModel::updateNote,
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                ResultCard(uiState = uiState)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::clearForm, modifier = Modifier.weight(1f)) { Text("清空") }
            Button(onClick = viewModel::onSaveClicked, modifier = Modifier.weight(2f)) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Text("保存测量", modifier = Modifier.padding(start = 4.dp))
            }
        }

        if (uiState.formMessage.isNotBlank()) {
            val ok = uiState.formMessage.contains("成功")
            Text(uiState.formMessage, color = if (ok) Color(0xFF1B5E20) else Color(0xFFB71C1C))
        }
    }
}

@Composable
private fun SummaryCard(uiState: HomeUiState, totalCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("测量概览（本次）", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("平均血压")
                Text(
                    "${uiState.avgSystolic ?: "--"}/${uiState.avgDiastolic ?: "--"} mmHg",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("平均脉搏")
                Text("${uiState.avgPulse ?: "--"} 次/分")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("本次读数分级（只读）")
                Text(uiState.categoryLabel, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("累计会话")
                Text("$totalCount 条")
            }
        }
    }
}

@Composable
private fun ResultCard(uiState: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("自动计算结果", style = MaterialTheme.typography.titleMedium)
            Text("平均收缩压：${uiState.avgSystolic ?: "--"}")
            Text("平均舒张压：${uiState.avgDiastolic ?: "--"}")
            Text("平均脉搏：${uiState.avgPulse ?: "--"}")
            Text("本次读数分级（只读）：${uiState.categoryLabel}")
        }
    }
}

@Composable
private fun ReadingCard(
    title: String,
    input: SessionReadingInputUi,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit,
    onPulseChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = input.systolic,
                    onValueChange = onSystolicChange,
                    label = { Text("收缩压") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = input.diastolic,
                    onValueChange = onDiastolicChange,
                    label = { Text("舒张压") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = input.pulse,
                onValueChange = onPulseChange,
                label = { Text("脉搏（可选）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
