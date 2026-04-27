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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Text("请选择要查看或修改的功能项", style = MaterialTheme.typography.bodyMedium)

        SettingsMenuCard("用户资料", "姓名、年龄、性别、目标血压", Icons.Outlined.Person, onOpenProfile)
        SettingsMenuCard("提醒设置", "晨间/晚间提醒及时间", Icons.Outlined.Notifications, onOpenReminder)
        SettingsMenuCard("显示设置", "趋势图与高风险提醒显示", Icons.Outlined.DisplaySettings, onOpenDisplay)
        SettingsMenuCard("数据管理", "导入导出与清空数据", Icons.Outlined.Folder, onOpenDataManagement)
        SettingsMenuCard("说明", "更新说明与测量建议", Icons.Outlined.Info, onOpenInfo)
        SettingsMenuCard("免责声明", "中性说明，非诊断结论", Icons.Outlined.Policy, onOpenDisclaimer)
    }
}

@Composable
private fun SettingsMenuCard(
    title: String,
    desc: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun SettingsProfileScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsSubPageShell("用户资料", onBack) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::updateName,
            label = { Text("姓名") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.ageText,
            onValueChange = viewModel::updateAgeText,
            label = { Text("年龄") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.gender,
            onValueChange = viewModel::updateGender,
            label = { Text("性别") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.targetSystolicText,
            onValueChange = viewModel::updateTargetSystolicText,
            label = { Text("目标收缩压（可选）") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.targetDiastolicText,
            onValueChange = viewModel::updateTargetDiastolicText,
            label = { Text("目标舒张压（可选）") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = viewModel::saveUserProfile, modifier = Modifier.fillMaxWidth()) {
            Text("保存资料")
        }
        if (uiState.message.isNotBlank()) {
            Text(uiState.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SettingsReminderScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsSubPageShell("提醒设置", onBack) {
        SettingsSwitchRow(
            title = "晨间提醒",
            checked = uiState.morningReminderEnabled,
            onChecked = viewModel::setMorningReminderEnabled
        )
        OutlinedTextField(
            value = uiState.morningReminderTime,
            onValueChange = viewModel::updateMorningTime,
            label = { Text("晨间时间（HH:mm）") },
            modifier = Modifier.fillMaxWidth()
        )
        SettingsSwitchRow(
            title = "晚间提醒",
            checked = uiState.eveningReminderEnabled,
            onChecked = viewModel::setEveningReminderEnabled
        )
        OutlinedTextField(
            value = uiState.eveningReminderTime,
            onValueChange = viewModel::updateEveningTime,
            label = { Text("晚间时间（HH:mm）") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = viewModel::saveReminderTimes, modifier = Modifier.fillMaxWidth()) {
            Text("保存提醒设置")
        }
        if (uiState.message.isNotBlank()) {
            Text(uiState.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SettingsDisplayScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsSubPageShell("显示设置", onBack) {
        SettingsSwitchRow("显示趋势图", uiState.showTrendChart, viewModel::setShowTrendChart)
        SettingsSwitchRow("启用高风险提醒", uiState.highRiskAlertEnabled, viewModel::setHighRiskAlertEnabled)
        SettingsSwitchRow("大字号显示", uiState.isLargeTextEnabled, viewModel::setLargeTextEnabled)
    }
}

@Composable
fun SettingsDataManagementScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.importCsvFromUri(uri)
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.importXlsxFromUri(uri)
    }

    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAll,
            title = { Text("清空全部数据") },
            text = { Text("确定要清空全部血压记录吗？此操作不可撤销。") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearAll) { Text("清空") } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearAll) { Text("取消") } }
        )
    }

    SettingsSubPageShell("数据管理", onBack) {
        DataActionButton("导出 CSV", viewModel::exportCsv)
        DataActionButton("导出 XLSX", viewModel::exportXlsx)
        DataActionButton("导入 CSV") { csvLauncher.launch("text/*") }
        DataActionButton("导入 XLSX") {
            xlsxLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        }
        Button(
            onClick = viewModel::requestClearAll,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Text("清空全部数据")
        }
        if (uiState.message.isNotBlank()) {
            Text(uiState.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SettingsInfoScreen(
    onBack: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
    onOpenMeasurementTips: () -> Unit
) {
    SettingsSubPageShell("说明", onBack) {
        SettingsMenuCard(
            title = "更新说明",
            desc = "查看 1.1.3 版本变更内容",
            icon = Icons.Outlined.Info,
            onClick = onOpenReleaseNotes
        )
        SettingsMenuCard(
            title = "测量建议",
            desc = "查看血压测量建议与注意事项",
            icon = Icons.Outlined.Info,
            onClick = onOpenMeasurementTips
        )
    }
}

@Composable
fun SettingsInfoReleaseNotesScreen(onBack: () -> Unit) {
    SettingsSubPageShell("更新说明", onBack) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("版本 1.1.3", fontWeight = FontWeight.SemiBold)
                Text("1) 底部导航升级为四栏：测量/历史/趋势/设置。")
                Text("2) 趋势从历史二级页提升为一级页面，直接展示血压趋势。")
                Text("3) 设置二级/三级页统一左上角“←”返回键样式。")
                Text("4) 设置-说明新增“更新说明/测量建议”两项并拆分三级页。")
                Text("5) 历史记录内容上移，紧接统计概览显示。")
            }
        }
    }
}

@Composable
fun SettingsInfoMeasurementTipsScreen(onBack: () -> Unit) {
    SettingsSubPageShell("测量建议", onBack) {
        Text("1. 测量前静坐休息 5 分钟，避免刚运动后立即测量。")
        Text("2. 每次尽量保持同一时间段测量，便于趋势对比。")
        Text("3. 每次至少测量两组，间隔 1-2 分钟，取平均值更稳定。")
        Text("4. 若出现异常高值，请重复测量并根据自身情况及时就医。")
    }
}

@Composable
fun SettingsDisclaimerScreen(onBack: () -> Unit) {
    SettingsSubPageShell("免责声明", onBack) {
        Text("本应用仅用于家庭健康记录与趋势观察，不提供医疗诊断。")
        Text("应用内分级与提醒仅作参考，不应替代专业医生建议。")
        Text("如出现持续不适或异常高值，请及时咨询医生或前往医院。")
    }
}

@Composable
private fun SettingsSubPageShell(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.example.bloodpressurerecord.ui.common.AppBackButton(onClick = onBack)
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title)
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun DataActionButton(
    text: String,
    onClick: () -> Unit
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}
