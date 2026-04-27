package com.example.bloodpressurerecord.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.LocalAppFontScale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val fontScale = LocalAppFontScale.current

    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAll,
            title = { Text("确认清空全部数据") },
            text = { Text("此操作会删除全部测量记录与用户资料，且无法恢复。") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearAll) { Text("确认清空") } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearAll) { Text("取消") } }
        )
    }
    if (uiState.showInfoDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showInfoDialog(false) },
            title = { Text("说明") },
            text = { Text("本应用用于家庭血压记录管理，帮助查看近期变化趋势。所有数据默认保存在本地。") },
            confirmButton = { TextButton(onClick = { viewModel.showInfoDialog(false) }) { Text("我知道了") } }
        )
    }
    if (uiState.showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDisclaimerDialog(false) },
            title = { Text("免责声明") },
            text = { Text("本应用结果仅作健康管理参考，不构成医学诊断或治疗建议。若出现不适，请及时咨询专业医生。") },
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
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = MaterialTheme.typography.headlineMedium.fontSize * fontScale
            )
        )

        SectionCard(title = "用户资料") {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("姓名（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.ageText,
                onValueChange = viewModel::updateAgeText,
                label = { Text("年龄（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = uiState.gender,
                onValueChange = viewModel::updateGender,
                label = { Text("性别（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
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
            Button(onClick = viewModel::saveUserProfile, modifier = Modifier.fillMaxWidth()) {
                Text("保存用户资料")
            }
        }

        SectionCard(title = "提醒设置") {
            SettingItem(
                title = "晨间提醒",
                description = "打开后保留晨间提醒配置。",
                checked = uiState.morningReminderEnabled,
                onCheckedChange = viewModel::setMorningReminderEnabled
            )
            OutlinedTextField(
                value = uiState.morningReminderTime,
                onValueChange = viewModel::updateMorningTime,
                label = { Text("晨间提醒时间（HH:mm）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            SettingItem(
                title = "晚间提醒",
                description = "打开后保留晚间提醒配置。",
                checked = uiState.eveningReminderEnabled,
                onCheckedChange = viewModel::setEveningReminderEnabled
            )
            OutlinedTextField(
                value = uiState.eveningReminderTime,
                onValueChange = viewModel::updateEveningTime,
                label = { Text("晚间提醒时间（HH:mm）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = viewModel::saveReminderTimes, modifier = Modifier.fillMaxWidth()) {
                Text("保存提醒设置")
            }
        }

        SectionCard(title = "显示设置") {
            SettingItem(
                title = "大字模式",
                description = "为中老年家庭成员提供更清晰的阅读体验。",
                checked = uiState.isLargeTextEnabled,
                onCheckedChange = viewModel::setLargeTextEnabled
            )
            SettingItem(
                title = "显示趋势图",
                description = "在历史页显示 7 天和 30 天趋势图。",
                checked = uiState.showTrendChart,
                onCheckedChange = viewModel::setShowTrendChart
            )
            SettingItem(
                title = "启用高风险提醒",
                description = "检测到高风险值时进行高优先级提示。",
                checked = uiState.highRiskAlertEnabled,
                onCheckedChange = viewModel::setHighRiskAlertEnabled
            )
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
            Text(
                text = uiState.message,
                color = if (uiState.message.contains("已") || uiState.message.contains("成功")) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
