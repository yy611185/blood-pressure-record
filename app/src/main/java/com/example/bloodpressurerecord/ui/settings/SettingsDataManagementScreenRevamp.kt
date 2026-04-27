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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsDataManagementScreenRevamp(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingExportFileName by remember { mutableStateOf(defaultBackupFileName()) }
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

    if (uiState.showBackupExportConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupExport,
            title = { Text("导出 Excel 备份") },
            text = {
                Text(
                    "导出的文件只会保存到您选择的位置，不会上传服务器。文件包含使用说明、测量记录、用户资料和导出信息。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExportFileName = defaultBackupFileName()
                        backupExportLauncher.launch(pendingExportFileName)
                    }
                ) {
                    Text("选择保存位置")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBackupExport) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(title = "数据管理", onBack = onBack)

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
                        text = "• 数据仅保存在您的手机本机\n• 不会上传任何服务器\n• 导出的 Excel 文件可用于本地备份、换机迁移或家人查看",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
