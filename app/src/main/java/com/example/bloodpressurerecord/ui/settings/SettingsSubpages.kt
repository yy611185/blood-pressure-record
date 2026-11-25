package com.example.bloodpressurerecord.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.BuildConfig
import com.example.bloodpressurerecord.data.calendar.MedicationCalendarTarget
import com.example.bloodpressurerecord.reminder.ReminderAuthorization
import com.example.bloodpressurerecord.reminder.ReminderAuthorizationStatus
import com.example.bloodpressurerecord.reminder.ReminderType
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.SegmentedPillGroup
import com.example.bloodpressurerecord.ui.common.WheelTimePickerDialog
import com.example.bloodpressurerecord.ui.common.pageContentBottomPadding
import com.example.bloodpressurerecord.ui.common.pressableScale
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.tabularNums
import com.example.bloodpressurerecord.update.UpdateSettingsContent
import com.example.bloodpressurerecord.update.UpdateViewModel
import java.time.LocalTime
import kotlinx.coroutines.CancellationException

@Composable
fun SettingsProfileScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onSavedFeedback: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }

    val handleBack = {
        when {
            uiState.isSavingProfile -> Unit
            uiState.isProfileDirty -> showDiscardDialog = true
            else -> onBack()
        }
    }
    BackHandler(enabled = uiState.isSavingProfile || uiState.isProfileDirty) {
        if (!uiState.isSavingProfile) showDiscardDialog = true
    }

    if (showDiscardDialog && !uiState.isSavingProfile) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("您对个人资料或达标值的修改尚未保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    viewModel.discardProfileChanges()
                    onBack()
                }) { Text("放弃并返回", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") }
            }
        )
    }

    val genderOptions = remember { listOf("男", "女", "不填") }
    val selectedGenderIndex = when (uiState.gender) {
        "男" -> 0
        "女" -> 1
        "" -> 2
        else -> -1
    }

    SettingsSubPageShell("用户资料", handleBack) {
        Text(
            "个人信息与达标值",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        DataCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    enabled = uiState.isProfileLoaded && !uiState.isSavingProfile,
                    label = { Text("姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uiState.ageText,
                    onValueChange = viewModel::updateAgeText,
                    enabled = uiState.isProfileLoaded && !uiState.isSavingProfile,
                    label = { Text("年龄") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.ageError != null,
                    supportingText = uiState.ageError?.let { error -> { Text(error) } }
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "性别",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SegmentedPillGroup(
                        options = genderOptions,
                        selectedIndex = if (selectedGenderIndex >= 0) selectedGenderIndex else 2,
                        enabled = uiState.isProfileLoaded && !uiState.isSavingProfile,
                        onSelect = { idx ->
                            val value = when (idx) {
                                0 -> "男"
                                1 -> "女"
                                else -> ""
                            }
                            viewModel.updateGender(value)
                        }
                    )
                    if (selectedGenderIndex < 0 && uiState.gender.isNotBlank()) {
                        Text(
                            "当前自定义值：${uiState.gender}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.targetSystolicText,
                    onValueChange = viewModel::updateTargetSystolicText,
                    enabled = uiState.isProfileLoaded && !uiState.isSavingProfile,
                    label = { Text("达标高压（用于趋势参考线与达标率）") },
                    placeholder = { Text("例如 135") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.targetSystolicError != null,
                    supportingText = uiState.targetSystolicError?.let { error -> { Text(error) } }
                )
                OutlinedTextField(
                    value = uiState.targetDiastolicText,
                    onValueChange = viewModel::updateTargetDiastolicText,
                    enabled = uiState.isProfileLoaded && !uiState.isSavingProfile,
                    label = { Text("达标低压（用于趋势参考线与达标率）") },
                    placeholder = { Text("例如 85") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.targetDiastolicError != null,
                    supportingText = uiState.targetDiastolicError?.let { error -> { Text(error) } }
                )
            }
        }
        AppPrimaryButton(
            text = if (uiState.isSavingProfile) "正在保存…" else "保存资料",
            enabled = uiState.isProfileLoaded && !uiState.isSavingProfile,
            onClick = {
                viewModel.saveUserProfile {
                    onSavedFeedback()
                    onBack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        SettingsMessage(uiState.message, isError = uiState.messageIsError)
    }
}

private data class PendingMedicationDelete(
    val id: Long,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsReminderScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reducedMotion = LocalReducedMotion.current
    var authorizationStatus by remember {
        mutableStateOf(ReminderAuthorization.status(context))
    }
    var pendingMedicationDelete by remember { mutableStateOf<PendingMedicationDelete?>(null) }

    pendingMedicationDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingMedicationDelete = null },
            title = { Text("删除药品？") },
            text = { Text("将删除「${pending.name}」及其全部服药时间。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedication(pending.id)
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

    fun checkNotificationPermissionForReminder(systemDisabledMessage: String) {
        authorizationStatus = ReminderAuthorization.status(context)
        when (authorizationStatus) {
            ReminderAuthorizationStatus.GRANTED -> Unit
            ReminderAuthorizationStatus.RUNTIME_PERMISSION_REQUIRED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            ReminderAuthorizationStatus.SYSTEM_DISABLED -> {
                viewModel.showReminderAuthorizationRequired(systemDisabledMessage)
            }
        }
    }

    fun requestEnable(type: ReminderType) {
        when (type) {
            ReminderType.MORNING -> viewModel.setMorningReminderEnabled(true)
            ReminderType.EVENING -> viewModel.setEveningReminderEnabled(true)
        }
        checkNotificationPermissionForReminder(
            "提醒已启用，但系统通知已关闭；应用内闹钟仍会继续安排。请前往系统通知设置开启通知。"
        )
    }

    SettingsSubPageShell("提醒中心", onBack) {
        AnimatedVisibility(
            visible = authorizationStatus != ReminderAuthorizationStatus.GRANTED,
            enter = AppMotion.expandVisibilityEnter(reducedMotion),
            exit = AppMotion.expandVisibilityExit(reducedMotion)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DataCard {
                    Text(
                        "通知权限未开启。提醒仍会按时触发，但不会显示系统通知。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                AppPrimaryButton(
                    text = "前往系统通知设置",
                    onClick = {
                        context.startActivity(ReminderAuthorization.settingsIntent(context))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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
            onTimeSelected = { time ->
                val wasDisabled = !uiState.morningReminderEnabled
                viewModel.updateMorningTime(time)
                if (wasDisabled) {
                    checkNotificationPermissionForReminder(
                        "已自动开启晨间提醒，但系统通知已关闭；请前往系统通知设置开启通知。"
                    )
                }
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
            onTimeSelected = { time ->
                val wasDisabled = !uiState.eveningReminderEnabled
                viewModel.updateEveningTime(time)
                if (wasDisabled) {
                    checkNotificationPermissionForReminder(
                        "已自动开启晚间提醒，但系统通知已关闭；请前往系统通知设置开启通知。"
                    )
                }
            }
        )
        Text(
            "选择时间后会自动保存并开启对应提醒。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("服药提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "为每种降压药设置每天的服药时间，到点提醒，通知栏或首页都可一键打勾。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsSwitchRow(
            "启用服药提醒",
            uiState.medicationReminderEnabled
        ) { enabled ->
            if (enabled) {
                viewModel.setMedicationReminderEnabled(true)
                checkNotificationPermissionForReminder(
                    "服药提醒已启用，但系统通知已关闭；应用内闹钟仍会继续安排。请前往系统通知设置开启通知。"
                )
            } else {
                viewModel.setMedicationReminderEnabled(false)
            }
        }
        MedicationCalendarSettings(
            enabled = uiState.medicationCalendarSyncEnabled,
            calendarId = uiState.medicationCalendarId,
            includeDetails = uiState.medicationCalendarIncludeDetails,
            viewModel = viewModel
        )

        Text("药品与服药时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        var editorState by remember { mutableStateOf<MedicationEditorState?>(null) }
        uiState.medications.forEach { med ->
            val sortedTimes = med.times.map { it.timeText }.sorted()
            val cardInteraction = remember { MutableInteractionSource() }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableScale(cardInteraction)
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = LocalIndication.current
                    ) {
                        editorState = MedicationEditorState(
                            id = med.medication.id,
                            name = med.medication.name,
                            dosage = med.medication.dosage,
                            times = sortedTimes
                        )
                    },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${med.medication.name} ${med.medication.dosage}".trim(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (sortedTimes.isEmpty()) {
                                "未设置服药时间"
                            } else {
                                "每天 " + sortedTimes.joinToString("、")
                            },
                            style = MaterialTheme.typography.bodySmall.tabularNums(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "编辑药品",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        AppSecondaryButton(
            text = "添加药品",
            onClick = {
                editorState = MedicationEditorState(
                    id = null,
                    name = "",
                    dosage = "",
                    times = listOf("08:00")
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

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
                },
                onDelete = editor.id?.let { id ->
                    {
                        editorState = null
                        pendingMedicationDelete = PendingMedicationDelete(
                            id = id,
                            name = editor.name
                        )
                    }
                }
            )
        }

        SettingsMessage(uiState.message, isError = uiState.messageIsError)
    }
}

@Composable
private fun MedicationCalendarSettings(
    enabled: Boolean,
    calendarId: Long?,
    includeDetails: Boolean,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    val permissions = remember { arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var calendars by remember(enabled, calendarId, editing) { mutableStateOf<List<MedicationCalendarTarget>?>(null) }
    fun hasPermissions() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPermissions()) editing = true
        else viewModel.showReminderAuthorizationRequired("需要日历权限才能选择服药提醒的目标日历。")
    }
    fun configure() {
        if (hasPermissions()) editing = true else permissionLauncher.launch(permissions)
    }
    LaunchedEffect(enabled, calendarId, editing) {
        if (!editing && !enabled) return@LaunchedEffect
        if (!hasPermissions()) {
            if (editing) {
                editing = false
                viewModel.showReminderAuthorizationRequired("日历权限已关闭，请重新授权后选择日历。")
            }
            return@LaunchedEffect
        }
        try {
            val targets = viewModel.getWritableMedicationCalendars()
            calendars = targets
            if (editing && targets.isEmpty()) {
                editing = false
                viewModel.showReminderAuthorizationRequired("未找到可写入的日历，请先在系统日历中添加日历。")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (editing) {
                editing = false
                viewModel.showReminderAuthorizationRequired("无法读取系统日历，请检查日历权限后重试。")
            }
        }
    }

    Text("系统日历", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    SettingsSwitchRow("同步到系统日历", enabled) { checked ->
        if (checked) configure() else viewModel.setMedicationCalendarSyncEnabled(false)
    }
    AnimatedVisibility(
        visible = enabled,
        enter = AppMotion.expandVisibilityEnter(reducedMotion),
        exit = AppMotion.expandVisibilityExit(reducedMotion)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val target = calendars?.find { it.id == calendarId }
            val summary = target?.let {
                listOf(it.displayName, it.accountName).filter(String::isNotBlank).distinct().joinToString(" · ")
            }
            Text(
                summary?.let { "当前日历：$it" } ?: "当前日历名称暂不可用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppSecondaryButton(
                text = "更改日历或提醒内容",
                onClick = ::configure,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (editing) {
        var selectedId by rememberSaveable { mutableStateOf(calendarId.takeIf { enabled }) }
        var showDetails by rememberSaveable { mutableStateOf(enabled && includeDetails) }
        val targets = calendars
        val selected = targets?.find { it.id == selectedId }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("选择服药提醒日历") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("服药时间会写入所选日历。账户日历可能同步到云端，共享日历的其他成员也可能看到提醒内容。")
                    if (targets == null) Text("正在读取日历…")
                    Column(Modifier.selectableGroup()) {
                        targets.orEmpty().forEach { target ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(selectedId == target.id, role = Role.RadioButton, onClick = {
                                        if (selectedId != target.id) showDetails = false
                                        selectedId = target.id
                                    }),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(selected = selectedId == target.id, onClick = null)
                                Column {
                                    Text(target.displayName)
                                    if (target.accountName.isNotBlank() && target.accountName != target.displayName) {
                                        Text(target.accountName, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    SettingsSwitchRow("在日程标题中显示药名和剂量", showDetails) { showDetails = it }
                    Text(
                        if (showDetails) "日程标题格式为「服药提醒：药名 剂量」。"
                        else "日程标题为「服药提醒」。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = selected != null, onClick = {
                    selected?.let { viewModel.setMedicationCalendarSyncEnabled(true, it.id, showDetails) }
                    editing = false
                }) { Text(if (enabled) "保存" else "开启同步") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } }
        )
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
    onConfirm: (name: String, dosage: String, times: List<String>) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val reducedMotion = LocalReducedMotion.current
    var name by remember { mutableStateOf(initial.name) }
    var dosage by remember { mutableStateOf(initial.dosage) }
    var times by remember { mutableStateOf(initial.times) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var timesError by remember { mutableStateOf<String?>(null) }
    var pickerTarget by remember { mutableStateOf<String?>(null) } // "" = add new, "HH:mm" = edit existing

    pickerTarget?.let { target ->
        val initialTime = if (target.isNotEmpty()) {
            runCatching { LocalTime.parse(target) }.getOrDefault(LocalTime.of(8, 0))
        } else {
            LocalTime.of(8, 0)
        }
        WheelTimePickerDialog(
            title = if (target.isEmpty()) "添加服药时间" else "修改服药时间",
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            onDismiss = { pickerTarget = null },
            onConfirm = { hour, minute ->
                val text = "%02d:%02d".format(hour, minute)
                times = if (target.isEmpty()) {
                    (times + text).distinct().sorted()
                } else {
                    (times - target + text).distinct().sorted()
                }
                timesError = null
                pickerTarget = null
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == null) "添加药品" else "编辑药品") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .then(if (reducedMotion) Modifier else Modifier.animateContentSize(AppMotion.sizeSpec(false))),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = null
                    },
                    label = { Text("药品名称") },
                    placeholder = { Text("如：氨氯地平") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { err -> { Text(err) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("每次数量（选填）") },
                    placeholder = { Text("如：1片") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "每日服药时间（点击时间可修改）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                times.forEach { time ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickerTarget = time }
                                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                time,
                                style = MaterialTheme.typography.titleMedium.tabularNums(),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { times = times - time }) {
                                Text("移除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = timesError != null,
                    enter = AppMotion.expandVisibilityEnter(reducedMotion),
                    exit = AppMotion.expandVisibilityExit(reducedMotion)
                ) {
                    timesError?.let { err ->
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                OutlinedButton(
                    onClick = { pickerTarget = "" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("添加时间")
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {
                        Text("删除这种药品", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var valid = true
                if (name.isBlank()) {
                    nameError = "请填写药品名称"
                    valid = false
                }
                if (times.isEmpty()) {
                    timesError = "请至少保留一个服药时间"
                    valid = false
                }
                if (valid) {
                    onConfirm(name.trim(), dosage.trim(), times)
                }
            }) { Text("保存") }
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
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = { showPicker = true },
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressableScale(interactionSource),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("$label：$timeText", style = MaterialTheme.typography.labelLarge.tabularNums())
    }
}

@Composable
fun SettingsDisplayScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsSubPageShell("显示设置", onBack) {
        SettingsSwitchRow("显示趋势图", uiState.showTrendChart, viewModel::setShowTrendChart)
        SettingsSwitchRow("启用高风险提醒", uiState.highRiskAlertEnabled, viewModel::setHighRiskAlertEnabled)
        SettingsSwitchRow("大字模式", uiState.isLargeTextEnabled, viewModel::setLargeTextEnabled)
    }
}

@Composable
fun SettingsInfoScreen(
    onBack: () -> Unit,
    onOpenAppGuide: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
    updateViewModel: UpdateViewModel,
    onDownloadUpdate: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenDirectDownload: (() -> Unit)?
) {
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    SettingsSubPageShell("应用说明与更新说明", onBack) {
        UpdateSettingsContent(
            state = updateState,
            currentVersion = BuildConfig.VERSION_NAME,
            onCheck = updateViewModel::checkManually,
            onAutoCheckChanged = updateViewModel::setAutoCheckEnabled,
            onDownload = onDownloadUpdate,
            onOpenReleases = onOpenReleases,
            onOpenDirectDownload = onOpenDirectDownload
        )
        SettingListItem(
            title = "应用说明",
            subtitle = "查看当前可实现功能、测量建议和免责声明",
            icon = Icons.Outlined.Info,
            onClick = onOpenAppGuide
        )
        SettingListItem(
            title = "更新说明",
            subtitle = "查看 ${AppInfoContent.CURRENT_VERSION} 及历史版本更新记录",
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
                    Text(
                        "版本 ${note.version}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(note.summary, style = MaterialTheme.typography.bodyMedium)
                    note.changes.forEach { change ->
                        Text(
                            "• $change",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                .imePadding()
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
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale(interactionSource),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(
                    value = checked,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Switch,
                    onValueChange = onChecked
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
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
                Text(
                    "• $item",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsMessage(message: String, isError: Boolean = false) {
    val reducedMotion = LocalReducedMotion.current
    AnimatedVisibility(
        visible = message.isNotBlank(),
        enter = AppMotion.expandVisibilityEnter(reducedMotion),
        exit = AppMotion.expandVisibilityExit(reducedMotion)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
        )
    }
}
