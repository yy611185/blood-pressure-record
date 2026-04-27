package com.example.bloodpressurerecord.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onOpenProfile: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenDisclaimer: () -> Unit
) {
    val menus = listOf(
        Triple("用户资料", "姓名、年龄、性别、目标血压", onOpenProfile),
        Triple("提醒设置", "晨间提醒、晚间提醒与时间", onOpenReminder),
        Triple("显示设置", "趋势图显示和高风险提醒开关", onOpenDisplay),
        Triple("数据管理", "导入/导出与清空全部数据", onOpenDataManagement),
        Triple("说明", "应用使用说明", onOpenInfo),
        Triple("免责声明", "医疗相关中性声明", onOpenDisclaimer)
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        menus.forEach { (title, desc, onClick) ->
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = title, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsProfileScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    SettingsSubPageScaffold(title = "用户资料", onBack = onBack) {
        OutlinedTextField(value = state.name, onValueChange = viewModel::updateName, label = { Text("姓名（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = state.ageText, onValueChange = viewModel::updateAgeText, label = { Text("年龄（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = state.gender, onValueChange = viewModel::updateGender, label = { Text("性别（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = state.targetSystolicText, onValueChange = viewModel::updateTargetSystolicText, label = { Text("目标收缩压（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = state.targetDiastolicText, onValueChange = viewModel::updateTargetDiastolicText, label = { Text("目标舒张压（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Button(onClick = viewModel::saveUserProfile, modifier = Modifier.fillMaxWidth()) { Text("保存资料") }
        MessageText(state.message)
    }
}

@Composable
fun SettingsReminderScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    SettingsSubPageScaffold(title = "提醒设置", onBack = onBack) {
        SettingSwitch("晨间提醒", state.morningReminderEnabled, viewModel::setMorningReminderEnabled)
        OutlinedTextField(value = state.morningReminderTime, onValueChange = viewModel::updateMorningTime, label = { Text("晨间时间 HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        SettingSwitch("晚间提醒", state.eveningReminderEnabled, viewModel::setEveningReminderEnabled)
        OutlinedTextField(value = state.eveningReminderTime, onValueChange = viewModel::updateEveningTime, label = { Text("晚间时间 HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = viewModel::saveReminderTimes, modifier = Modifier.fillMaxWidth()) { Text("保存提醒") }
        MessageText(state.message)
    }
}

@Composable
fun SettingsDisplayScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    SettingsSubPageScaffold(title = "显示设置", onBack = onBack) {
        SettingSwitch("大字模式", state.isLargeTextEnabled, viewModel::setLargeTextEnabled)
        SettingSwitch("显示趋势图", state.showTrendChart, viewModel::setShowTrendChart)
        SettingSwitch("启用高风险提醒", state.highRiskAlertEnabled, viewModel::setHighRiskAlertEnabled)
        MessageText(state.message)
    }
}

@Composable
fun SettingsDataManagementScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
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

    if (state.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAll,
            title = { Text("清空全部数据") },
            text = { Text("此操作不可撤销，是否继续？") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearAll) { Text("确认清空") } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearAll) { Text("取消") } }
        )
    }

    SettingsSubPageScaffold(title = "数据管理", onBack = onBack) {
        Button(onClick = viewModel::exportCsv, modifier = Modifier.fillMaxWidth()) { Text("导出 CSV") }
        Button(onClick = viewModel::exportXlsx, modifier = Modifier.fillMaxWidth()) { Text("导出 XLSX") }
        Button(onClick = { importCsvLauncher.launch(arrayOf("text/csv", "application/csv", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("导入 CSV") }
        Button(onClick = { importXlsxLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("导入 XLSX") }
        Button(onClick = viewModel::requestClearAll, modifier = Modifier.fillMaxWidth()) { Text("清空全部数据") }
        MessageText(state.message)
    }
}

@Composable
fun SettingsInfoScreen(onBack: () -> Unit) {
    SettingsSubPageScaffold(title = "说明", onBack = onBack) {
        Text("版本 1.1.1 更新说明", style = MaterialTheme.typography.titleMedium)
        Text("1. 全面升级首页、历史、趋势、设置页面视觉样式，采用统一卡片化设计。")
        Text("2. 修复页面中文乱码问题，所有核心界面文案恢复完整中文显示。")
        Text("3. 保持“前2组固定 + 第3组起动态扩展”录入体验，保存/编辑/计算逻辑不变。")
        Text("4. 优化二级页面导航体验：子页面聚焦展示，主页面保留3栏底部导航。")
        Text("5. 导入导出与数据管理能力保持兼容（CSV / XLSX）。")
    }
}

@Composable
fun SettingsDisclaimerScreen(onBack: () -> Unit) {
    SettingsSubPageScaffold(title = "免责声明", onBack = onBack) {
        Text("本应用仅用于健康数据记录与管理，不提供医学诊断或治疗结论。")
        Text("如出现持续不适或异常高值，请及时联系专业医疗机构。")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MessageText(message: String) {
    if (message.isBlank()) return
    val success = message.contains("成功") || message.contains("已")
    Text(message, color = if (success) Color(0xFF1B5E20) else Color(0xFFB71C1C))
}
