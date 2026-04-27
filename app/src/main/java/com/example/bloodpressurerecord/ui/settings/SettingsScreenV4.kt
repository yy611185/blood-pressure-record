package com.example.bloodpressurerecord.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.AppTopBar

@Composable
fun SettingsProfileScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsSubPageShell("用户资料", onBack) {
        OutlinedTextField(uiState.name, viewModel::updateName, label = { Text("姓名") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(uiState.ageText, viewModel::updateAgeText, label = { Text("年龄") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(uiState.gender, viewModel::updateGender, label = { Text("性别") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            uiState.targetSystolicText,
            viewModel::updateTargetSystolicText,
            label = { Text("目标收缩压（可选）") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            uiState.targetDiastolicText,
            viewModel::updateTargetDiastolicText,
            label = { Text("目标舒张压（可选）") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = viewModel::saveUserProfile, modifier = Modifier.fillMaxWidth()) {
            Text("保存资料")
        }
        SettingsMessage(uiState.message)
    }
}

@Composable
fun SettingsReminderScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsSubPageShell("提醒设置", onBack) {
        SettingsSwitchRow("晨间提醒", uiState.morningReminderEnabled, viewModel::setMorningReminderEnabled)
        OutlinedTextField(
            uiState.morningReminderTime,
            viewModel::updateMorningTime,
            label = { Text("晨间时间（HH:mm）") },
            modifier = Modifier.fillMaxWidth()
        )
        SettingsSwitchRow("晚间提醒", uiState.eveningReminderEnabled, viewModel::setEveningReminderEnabled)
        OutlinedTextField(
            uiState.eveningReminderTime,
            viewModel::updateEveningTime,
            label = { Text("晚间时间（HH:mm）") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = viewModel::saveReminderTimes, modifier = Modifier.fillMaxWidth()) {
            Text("保存提醒设置")
        }
        SettingsMessage(uiState.message)
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
fun SettingsInfoScreen(
    onBack: () -> Unit,
    onOpenAppGuide: () -> Unit,
    onOpenReleaseNotes: () -> Unit
) {
    SettingsSubPageShell("应用说明与更新说明", onBack) {
        SettingListItem(
            title = "应用说明",
            subtitle = "查看当前可实现功能、测量建议和免责声明",
            icon = Icons.Outlined.Info,
            onClick = onOpenAppGuide
        )
        SettingListItem(
            title = "更新说明",
            subtitle = "查看 ${AppInfoContent.CURRENT_VERSION} 及后续版本更新记录",
            icon = Icons.Outlined.Update,
            onClick = onOpenReleaseNotes
        )
    }
}

@Composable
fun SettingsAppGuideScreen(onBack: () -> Unit) {
    SettingsSubPageShell("应用说明", onBack) {
        AppInfoContent.featureSections.forEach { section ->
            InfoSectionCard(section)
        }
    }
}

@Composable
fun SettingsInfoReleaseNotesScreen(onBack: () -> Unit) {
    SettingsSubPageShell("更新说明", onBack) {
        AppReleaseNotes.notes.forEach { note ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("版本 ${note.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(note.summary, style = MaterialTheme.typography.bodyMedium)
                    note.changes.forEach { change ->
                        Text("• $change", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsInfoMeasurementTipsScreen(onBack: () -> Unit) {
    SettingsAppGuideScreen(onBack)
}

@Composable
fun SettingsDisclaimerScreen(onBack: () -> Unit) {
    SettingsAppGuideScreen(onBack)
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
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
private fun InfoSectionCard(section: InfoSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            section.items.forEach { item ->
                Text("• $item", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsMessage(message: String) {
    if (message.isNotBlank()) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}
