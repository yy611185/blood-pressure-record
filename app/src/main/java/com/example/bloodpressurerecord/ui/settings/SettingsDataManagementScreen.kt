package com.example.bloodpressurerecord.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.data.repository.backup.BackupCrypto
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppDangerButton
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.pageContentBottomPadding
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppMotion
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsDataManagementScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenDoctorReport: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val dataBusy = uiState.isDataActionRunning || uiState.isChoosingBackupLocation
    val safeBack = {
        if (!dataBusy) onBack()
    }
    BackHandler(enabled = dataBusy) { }

    uiState.pendingExportFormatSwitchToEncrypted?.let { switchToEncrypted ->
        val currentLabel = if (uiState.exportEncrypted) "加密备份（.bpx）" else "明文 Excel（.xlsx）"
        val targetLabel = if (switchToEncrypted) "加密备份（.bpx）" else "明文 Excel（.xlsx）"
        val progressDetail = if (uiState.exportVolumeCount > 1) {
            "当前还有未完成的$currentLabel 导出（已保存 ${uiState.exportVolumeIndex}/${uiState.exportVolumeCount} 卷）。"
        } else {
            "当前有一项已暂停的$currentLabel 导出尚未保存。"
        }
        AlertDialog(
            onDismissRequest = { if (!dataBusy) viewModel.dismissSwitchExportFormat() },
            title = { Text(if (switchToEncrypted) "切换为加密备份？" else "切换为明文 Excel？") },
            text = {
                Text("${progressDetail}切换到$targetLabel 将放弃当前进度并重新开始。")
            },
            confirmButton = {
                TextButton(
                    enabled = !dataBusy,
                    onClick = viewModel::confirmSwitchExportFormat
                ) { Text("放弃当前并切换") }
            },
            dismissButton = {
                TextButton(
                    enabled = !dataBusy,
                    onClick = viewModel::dismissSwitchExportFormat
                ) { Text("保留原导出") }
            }
        )
    }

    if (uiState.showBackupExportConfirm) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isDataActionRunning) viewModel.dismissBackupExport() },
            title = { Text("导出 Excel 备份") },
            text = {
                Text(
                    "明文导出的 Excel 表格未加密，可能包含姓名、年龄、血压记录、症状、备注和提醒设置。" +
                        "请勿保存到公共设备、不受信任的云盘或与他人共享的位置。文件只会保存到你选择的位置，" +
                        "应用不会上传服务器。超过 5000 条记录会自动分卷，建议使用「加密导出（推荐）」。"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isDataActionRunning,
                    onClick = viewModel::requestEncryptedBackupExport
                ) {
                    Text("加密导出（推荐）")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = !uiState.isDataActionRunning,
                        onClick = {
                            viewModel.prepareBackupExport(defaultBackupFileName().removeSuffix(".xlsx"))
                        }
                    ) {
                        Text("明文导出")
                    }
                    TextButton(
                        enabled = !uiState.isDataActionRunning,
                        onClick = viewModel::dismissBackupExport
                    ) {
                        Text("取消")
                    }
                }
            }
        )
    }

    if (uiState.showExportPassphraseDialog) {
        var exportPassphrase by remember { mutableStateOf("") }
        var exportPassphraseConfirm by remember { mutableStateOf("") }
        var showPassphrase by remember { mutableStateOf(false) }
        var showPassphraseConfirm by remember { mutableStateOf(false) }
        val passphraseValid = exportPassphrase.length >= 8 &&
            exportPassphrase == exportPassphraseConfirm
        AlertDialog(
            onDismissRequest = viewModel::dismissEncryptedBackupExport,
            title = { Text("设置备份口令") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "备份将以 .bpx 加密格式保存，导入时必须输入相同口令。" +
                            "口令不会保存在设备上，遗失后无法恢复数据，请牢记或另行保存。"
                    )
                    OutlinedTextField(
                        value = exportPassphrase,
                        onValueChange = { exportPassphrase = it },
                        label = { Text("备份口令（至少 8 位）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        visualTransformation = if (showPassphrase) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    imageVector = if (showPassphrase) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassphrase) "隐藏口令" else "显示口令"
                                )
                            }
                        }
                    )
                    OutlinedTextField(
                        value = exportPassphraseConfirm,
                        onValueChange = { exportPassphraseConfirm = it },
                        label = { Text("再次输入口令") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        visualTransformation = if (showPassphraseConfirm) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassphraseConfirm = !showPassphraseConfirm }) {
                                Icon(
                                    imageVector = if (showPassphraseConfirm) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassphraseConfirm) "隐藏口令" else "显示口令"
                                )
                            }
                        },
                        isError = exportPassphraseConfirm.isNotEmpty() &&
                            exportPassphrase != exportPassphraseConfirm,
                        supportingText = {
                            if (exportPassphraseConfirm.isNotEmpty() &&
                                exportPassphrase != exportPassphraseConfirm
                            ) {
                                Text("两次输入的口令不一致")
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphraseValid && !uiState.isDataActionRunning,
                    onClick = {
                        viewModel.prepareBackupExport(
                            defaultEncryptedBackupFileName().removeSuffix(".${BackupCrypto.FILE_EXTENSION}"),
                            exportPassphrase.toCharArray()
                        )
                        exportPassphrase = ""
                        exportPassphraseConfirm = ""
                    }
                ) { Text("选择保存位置") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEncryptedBackupExport) { Text("取消") }
            }
        )
    }

    val pickerRequestId = uiState.exportVolumeRequestId ?: viewModel.interruptedExportRequestId
    val exportLauncher = pickerRequestId?.let { requestId ->
        val mime = if (uiState.exportEncrypted) "application/octet-stream"
            else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        key(requestId) {
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
                viewModel.finishExportBackupLocation(requestId, uri)
            }
        }
    }
    uiState.exportVolumeRequestId?.let { requestId ->
        val isMultiVolume = uiState.exportVolumeCount > 1
        AlertDialog(
            onDismissRequest = viewModel::pauseBackupExport,
            title = {
                Text(
                    if (isMultiVolume) {
                        "保存第 ${uiState.exportVolumeIndex + 1}/${uiState.exportVolumeCount} 卷"
                    } else {
                        "保存备份文件"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isMultiVolume) {
                        Text("共 ${uiState.exportTotalRecords} 条测量，每卷最多 5000 条；本卷 ${uiState.exportVolumeRecords} 条。每次测量和原始读数组会完整保留。")
                        Text("用户资料和设置在第一卷，用药数据可能分布在多卷。")
                        Text(
                            "请依次保存全部 ${uiState.exportVolumeCount} 卷，恢复时从第 1 卷起逐卷导入。" +
                                if (uiState.exportEncrypted) "全部卷使用同一口令。" else "文件为明文 Excel。"
                        )
                    } else {
                        Text("共 ${uiState.exportTotalRecords} 条测量记录（含原始读数组、用户资料、设置与用药数据）。")
                        Text(
                            if (uiState.exportEncrypted) {
                                "将以加密备份（.bpx）格式保存，请妥善保管文件并牢记口令。"
                            } else {
                                "将以明文 Excel（.xlsx）格式保存，请注意隐私保护。"
                            }
                        )
                    }
                    Text(uiState.exportFileName, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !dataBusy, onClick = {
                    if (exportLauncher != null && viewModel.beginBackupLocation(requestId)) {
                        try { exportLauncher.launch(uiState.exportFileName) }
                        catch (_: Exception) { viewModel.onBackupLocationLaunchFailed() }
                    }
                }) { Text(if (isMultiVolume) "选择本卷保存位置" else "选择保存位置") }
            },
            dismissButton = {
                TextButton(enabled = !dataBusy, onClick = viewModel::pauseBackupExport) { Text("稍后继续") }
            }
        )
    }

    if (uiState.showImportPassphraseDialog) {
        var importPassphrase by remember { mutableStateOf("") }
        var showImportPassphrase by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::dismissImportPassphrase,
            title = { Text("输入备份口令") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("该备份文件已加密，请输入导出时设置的口令以生成预览。")
                    OutlinedTextField(
                        value = importPassphrase,
                        onValueChange = { importPassphrase = it },
                        label = { Text("备份口令") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        visualTransformation = if (showImportPassphrase) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showImportPassphrase = !showImportPassphrase }) {
                                Icon(
                                    imageVector = if (showImportPassphrase) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showImportPassphrase) "隐藏口令" else "显示口令"
                                )
                            }
                        },
                        isError = uiState.importPassphraseError != null,
                        supportingText = {
                            uiState.importPassphraseError?.let { Text(it) }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importPassphrase.isNotEmpty() && !uiState.isDataActionRunning,
                    onClick = {
                        viewModel.submitImportPassphrase(importPassphrase.toCharArray())
                        importPassphrase = ""
                    }
                ) { Text("继续预览") }
            },
            dismissButton = {
                TextButton(onClick = {
                    importPassphrase = ""
                    viewModel.dismissImportPassphrase()
                }) { Text("取消") }
            }
        )
    }

    if (uiState.showBackupImportConfirm) {
        val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            viewModel.finishBackupLocation {
                if (viewModel.uiState.value.showBackupImportConfirm) {
                    if (uri != null) viewModel.previewBackupXlsxFromUri(uri) else viewModel.dismissBackupImport()
                }
            }
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupImport,
            title = { Text("从备份文件恢复") },
            text = {
                Text("支持明文 .xlsx 与加密 .bpx 备份文件。先选择文件生成预览；预览阶段不会写入本机数据。确认后可选择恢复记录、用户资料、显示设置、提醒设置和用药数据。")
            },
            confirmButton = {
                TextButton(
                    enabled = !dataBusy,
                    onClick = {
                        if (viewModel.beginBackupLocation()) {
                            try {
                                backupImportLauncher.launch(arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/octet-stream"
                                ))
                            } catch (_: Exception) {
                                viewModel.onBackupLocationLaunchFailed()
                            }
                        }
                    }
                ) { Text("选择备份文件") }
            },
            dismissButton = {
                TextButton(enabled = !dataBusy, onClick = viewModel::dismissBackupImport) { Text("取消") }
            }
        )
    }

    uiState.backupImportPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupImport,
            title = { Text("确认导入范围") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    preview.volumeDescription?.let { Text(it) }
                    Text(
                        "可用记录 ${preview.validRecordCount} 条，新增 ${preview.insertedCount} 条，" +
                            "覆盖 ${preview.replacedCount} 条，自动修正 ${preview.correctedCount} 条，" +
                            "跳过 ${preview.skippedCount} 条，原始读数 ${preview.readingCount} 组。"
                    )
                    Text(
                        if (preview.medicationCount > 0 || preview.medicationLogCount > 0) {
                            "包含药品 ${preview.medicationCount} 种、每日时间点 ${preview.medicationTimeCount} 个、服药打卡 ${preview.medicationLogCount} 条。"
                        } else {
                            "此备份不包含用药数据；现有药品不会因此被清空。"
                        }
                    )
                    if (preview.errorCount > 0) {
                        Text(
                            "校验提示 ${preview.errorCount} 条；被跳过的记录不会写入。",
                            color = MaterialTheme.colorScheme.error
                        )
                        preview.errors.take(5).forEach { error ->
                            Text(
                                text = "• ${error.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (preview.errorCount > 5) {
                            Text(
                                text = "还有 ${preview.errorCount - 5} 条校验提示未展开。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.importMeasurementsSelected,
                            onCheckedChange = viewModel::setImportMeasurementsSelected,
                            enabled = preview.validRecordCount > 0
                        )
                        Text("导入测量记录（已存在的同条记录将自动覆盖更新）")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.restoreMedicationsSelected,
                            onCheckedChange = viewModel::setRestoreMedicationsSelected,
                            enabled = preview.medicationCount > 0
                        )
                        Text("恢复用药数据（药品、时间点及打卡）")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.restoreUserProfileSelected,
                            onCheckedChange = viewModel::setRestoreUserProfileSelected,
                            enabled = preview.hasSettings
                        )
                        Text("恢复用户资料" + if (preview.changesName || preview.changesAgeAndGender || preview.changesTargetPressure) "（有变化）" else "")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.restoreDisplaySettingsSelected,
                            onCheckedChange = viewModel::setRestoreDisplaySettingsSelected,
                            enabled = preview.hasSettings
                        )
                        Text("恢复显示设置" + if (preview.changesDisplaySettings) "（有变化）" else "")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.restoreReminderSettingsSelected,
                            onCheckedChange = viewModel::setRestoreReminderSettingsSelected,
                            enabled = preview.hasSettings
                        )
                        Text(
                            "恢复提醒设置" +
                                (if (preview.hasMedicationReminderSetting) "（含服药提醒开关）" else "") +
                                (if (preview.changesReminderTimes || preview.changesReminderEnabled) "（有变化）" else "")
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isDataActionRunning && uiState.hasImportSelection,
                    onClick = viewModel::commitBackupImport
                ) {
                    Text(if (uiState.activeDataAction == SettingsDataAction.IMPORT) "正在导入…" else "确认导入")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isDataActionRunning,
                    onClick = viewModel::dismissBackupImport
                ) { Text("取消") }
            }
        )
    }

    if (uiState.showClearConfirm) {
        var acknowledged by remember { mutableStateOf(false) }
        val isClearing = uiState.activeDataAction == SettingsDataAction.CLEAR
        AlertDialog(
            onDismissRequest = { if (!uiState.isDataActionRunning) viewModel.dismissClearAll() },
            title = { Text("再次确认清空数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "将永久清空本机全部血压记录、药品与服药打卡、个人资料和偏好设置，且无法恢复。建议先导出备份。"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = acknowledged,
                                enabled = !uiState.isDataActionRunning,
                                role = androidx.compose.ui.semantics.Role.Checkbox,
                                onValueChange = { acknowledged = it }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acknowledged,
                            enabled = !uiState.isDataActionRunning,
                            onCheckedChange = null
                        )
                        Text(
                            "我已知晓清空后无法恢复",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = acknowledged && !uiState.isDataActionRunning,
                    onClick = viewModel::confirmClearAll
                ) {
                    Text(
                        text = if (isClearing) "正在清空…" else "永久清空",
                        color = if (acknowledged && !uiState.isDataActionRunning) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = !uiState.isDataActionRunning,
                        onClick = {
                            viewModel.dismissClearAll()
                            viewModel.requestBackupExport()
                        }
                    ) { Text("先导出备份") }
                    TextButton(
                        enabled = !uiState.isDataActionRunning,
                        onClick = viewModel::dismissClearAll
                    ) { Text("取消") }
                }
            }
        )
    }

    val topBarScroll = rememberHideOnScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarTopPadding())
            .nestedScroll(topBarScroll.nestedScrollConnection)
    ) {
        AppTopBar(title = "数据管理", onBack = safeBack, hideOnScroll = topBarScroll)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = pageContentBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("本地备份与导出", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "• 健康记录默认保存在本机，不主动上传\n" +
                            "• Android 系统自动云备份已关闭\n" +
                            "• 卸载或换机前请务必先导出备份\n" +
                            "• 应用仅用于健康记录，不提供医学诊断",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                    Text(
                        text = uiState.lastSuccessfulExportAt?.let {
                            "上次成功导出：" + Instant.ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
                        } ?: "上次成功导出：尚未导出",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.isDataActionRunning,
                enter = AppMotion.expandVisibilityEnter(reducedMotion),
                exit = AppMotion.expandVisibilityExit(reducedMotion)
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Text("导出与备份", style = MaterialTheme.typography.titleMedium)
            AppSecondaryButton(
                text = "导出医生报告 PDF",
                onClick = onOpenDoctorReport,
                enabled = !dataBusy,
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.exportVolumeCount > 1) {
                Text(
                    "当前${if (uiState.exportEncrypted) "加密备份（.bpx）" else "明文 Excel（.xlsx）"}导出共 ${uiState.exportVolumeCount} 卷，每卷最多 5000 条记录，请依次保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppPrimaryButton(
                text = when {
                    uiState.activeDataAction == SettingsDataAction.EXPORT_XLSX -> "正在导出…"
                    uiState.exportVolumeCount > 1 && !uiState.exportEncrypted ->
                        "继续保存明文 Excel（第 ${uiState.exportVolumeIndex + 1}/${uiState.exportVolumeCount} 卷）"
                    uiState.exportVolumeCount == 1 && !uiState.exportEncrypted -> "继续保存明文 Excel"
                    else -> "导出明文 Excel (.xlsx)"
                },
                icon = Icons.Outlined.SaveAlt,
                onClick = viewModel::requestBackupExport,
                enabled = !dataBusy,
                modifier = Modifier.fillMaxWidth()
            )

            AppSecondaryButton(
                text = when {
                    uiState.activeDataAction == SettingsDataAction.EXPORT_BPX -> "正在导出…"
                    uiState.exportVolumeCount > 1 && uiState.exportEncrypted ->
                        "继续保存加密备份（第 ${uiState.exportVolumeIndex + 1}/${uiState.exportVolumeCount} 卷）"
                    uiState.exportVolumeCount == 1 && uiState.exportEncrypted -> "继续保存加密备份"
                    else -> "加密导出（推荐 · .bpx）"
                },
                onClick = viewModel::requestEncryptedBackupExport,
                enabled = !dataBusy,
                modifier = Modifier.fillMaxWidth()
            )

            AppSecondaryButton(
                text = if (uiState.activeDataAction == SettingsDataAction.IMPORT) {
                    "正在导入…"
                } else {
                    "从备份文件恢复（支持 .xlsx / .bpx）"
                },
                onClick = viewModel::requestBackupImport,
                enabled = !dataBusy,
                modifier = Modifier.fillMaxWidth()
            )

            Text("设备数据", style = MaterialTheme.typography.titleMedium)
            AppDangerButton(
                text = if (uiState.activeDataAction == SettingsDataAction.CLEAR) {
                    "正在清空…"
                } else {
                    "清空所有本地数据"
                },
                onClick = viewModel::requestClearAll,
                enabled = !dataBusy,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = uiState.message.isNotBlank(),
                enter = AppMotion.expandVisibilityEnter(reducedMotion),
                exit = AppMotion.expandVisibilityExit(reducedMotion)
            ) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.messageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

private fun defaultBackupFileName(): String {
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
    return "家庭血压记录备份_$stamp.xlsx"
}

private fun defaultEncryptedBackupFileName(): String {
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
    return "家庭血压记录备份_$stamp.${BackupCrypto.FILE_EXTENSION}"
}
