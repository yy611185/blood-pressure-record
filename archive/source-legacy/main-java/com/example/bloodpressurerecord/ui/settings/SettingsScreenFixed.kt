package com.example.bloodpressurerecord.ui.settings

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

    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAll,
            title = { Text("确认清空全部数据") },
            text = { Text("该操作会删除全部测量记录和用户资料，且无法恢复。") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearAll) { Text("确认清空") } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearAll) { Text("取消") } }
        )
    }
    if (uiState.showInfoDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showInfoDialog(false) },
            title = { Text("说明") },
            text = { Text("本应用用于家庭血压记录管理，数据默认保存在本地。") },
            confirmButton = { TextButton(onClick = { viewModel.showInfoDialog(false) }) { Text("我知道了") } }
        )
    }
    if (uiState.showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDisclaimerDialog(false) },
            title = { Text("免责声明") },
            text = { Text("本应用结果仅供健康管理参考，不构成医疗诊断。若有不适，请及时就医。") },
            confirmButton = { TextButton(onClick = { viewModel.showDisclaimerDialog(false) }) { Text("关闭") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        SectionCard(title = "用户资料") {
            OutlinedTextField(value = uiState.name, onValueChange = viewModel::updateName, label = { Text("姓名（可选）" }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                value = uiState.ageText,
                onValueChange = viewModel::updateAgeText,
                label = { Text("年龄（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(value = uiState.gender, onValueChange = viewModel::updateGender, label = { Text("性别（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                value = uiState.targetSystolicText,
                onValueChange = viewModel::updateTargetSystolicText,
                label = { Text("目标收缩压（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = uiState.targetDiastolicText,
                onValueChange = viewModel::updateTargetDiastolicText,
                label = { Text("目标舒张压（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(onClick = viewModel::saveUserProfile, modifier = Modifier.fillMaxWidth()) { Text("保存用户资料") }
        }

        SectionCard(title = "提醒设置") {
            SettingItem("晨间提醒", uiState.morningReminderEnabled, viewModel::setMorningReminderEnabled)
            OutlinedTextField(value = uiState.morningReminderTime, onValueChange = viewModel::updateMorningTime, label = { Text("晨间提醒时间（HH:mm）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            SettingItem("晚间提醒", uiState.eveningReminderEnabled, viewModel::setEveningReminderEnabled)
            OutlinedTextField(value = uiState.eveningReminderTime, onValueChange = viewModel::updateEveningTime, label = { Text("晚间提醒时间（HH:mm）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = viewModel::saveReminderTimes, modifier = Modifier.fillMaxWidth()) { Text("保存提醒设置") }
        }

        SectionCard(title = "显示设置") {
            SettingItem("大字模式", uiState.isLargeTextEnabled, viewModel::setLargeTextEnabled)
            SettingItem("显示趋势图", uiState.showTrendChart, viewModel::setShowTrendChart)
            SettingItem("启用高风险提醒", uiState.highRiskAlertEnabled, viewModel::setHighRiskAlertEnabled)
        }

        SectionCard(title = "数据管理") {
            Button(onClick = viewModel::exportCsv, modifier = Modifier.fillMaxWidth()) { Text("导出 CSV") }
            Button(onClick = viewModel::exportXlsx, modifier = Modifier.fillMaxWidth()) { Text("导出 XLSX") }
            Button(onClick = viewModel::importCsv, modifier = Modifier.fillMaxWidth()) { Text("导入 CSV") }
            Button(onClick = viewModel::importXlsx, modifier = Modifier.fillMaxWidth()) { Text("导入 XLSX") }
            Button(onClick = viewModel::requestClearAll, modifier = Modifier.fillMaxWidth()) { Text("清空全部数据") }
        }

        SectionCard(title = "说明与免责声明") {
            Button(onClick = { viewModel.showInfoDialog(true) }, modifier = Modifier.fillMaxWidth()) { Text("查看说明") }
            Button(onClick = { viewModel.showDisclaimerDialog(true) }, modifier = Modifier.fillMaxWidth()) { Text("查看免责声明") }
        }

        if (uiState.message.isNotBlank()) {
            val success = uiState.message.contains("成功") || uiState.message.contains("完成")
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
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
