package com.example.bloodpressurerecord.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importCsvFromUri(uri, fileNameHint = "import.csv")
    }
    val importXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importXlsxFromUri(uri, fileNameHint = "import.xlsx")
    }

    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAll,
            title = { Text("清空全部数据") },
            text = { Text("此操作不可撤销，是否继续？") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearAll) { Text("确认清空") } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearAll) { Text("取消") } }
        )
    }

    if (uiState.showInfoDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showInfoDialog(false) },
            title = { Text("更新说明") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("版本 1.0.3（当前版）", style = MaterialTheme.typography.titleSmall)
                    Text("• 开启代码压缩与资源精简，显著降低应用占用存储空间")
                    Text("• 将【收缩压】【舒张压】全局加入（高压）/（低压）说明，更直观")
                    Text("• 应用图标全新升级")
                    Text("• 将【趋势图】重构为独立页面的7天/30天双折线时间序列图，展示每次实际测量数据，并含120/130/140和80/90医学参考线")
                    Text("• 将【查看说明】按钮更名为【查看更新说明】")
                    HorizontalDivider()
                    Text("版本 1.0.1 – 1.0.2", style = MaterialTheme.typography.titleSmall)
                    Text("• 初始发布，包含血压记录、历史查询、导入导出、设置等核心功能")
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.showInfoDialog(false) }) { Text("知道了") } }
        )
    }

    if (uiState.showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDisclaimerDialog(false) },
            title = { Text("免责声明") },
            text = { Text("本应用仅用于记录与整理健康数据，不提供医学诊断或治疗结论。") },
            confirmButton = { TextButton(onClick = { viewModel.showDisclaimerDialog(false) }) { Text("关闭") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        SectionCard("用户资料") {
            OutlinedTextField(value = uiState.name, onValueChange = viewModel::updateName, label = { Text("姓名（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = uiState.ageText, onValueChange = viewModel::updateAgeText, label = { Text("年龄（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = uiState.gender, onValueChange = viewModel::updateGender, label = { Text("性别（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = uiState.targetSystolicText, onValueChange = viewModel::updateTargetSystolicText, label = { Text("目标收缩压（高压）（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = uiState.targetDiastolicText, onValueChange = viewModel::updateTargetDiastolicText, label = { Text("目标舒张压（低压）（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(onClick = viewModel::saveUserProfile, modifier = Modifier.fillMaxWidth()) { Text("保存资料") }
        }

        SectionCard("提醒设置") {
            SettingSwitch("晨间提醒", uiState.morningReminderEnabled, viewModel::setMorningReminderEnabled)
            OutlinedTextField(value = uiState.morningReminderTime, onValueChange = viewModel::updateMorningTime, label = { Text("晨间时间 HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            SettingSwitch("晚间提醒", uiState.eveningReminderEnabled, viewModel::setEveningReminderEnabled)
            OutlinedTextField(value = uiState.eveningReminderTime, onValueChange = viewModel::updateEveningTime, label = { Text("晚间时间 HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = viewModel::saveReminderTimes, modifier = Modifier.fillMaxWidth()) { Text("保存提醒") }
        }

        SectionCard("显示设置") {
            SettingSwitch("大字模式", uiState.isLargeTextEnabled, viewModel::setLargeTextEnabled)
            SettingSwitch("显示趋势图", uiState.showTrendChart, viewModel::setShowTrendChart)
            SettingSwitch("启用高风险提醒", uiState.highRiskAlertEnabled, viewModel::setHighRiskAlertEnabled)
        }

        SectionCard("数据管理") {
            Button(onClick = viewModel::exportCsv, modifier = Modifier.fillMaxWidth()) { Text("导出 CSV") }
            Button(onClick = viewModel::exportXlsx, modifier = Modifier.fillMaxWidth()) { Text("导出 XLSX") }
            Button(
                onClick = {
                    importCsvLauncher.launch(
                        arrayOf("text/csv", "application/csv", "text/*", "*/*")
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导入 CSV") }
            Button(
                onClick = {
                    importXlsxLauncher.launch(
                        arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导入 XLSX") }
            Button(onClick = viewModel::requestClearAll, modifier = Modifier.fillMaxWidth()) { Text("清空全部数据") }
        }

        SectionCard("说明与免责声明") {
            Button(onClick = { viewModel.showInfoDialog(true) }, modifier = Modifier.fillMaxWidth()) { Text("查看更新说明") }
            Button(onClick = { viewModel.showDisclaimerDialog(true) }, modifier = Modifier.fillMaxWidth()) { Text("查看免责声明") }
        }

        if (uiState.message.isNotBlank()) {
            val success = uiState.message.contains("成功") || uiState.message.contains("已")
            Text(uiState.message, color = if (success) Color(0xFF1B5E20) else Color(0xFFB71C1C))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.padding(top = 10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
