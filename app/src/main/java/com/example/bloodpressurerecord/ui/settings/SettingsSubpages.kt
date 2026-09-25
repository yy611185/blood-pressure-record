package com.example.bloodpressurerecord.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.DataCard
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bloodpressurerecord.reminder.ReminderAuthorization
import com.example.bloodpressurerecord.reminder.ReminderAuthorizationStatus
import com.example.bloodpressurerecord.reminder.ReminderType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.WheelTimePickerDialog
import com.example.bloodpressurerecord.ui.common.pageContentBottomPadding
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import java.time.LocalTime

@Composable
fun SettingsProfileScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsSubPageShell("用户资料", onBack) {
        Text("个人信息与目标值", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        }
        }
        AppPrimaryButton(text = "保存资料", onClick = viewModel::saveUserProfile,
            modifier = Modifier.fillMaxWidth())
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
    var pendingMedicationDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }

    pendingMedicationDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { pendingMedicationDelete = null },
            title = { Text("删除药品？") },
            text = { Text("将删除「$name」及其全部服药时间。删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedication(id)
                        pendingMedicationDelete = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMedicationDelete = null }) { Text("取消") }
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        authorizationStatus = ReminderAuthorization.status(context)
        if (!granted || authorizationStatus != ReminderAuthorizationStatus.GRANTED) {
            viewModel.showReminderAuthorizationRequired(
                "提醒开关已启用，但通知权限未授予；应用内闹钟仍会继续安排，到点后不会显示系统通知。"
            )
        }
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            viewModel.setMedicationCalendarSyncEnabled(true)
        } else {
            viewModel.showReminderAuthorizationRequired(
                "日历权限未授予，暂时无法把服药提醒写入系统日历。"
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
        // “是否安排闹钟”和“是否允许显示通知”是两个独立状态。
        when (type) {
            ReminderType.MORNING -> viewModel.setMorningReminderEnabled(true)
            ReminderType.EVENING -> viewModel.setEveningReminderEnabled(true)
        }
        when (authorizationStatus) {
            ReminderAuthorizationStatus.GRANTED -> Unit
            ReminderAuthorizationStatus.RUNTIME_PERMISSION_REQUIRED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            ReminderAuthorizationStatus.SYSTEM_DISABLED -> {
                viewModel.showReminderAuthorizationRequired(
                    "提醒已启用，但系统通知已关闭；应用内闹钟仍会继续安排。请前往系统通知设置开启通知。"
                )
            }
        }
    }

    SettingsSubPageShell("提醒中心", onBack) {
        if (authorizationStatus != ReminderAuthorizationStatus.GRANTED) {
            DataCard {
                Text(
                    "通知权限未开启。提醒仍会按时触发，但不会显示系统通知。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
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
        Text("测量提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            onTimeSelected = viewModel::updateMorningTime
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
            onTimeSelected = viewModel::updateEveningTime
        )
        Text(
            "选择时间后会自动保存并更新提醒。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("服药提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "为每种降压药设置每天的服药时间，到点提醒，吃完在首页打个勾。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsSwitchRow(
            "启用服药提醒",
            uiState.medicationReminderEnabled
        ) { enabled ->
            if (enabled) {
                authorizationStatus = ReminderAuthorization.status(context)
                viewModel.setMedicationReminderEnabled(true)
                when (authorizationStatus) {
                    ReminderAuthorizationStatus.GRANTED -> Unit
                    ReminderAuthorizationStatus.RUNTIME_PERMISSION_REQUIRED -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    ReminderAuthorizationStatus.SYSTEM_DISABLED -> {
                        viewModel.showReminderAuthorizationRequired(
                            "服药提醒已启用，但系统通知已关闭；应用内闹钟仍会继续安排。请前往系统通知设置开启通知。"
                        )
                    }
                }
            } else {
                viewModel.setMedicationReminderEnabled(false)
            }
        }
        Text("系统日历", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingsSwitchRow(
            "同步到系统日历",
            uiState.medicationCalendarSyncEnabled
        ) { enabled ->
            if (enabled) {
                val hasCalendarPermission =
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_CALENDAR
                    ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.WRITE_CALENDAR
                        ) == PackageManager.PERMISSION_GRANTED
                if (hasCalendarPermission) {
                    viewModel.setMedicationCalendarSyncEnabled(true)
                } else {
                    calendarPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                        )
                    )
                }
            } else {
                viewModel.setMedicationCalendarSyncEnabled(false)
            }
        }
        Text(
            "开启后会在系统日历中创建每日重复的服药日程（含日历提醒），与应用内提醒互为双保险；关闭时自动清理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("药品与服药时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        var editorState by remember { mutableStateOf<MedicationEditorState?>(null) }
        uiState.medications.forEach { med ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${med.medication.name} ${med.medication.dosage}".trim(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (med.times.isEmpty()) {
                                "未设置服药时间"
                            } else {
                                "每天 " + med.times.map { it.timeText }.sorted().joinToString("、")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        editorState = MedicationEditorState(
                            id = med.medication.id,
                            name = med.medication.name,
                            dosage = med.medication.dosage,
                            times = med.times.map { it.timeText }.sorted()
                        )
                    }) { Text("编辑") }
                    TextButton(onClick = {
                        pendingMedicationDelete = med.medication.id to med.medication.name
                    }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                editorState = MedicationEditorState(
                    id = null, name = "", dosage = "", times = emptyList()
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("添加药品")
        }

        editorState?.let { editor ->
            MedicationEditorDialog(
                initial = editor,
                onDismiss = { editorState = null },
                onConfirm = { name, dosage, times ->
                    if (editor.id == null) {
                        viewModel.addMedication(name, dosage, times)
                    } else {
                        viewModel.updateMedication(editor.id, name, dosage, times)
                    }
                    editorState = null
                }
            )
        }

        SettingsMessage(uiState.message)
    }
}

private data class MedicationEditorState(
    val id: Long?,
    val name: String,
    val dosage: String,
    val times: List<String>
)

@Composable
private fun MedicationEditorDialog(
    initial: MedicationEditorState,
    onDismiss: () -> Unit,
    onConfirm: (name: String, dosage: String, times: List<String>) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var dosage by remember { mutableStateOf(initial.dosage) }
    var times by remember { mutableStateOf(initial.times) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        WheelTimePickerDialog(
            title = "选择服药时间",
            initialHour = 8,
            initialMinute = 0,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                val text = "%02d:%02d".format(hour, minute)
                times = (times + text).distinct().sorted()
                showTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == null) "添加药品" else "编辑药品") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("药品名称") },
                    placeholder = { Text("如：氨氯地平") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("每次数量") },
                    placeholder = { Text("如：1片") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("每日服药时间（可多个）", style = MaterialTheme.typography.bodySmall)
                times.forEach { time ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(time, modifier = Modifier.weight(1f))
                        TextButton(onClick = { times = times - time }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("添加时间")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, dosage, times) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
        WheelTimePickerDialog(
            title = label,
            initialHour = initial.hour,
            initialMinute = initial.minute,
            onDismiss = { showPicker = false },
            onConfirm = { hour, minute ->
                onTimeSelected("%02d:%02d".format(hour, minute))
                showPicker = false
            }
        )
    }
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp)
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
        SettingsSwitchRow(
            "平均值不计第一组读数",
            uiState.discardFirstReading,
            viewModel::setDiscardFirstReading
        )
        Text(
            "家庭自测第一次读数常偏高。开启后，新保存的记录计算平均值时会弃用第一组" +
                "（仅一组时仍按全部计算）；高风险提醒始终检查每一组原始读数。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                shape = RoundedCornerShape(24.dp),
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
private fun SettingsSubPageShell(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val topBarScroll = rememberHideOnScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarTopPadding())
            .nestedScroll(topBarScroll.nestedScrollConnection)
    ) {
        AppTopBar(title = title, onBack = onBack, hideOnScroll = topBarScroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = pageContentBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
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
