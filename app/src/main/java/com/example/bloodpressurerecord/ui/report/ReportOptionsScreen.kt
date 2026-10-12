package com.example.bloodpressurerecord.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding

@Composable
fun ReportScreen(viewModel: ReportViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val container = (context.applicationContext as BloodPressureApplication).appContainer
    if (state.artifact != null) {
        ReportPreviewScreen(state, viewModel, container.reportCache, onBack)
    } else {
        ReportOptionsScreen(state, viewModel, onBack)
    }
    if (state.showIdentityConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissIdentityConfirmation,
            title = { Text("是否包含姓名和年龄？") },
            text = {
                Column {
                    Text("此选择只用于本次报告，下次导出会再次询问。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(state.options.includeIdentity, viewModel::setIncludeIdentity)
                        Text("包含姓名和年龄")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAndGenerate) { Text("生成预览") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissIdentityConfirmation) { Text("取消") }
            }
        )
    }
}

@Composable
fun ReportOptionsScreen(state: ReportUiState, viewModel: ReportViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = statusBarTopPadding())) {
        AppTopBar(title = "导出医生报告", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("近 30 天", style = MaterialTheme.typography.titleLarge)
                    Text("${state.period.startDate} 至 ${state.period.endDate}")
                    Text("摘要、逐日明细及补充记录将生成在同一份 PDF 中。")
                }
            }
            Text("报告在本机生成。请先检查预览，再分享或保存。")
            Text(
                "现有记录未区分测量场景，分级与参考线采用诊室标准。用药仅显示现有药品信息和保留的打卡次数。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.isGenerating) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(
                onClick = viewModel::requestPreview,
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.isGenerating) "正在生成报告…" else "生成预览") }
            if (state.message.isNotEmpty()) Text(state.message)
        }
    }
}
