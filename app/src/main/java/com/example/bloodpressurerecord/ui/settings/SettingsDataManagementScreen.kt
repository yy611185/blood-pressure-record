package com.example.bloodpressurerecord.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.data.repository.backup.BackupCrypto
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton
import com.example.bloodpressurerecord.ui.common.AppDangerButton
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsDataManagementScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingExportFileName by remember { mutableStateOf(defaultBackupFileName()) }
    var pendingEncryptedExportFileName by remember {
        mutableStateOf(defaultEncryptedBackupFileName())
    }
    var exportPassphrase by remember { mutableStateOf("") }
    var exportPassphraseConfirm by remember { mutableStateOf("") }
    var importPassphrase by remember { mutableStateOf("") }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupXlsxToUri(uri, pendingExportFileName)
        } else {
            viewModel.dismissBackupExport()
        }
    }
    val encryptedExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportEncryptedBackupToUri(
                uri,
                pendingEncryptedExportFileName,
                exportPassphrase.toCharArray()
            )
            exportPassphrase = ""
            exportPassphraseConfirm = ""
        } else {
            viewModel.dismissEncryptedBackupExport()
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.previewBackupXlsxFromUri(uri)
        } else {
            viewModel.dismissBackupImport()
        }
    }

    if (uiState.showBackupExportConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupExport,
            title = { Text("导出 Excel 备份") },
            text = {
                Text(
                    "明文导出的 Excel 文件未加密，可能包含姓名、年龄、血压记录、症状、备注和提醒设置。" +
                        "请勿保存到公共设备、不受信任的云盘或与他人共享的位置。文件只会保存到您选择的位置，" +
                        "应用不会上传服务器。建议选择“加密导出”。"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::requestEncryptedBackupExport) {
                    Text("加密导出")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            pendingExportFileName = defaultBackupFileName()
                            backupExportLauncher.launch(pendingExportFileName)
                        }
                    ) {
                        Text("明文导出")
                    }
                    TextButton(onClick = viewModel::dismissBackupExport) {
                        Text("取消")
                    }
                }
            }
        )
    }

    if (uiState.showExportPassphraseDialog) {
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
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = exportPassphraseConfirm,
                        onValueChange = { exportPassphraseConfirm = it },
                        label = { Text("再次输入口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
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
                        pendingEncryptedExportFileName = defaultEncryptedBackupFileName()
                        encryptedExportLauncher.launch(pendingEncryptedExportFileName)
                    }
                ) { Text("选择保存位置") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEncryptedBackupExport) { Text("取消") }
            }
        )
    }

    if (uiState.showImportPassphraseDialog) {
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
                        visualTransformation = PasswordVisualTransformation(),
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
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupImport,
            title = { Text("导入 Excel 备份") },
            text = {
                Text("先选择文件生成预览；预览阶段不会写入本机数据。确认后可选择导入记录、用户资料、显示设置和提醒设置。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        backupImportLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/octet-stream"
                            )
                        )
                    }
                ) { Text("选择备份文件") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBackupImport) { Text("取消") }
            }
        )
    }

    uiState.backupImportPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupImport,
            title = { Text("确认导入范围") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            onCheckedChange = viewModel::setImportMeasurementsSelected
                        )
                        Text("导入测量记录（覆盖同 record_id）")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.restoreUserProfileSelected,
                            onCheckedChange = viewModel::setRestoreUserProfileSelected
                        )
                        Text("恢复用户资料" + if (preview.changesName || preview.changesAgeAndGender || preview.changesTargetPressure) "（有变化）" else "")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.restoreDisplaySettingsSelected,
                            onCheckedChange = viewModel::setRestoreDisplaySettingsSelected
                        )
                        Text("恢复显示设置" + if (preview.changesDisplaySettings) "（有变化）" else "")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.restoreReminderSettingsSelected,
                            onCheckedChange = viewModel::setRestoreReminderSettingsSelected
                        )
                        Text(
                            "恢复提醒设置" +
                                if (preview.changesReminderTimes || preview.changesReminderEnabled) "（有变化）" else ""
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isDataActionRunning,
                    onClick = viewModel::commitBackupImport
                ) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBackupImport) { Text("取消") }
            }
        )
    }

    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAll,
            title = { Text("再次确认清空数据") },
            text = { Text("此操作会永久删除所有测量记录和用户资料，且无法撤销。建议先导出 Excel 备份。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmClearAll) {
                    Text("永久清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearAll) { Text("取消") }
            }
        )
    }

    val topBarScroll = rememberHideOnScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(topBarScroll.nestedScrollConnection)
    ) {
        AppTopBar(title = "数据管理", onBack = onBack, hideOnScroll = topBarScroll)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
                        text = "• 数据保存在本机，应用不主动上传\n" +
                            "• Android 系统自动备份已关闭\n" +
                            "• 卸载前请主动导出 Excel 备份\n" +
                            "• 应用不提供医学诊断",
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

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isDataActionRunning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AppPrimaryButton(
                text = if (uiState.isDataActionRunning) "正在处理..." else "导出为 Excel (.xlsx)",
                icon = Icons.Outlined.SaveAlt,
                onClick = {
                    if (!uiState.isDataActionRunning) {
                        viewModel.requestBackupExport()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            AppSecondaryButton(
                text = if (uiState.isDataActionRunning) "正在处理..." else "从 Excel 备份导入",
                onClick = {
                    if (!uiState.isDataActionRunning) viewModel.requestBackupImport()
                },
                modifier = Modifier.fillMaxWidth()
            )

            AppDangerButton(
                text = "清空所有本地数据",
                onClick = {
                    if (!uiState.isDataActionRunning) viewModel.requestClearAll()
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.message.isNotBlank()) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
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
