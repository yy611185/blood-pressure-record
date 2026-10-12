package com.example.bloodpressurerecord.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.bloodpressurerecord.BuildConfig
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton
import com.example.bloodpressurerecord.ui.common.DataCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Entire dialog scrolls, including actions, so large type never hides an escape action. */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    currentVersion: String,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    onOpenReleases: (String) -> Unit
) {
    val manifest = state.manifest ?: return
    val hasTask = state.download.phase != UpdateDownloadPhase.IDLE
    val taskMatchesLatest = hasTask && state.download.manifest?.identity == manifest.identity
    val otherTask = state.download.manifest?.takeIf { hasTask && it.identity != manifest.identity }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (state.required) "需要更新" else "发现新版本",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("最新版本 ${manifest.version}", style = MaterialTheme.typography.titleMedium)
                    SecondaryText("当前版本 $currentVersion · 安装包 ${formatUpdateFileSize(manifest.size)}")
                    SecondaryText("下载完成后，再由您确认安装。")
                    if (state.required) {
                        Text(
                            "当前版本低于建议的最低支持版本。您仍可继续记录血压、查看历史、使用提醒和备份。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                UpdateNotes(manifest.notes)
                if (taskMatchesLatest) {
                    UpdateDownloadContent(state.download, onRetry, onCancel, onInstall)
                    AppSecondaryButton("稍后查看", onDismiss, Modifier.fillMaxWidth())
                } else {
                    otherTask?.let {
                        SecondaryText("另有版本 ${it.version} 的安装包；下载此版本将替换旧任务。")
                    }
                    AppPrimaryButton("下载并安装", onDownload, Modifier.fillMaxWidth())
                    if (state.required) {
                        AppSecondaryButton("继续使用", onDismiss, Modifier.fillMaxWidth())
                    } else {
                        AppSecondaryButton("72 小时后提醒", onSnooze, Modifier.fillMaxWidth())
                        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("跳过此版本")
                        }
                    }
                }
                TextButton(
                    onClick = { onOpenReleases(manifest.releasesUrl) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("前往 GitHub 查看发布说明") }
            }
        }
    }
}

/** Embedded in the existing scrollable about page; does not own navigation or lifecycle. */
@Composable
fun UpdateSettingsContent(
    state: UpdateUiState,
    currentVersion: String,
    onCheck: () -> Unit,
    onAutoCheckChanged: (Boolean) -> Unit,
    onAutoDownloadChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleases: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "软件更新",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        DataCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当前版本 $currentVersion", style = MaterialTheme.typography.titleMedium)
                SecondaryText("上次成功检查：${formatUpdateCheckTime(state.settings.lastSuccessfulCheckAt)}")
                AppPrimaryButton(
                    text = if (state.checkState == UpdateCheckState.CHECKING) "正在检查…" else "检查更新",
                    onClick = onCheck,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.checkState != UpdateCheckState.CHECKING
                )
                when (state.checkState) {
                    UpdateCheckState.IDLE -> Unit
                    UpdateCheckState.CHECKING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        SecondaryText("正在获取最新版本信息")
                    }
                    UpdateCheckState.CURRENT -> Text("已是最新版本", style = MaterialTheme.typography.bodyMedium)
                    UpdateCheckState.AVAILABLE -> Text(
                        if (state.required) "当前版本低于最低支持版本，建议尽快更新" else "有新版本可用",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    UpdateCheckState.FAILED -> Text(
                        state.checkMessage ?: "检查失败，请稍后重试。您可以继续正常使用应用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.checkState != UpdateCheckState.FAILED && state.checkState != UpdateCheckState.CURRENT) {
                    state.checkMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            UpdatePreferenceSwitch(
                "自动检查更新", "打开应用或返回前台时检查，成功后 24 小时内不再检查。",
                state.settings.autoCheckEnabled, onAutoCheckChanged
            )
            UpdatePreferenceSwitch(
                "自动下载安装包", "发现更新后下载；安装前仍需您确认。",
                state.settings.autoDownloadEnabled, onAutoDownloadChanged
            )
            UpdatePreferenceSwitch(
                "仅 Wi-Fi 下载", "只在可用且非计费的 Wi-Fi 网络下载；关闭后允许使用移动数据。",
                state.settings.wifiOnly, onWifiOnlyChanged
            )
        }
        if (state.download.phase != UpdateDownloadPhase.IDLE) {
            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "安装包${state.download.manifest?.version?.let { " $it" }.orEmpty()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    UpdateDownloadContent(state.download, onRetry, onCancel, onInstall)
                }
            }
        }
        state.manifest?.let { manifest ->
            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("最新发布 ${manifest.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SecondaryText("安装包 ${formatUpdateFileSize(manifest.size)}")
                    UpdateNotes(manifest.notes)
                    val hasTask = state.download.phase != UpdateDownloadPhase.IDLE
                    val taskMatchesLatest = hasTask && state.download.manifest?.identity == manifest.identity
                    if (manifest.versionCode > BuildConfig.VERSION_CODE.toLong() && !taskMatchesLatest) {
                        state.download.manifest?.takeIf { hasTask }?.let {
                            SecondaryText("另有版本 ${it.version} 的安装包；下载此版本将替换旧任务。")
                        }
                        AppPrimaryButton("下载此版本", onDownload, Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { onOpenReleases(manifest.releasesUrl) }) {
                        Text("前往 GitHub 查看发布说明")
                    }
                }
            }
        }
        SecondaryText("检查与下载软件更新时会联网；健康记录、个人资料和备份文件不会上传。")
    }
}

@Composable
private fun UpdatePreferenceSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SecondaryText(description)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun UpdateNotes(notes: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("更新内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        // Server notes are untrusted plain text; never render HTML or load embedded resources.
        Text(notes.ifBlank { "此版本未提供更新说明。" }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UpdateDownloadContent(
    download: UpdateDownloadState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit
) {
    val total = download.manifest?.size ?: 0L
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            when (download.phase) {
                UpdateDownloadPhase.IDLE -> "尚未下载"
                UpdateDownloadPhase.WAITING_NETWORK -> "等待可用网络"
                UpdateDownloadPhase.DOWNLOADING -> "正在下载安装包"
                UpdateDownloadPhase.PAUSED -> "下载已暂停"
                UpdateDownloadPhase.VERIFYING -> "正在检查安装包"
                UpdateDownloadPhase.READY -> "安装包已准备好"
                UpdateDownloadPhase.FAILED -> "下载或检查失败"
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        download.source?.let { SecondaryText("下载来源：$it") }
        if (download.phase == UpdateDownloadPhase.DOWNLOADING || download.phase == UpdateDownloadPhase.PAUSED ||
            download.phase == UpdateDownloadPhase.WAITING_NETWORK) {
            if (total > 0L) {
                val progress = (download.downloadedBytes.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                SecondaryText("${formatUpdateFileSize(download.downloadedBytes)} / ${formatUpdateFileSize(total)} · ${(progress * 100).toInt()}%")
            }
        }
        if (download.phase == UpdateDownloadPhase.VERIFYING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        download.message?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (download.phase == UpdateDownloadPhase.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when (download.phase) {
            UpdateDownloadPhase.READY -> {
                SecondaryText("点击后由 Android 系统安装。请在系统页面确认更新；取消后可回来继续安装。")
                AppPrimaryButton("安装更新", onInstall, Modifier.fillMaxWidth())
                AppSecondaryButton("删除安装包", onCancel, Modifier.fillMaxWidth())
            }
            UpdateDownloadPhase.FAILED, UpdateDownloadPhase.PAUSED -> {
                AppPrimaryButton("继续下载或重试", onRetry, Modifier.fillMaxWidth())
                AppSecondaryButton("取消下载", onCancel, Modifier.fillMaxWidth())
            }
            UpdateDownloadPhase.WAITING_NETWORK, UpdateDownloadPhase.DOWNLOADING -> {
                AppSecondaryButton("取消下载", onCancel, Modifier.fillMaxWidth())
            }
            UpdateDownloadPhase.VERIFYING -> AppSecondaryButton("取消下载", onCancel, Modifier.fillMaxWidth())
            UpdateDownloadPhase.IDLE -> Unit
        }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun formatUpdateFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0)
    else -> "${bytes.coerceAtLeast(0)} B"
}

private fun formatUpdateCheckTime(timestamp: Long?): String = timestamp?.let {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
} ?: "尚未成功检查"
