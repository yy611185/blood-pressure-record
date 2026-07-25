package com.example.bloodpressurerecord.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bloodpressurerecord.reminder.ReminderAuthorization
import com.example.bloodpressurerecord.reminder.ReminderAuthorizationStatus
import com.example.bloodpressurerecord.reminder.ReminderType
import com.example.bloodpressurerecord.ui.common.AppTopBar
import java.time.LocalTime

@Composable
fun SettingsProfileScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsSubPageShell("用户资料", onBack) {
        OutlinedTextField(uiState.name, viewModel::updateName, label = { Text("姓名") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            uiState.ageText,
            viewModel::updateAgeText,
            label = { Text("年龄") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = uiState.ageError != null,
            supportingText = uiState.ageError?.let { error -> { Text(error) } }
        )
        OutlinedTextField(uiState.gender, viewModel::updateGender, label = { Text("性别") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            uiState.targetSystolicText,
            viewModel::updateTargetSystolicText,
            label = { Text("目标收缩压（可选）") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = uiState.targetSystolicError != null,
            supportingText = uiState.targetSystolicError?.let { error -> { Text(error) } }
        )
        OutlinedTextField(
            uiState.targetDiastolicText,
            viewModel::updateTargetDiastolicText,
            label = { Text("目标舒张压（可选）") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = uiState.targetDiastolicError != null,
            supportingText = uiState.targetDiastolicError?.let { error -> { Text(error) } }
        )
        Button(onClick = viewModel::saveUserProfile, modifier = Modifier.fillMaxWidth()) {
            Text("保存资料")
        }
        SettingsMessage(uiState.message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsReminderScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var authorizationStatus by remember {
        mutableStateOf(ReminderAuthorization.status(context))
    }
    var pendingEnable by remember { mutableStateOf<ReminderType?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        authorizationStatus = ReminderAuthorization.status(context)
        val type = pendingEnable
        pendingEnable = null
        if (granted && authorizationStatus == ReminderAuthorizationStatus.GRANTED) {
            when (type) {
                ReminderType.MORNING -> viewModel.setMorningReminderEnabled(true)
                ReminderType.EVENING -> viewModel.setEveningReminderEnabled(true)
                null -> Unit
            }
        } else {
            viewModel.showReminderAuthorizationRequired(
                "通知权限未授予，提醒尚未启用。可前往系统通知设置授权。"
            )
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                authorizationStatus = ReminderAuthorization.status(context)
                viewModel.refreshReminders()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestEnable(type: ReminderType) {
        authorizationStatus = ReminderAuthorization.status(context)
        when (authorizationStatus) {
            ReminderAuthorizationStatus.GRANTED -> when (type) {
                ReminderType.MORNING -> viewModel.setMorningReminderEnabled(true)
                ReminderType.EVENING -> viewModel.setEveningReminderEnabled(true)
            }
            ReminderAuthorizationStatus.RUNTIME_PERMISSION_REQUIRED -> {
                pendingEnable = type
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            ReminderAuthorizationStatus.SYSTEM_DISABLED -> {
                viewModel.showReminderAuthorizationRequired(
                    "系统通知已关闭，提醒尚未启用。请先前往系统通知设置开启。"
                )
            }
        }
    }

    SettingsSubPageShell("提醒设置", onBack) {
        Text(
            text = when (authorizationStatus) {
                ReminderAuthorizationStatus.GRANTED -> "通知授权状态：可用"
                ReminderAuthorizationStatus.RUNTIME_PERMISSION_REQUIRED -> "通知授权状态：未授予"
                ReminderAuthorizationStatus.SYSTEM_DISABLED -> "通知授权状态：系统已关闭"
            },
            color = if (authorizationStatus == ReminderAuthorizationStatus.GRANTED) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        if (authorizationStatus != ReminderAuthorizationStatus.GRANTED) {
            Button(
                onClick = {
                    context.startActivity(ReminderAuthorization.settingsIntent(context))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("前往系统通知设置")
            }
        }
        SettingsSwitchRow(
            "晨间提醒",
            uiState.morningReminderEnabled
        ) { enabled ->
            if (enabled) requestEnable(ReminderType.MORNING)
            else viewModel.setMorningReminderEnabled(false)
        }
        ReminderTimePickerButton(
            label = "晨间提醒时间",
            timeText = uiState.morningReminderTime,
            onTimeSelected = {
                viewModel.updateMorningTime(it)
                viewModel.saveReminderTimes()
            }
        )
        SettingsSwitchRow(
            "晚间提醒",
            uiState.eveningReminderEnabled
        ) { enabled ->
            if (enabled) requestEnable(ReminderType.EVENING)
            else viewModel.setEveningReminderEnabled(false)
        }
        ReminderTimePickerButton(
            label = "晚间提醒时间",
            timeText = uiState.eveningReminderTime,
            onTimeSelected = {
                viewModel.updateEveningTime(it)
                viewModel.saveReminderTimes()
            }
        )
        Button(onClick = viewModel::saveReminderTimes, modifier = Modifier.fillMaxWidth()) {
            Text("保存提醒设置")
        }
        SettingsMessage(uiState.message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerButton(
    label: String,
    timeText: String,
    onTimeSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val initial = runCatching { LocalTime.parse(timeText) }.getOrDefault(LocalTime.NOON)
    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(
                            "%02d:%02d".format(pickerState.hour, pickerState.minute)
                        )
                        showPicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            }
        )
    }
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("$label：$timeText")
    }
}

@Composable
fun SettingsDisplayScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
