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
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
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
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.ExpandableSection
import com.example.bloodpressurerecord.ui.common.SegmentedPillGroup
import com.example.bloodpressurerecord.ui.common.WheelTimePickerDialog
import com.example.bloodpressurerecord.ui.common.focusRing
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
            text = { Text("你对个人资料或达标值的修改尚未保存。") },
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
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uiState.ageText,
                    onValueChange = viewModel::updateAgeText,
                    enabled = uiState.isProfileLoaded && !uiState.isSavingProfile,
                    label = { Text("年龄") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
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
                    label = { Text("个人达标高压（收缩压）") },
                    placeholder = { Text("例如 135") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.targetSystolicError != null,
                    supportingText = uiState.targetSystolicError?.let { error -> { Text(error) } }
                )
                OutlinedTextField(
                    value = uiState.targetDiastolicText,
                    onValueChange = viewModel::updateTargetDiastolicText,
                    enabled = uiState.isProfileLoaded && !uiState.isSavingProfile,
                    label = { Text("个人达标低压（舒张压）") },
                    placeholder = { Text("例如 85") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.targetDiastolicError != null,
                    supportingText = uiState.targetDiastolicError?.let { error -> { Text(error) } }
                )
                Text(
                    "个人达标值默认未设置，可按医嘱或家庭自测参考自行填写；填写一项即可显示对应的趋势目标线，两项齐全才计算日历达标天数与趋势达标率。医学分级沿用诊室标准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            text = {
                Text(
                    if (uiState.medicationCalendarSyncEnabled) {
                        "将删除「${pending.name}」及其全部服药计划和历史打卡记录，并移除已同步的日历日程。删除后可在底部提示条立即撤销。"
                    } else {
                        "将删除「${pending.name}」及其全部服药计划和历史打卡记录。删除后可在底部提示条立即撤销。"
                    }
                )
            },
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
                "提醒开关已开启，但未授予通知权限，到点后无法弹出系统通知。"
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
            "提醒已开启，但系统通知已关闭。请前往系统通知设置开启通知，以免错过提醒。"
        )
    }

    SettingsSubPageShell("提醒与服药", onBack) {
        AnimatedVisibility(
            visible = authorizationStatus != ReminderAuthorizationStatus.GRANTED,
            enter = AppMotion.expandVisibilityEnter(reducedMotion),
            exit = AppMotion.expandVisibilityExit(reducedMotion)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DataCard {
                    Text(
                        "系统通知权限未开启，到点后将无法弹出提醒。请开启通知；如遇提醒延迟，请在手机省电或后台管理中允许本应用后台运行。",
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
            "早间提醒",
            uiState.morningReminderEnabled
        ) { enabled ->
            if (enabled) requestEnable(ReminderType.MORNING)
            else viewModel.setMorningReminderEnabled(false)
        }
        ReminderTimePickerButton(
            label = "早间提醒时间",
            timeText = uiState.morningReminderTime,
            onTimeSelected = { time ->
                val wasDisabled = !uiState.morningReminderEnabled
                viewModel.updateMorningTime(time)
                if (wasDisabled) {
                    checkNotificationPermissionForReminder(
                        "已自动开启早间提醒，但系统通知已关闭；请前往系统通知设置开启通知。"
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
            "为常用药品设置每日服药时间，到点发送通用隐私通知；首页可一键打卡，点按通知可在应用内核对具体药品后确认打卡。如遇提醒延迟，请在系统省电管理中允许后台运行。",
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
                    "服药提醒已开启，但系统通知已关闭。请前往系统通知设置开启通知，以免错过提醒。"
                )
            } else {
                viewModel.setMedicationReminderEnabled(false)
            }
        }
        MedicationCalendarSettings(
            enabled = uiState.medicationCalendarSyncEnabled,
            calendarId = uiState.medicationCalendarId,
            includeDetails = uiState.medicationCalendarIncludeDetails,
            cleanupPending = uiState.medicationCalendarCleanupPending,
            viewModel = viewModel
        )

        Text("药品与服药时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        var editorState by remember { mutableStateOf<MedicationEditorState?>(null) }
        uiState.medications.forEach { med ->
            val sortedTimes = med.times.map { it.timeText }.sorted()
            val cardInteraction = remember { MutableInteractionSource() }
            val cardShape = RoundedCornerShape(24.dp)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null
                    ) {
                        editorState = MedicationEditorState(
                            id = med.medication.id,
                            name = med.medication.name,
                            dosage = med.medication.dosage,
                            times = sortedTimes
                        )
                    }
                    .pressableScale(cardInteraction, pressedScale = 0.985f)
                    .focusRing(cardInteraction, cardShape),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .indication(cardInteraction, LocalIndication.current)
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
    cleanupPending: Boolean,
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
    var requestingCleanup by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPermissions()) {
            if (requestingCleanup) viewModel.setMedicationCalendarSyncEnabled(false) else editing = true
        } else {
            viewModel.showReminderAuthorizationRequired("需要日历读写权限才能管理服药日程。")
        }
        requestingCleanup = false
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
    if (cleanupPending) {
        Text("同步已关闭，先前写入的服药日程仍待清理。")
        AppSecondaryButton(
            text = "清理旧服药日程",
            onClick = {
                if (hasPermissions()) {
                    viewModel.setMedicationCalendarSyncEnabled(false)
                } else {
                    requestingCleanup = true
                    permissionLauncher.launch(permissions)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
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
                                    .clip(RoundedCornerShape(12.dp))
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
                    placeholder = { Text("请输入药品名称") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    isError = nameError != null,
                    supportingText = nameError?.let { err -> { Text(err) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("每次用量（选填）") },
                    placeholder = { Text("如：1 片 / 5 mg") },
                    supportingText = { Text("建议带上单位（如 1 片、5 mg），输入数字后可点下方快捷补充") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                MedicationDosageUnitBar(
                    currentDosage = dosage,
                    onSelectUnit = { unit ->
                        dosage = appendMedicationDosageUnit(dosage, unit)
                    }
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
                                .clip(RoundedCornerShape(14.dp))
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
                            .heightIn(min = AppDimensions.minimumTouchTarget)
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

private val MedicationQuickUnits = listOf("片", "粒", "丸", "mg", "袋")
private val PureNumericDosageRegex = Regex("""^\d+([./]\d+)?$""")

/**
 * 药品剂量快捷单位纯文本输入辅助（AUD-09）：
 * 仅在当前输入为纯数字（整数/小数/简单分数，尚无任何单位或复杂描述）时追加单位；
 * 绝不重复追加已有单位、绝不替换其他单位、绝不在空白时臆造剂量，也不做任何换算。
 */
internal fun appendMedicationDosageUnit(currentDosage: String, unit: String): String {
    val trimmed = currentDosage.trim()
    val cleanUnit = unit.trim()
    if (trimmed.isEmpty() || cleanUnit.isEmpty()) return currentDosage
    if (!PureNumericDosageRegex.matches(trimmed)) return currentDosage
    return "$trimmed $cleanUnit"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationDosageUnitBar(
    currentDosage: String,
    onSelectUnit: (String) -> Unit
) {
    val canAppend = PureNumericDosageRegex.matches(currentDosage.trim())
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MedicationQuickUnits.forEach { unit ->
            val interactionSource = remember { MutableInteractionSource() }
            val shape = RoundedCornerShape(50)
            Surface(
                shape = shape,
                color = if (canAppend) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .heightIn(min = AppDimensions.minimumTouchTarget)
                    .clip(shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        enabled = canAppend,
                        role = Role.Button,
                        onClick = { onSelectUnit(unit) }
                    )
                    .pressableScale(interactionSource, enabled = canAppend)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+ $unit",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canAppend) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
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
    SettingsSubPageShell("使用指南与更新", onBack) {
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
            title = "使用指南",
            subtitle = "快速上手、血压分级标准、数据隐私与测量建议",
            icon = Icons.Outlined.Info,
            onClick = onOpenAppGuide
        )
        SettingListItem(
            title = "版本更新与历史",
            subtitle = "查看当前版本（${AppInfoContent.CURRENT_VERSION}）及历史更新记录",
            icon = Icons.Outlined.Update,
            onClick = onOpenReleaseNotes
        )
    }
}

@Composable
fun SettingsAppGuideScreen(onBack: () -> Unit) {
    SettingsSubPageShell("使用指南", onBack) {
        AppInfoContent.featureSections.forEach { section ->
            InfoSectionCard(section)
        }
    }
}

@Composable
fun SettingsInfoReleaseNotesScreen(onBack: () -> Unit) {
    val assets = LocalContext.current.assets
    val notes = remember(assets) {
        AppReleaseNotes.forInstalledVersion(AppInfoContent.CURRENT_VERSION) { path ->
            runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
        }
    }
    val recentNotes = notes.take(4)
    val archivedNotes = notes.drop(4)
    SettingsSubPageShell("版本更新与历史", onBack) {
        recentNotes.forEach { note ->
            ReleaseNoteCard(note)
        }
        if (archivedNotes.isNotEmpty()) {
            ExpandableSection(
                title = "更早历史版本",
                summary = "共 ${archivedNotes.size} 个历史版本（${archivedNotes.first().version} – ${archivedNotes.last().version}）",
                initiallyExpanded = false,
                stateKey = "release-notes-archive"
            ) {
                Text(
                    "以下为更早版本的历史变更记录，部分页面入口与操作方式已在后续版本中调整，请以最新《使用指南》为准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                archivedNotes.forEach { note ->
                    ReleaseNoteCard(note)
                }
            }
        }
    }
}

@Composable
private fun ReleaseNoteCard(note: ReleaseNote) {
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
    val rowShape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onValueChange = onChecked
            )
            .pressableScale(interactionSource, pressedScale = 0.985f)
            .focusRing(interactionSource, rowShape),
        shape = rowShape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .indication(interactionSource, LocalIndication.current)
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
