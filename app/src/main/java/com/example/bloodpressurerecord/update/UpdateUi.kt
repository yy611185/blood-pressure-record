package com.example.bloodpressurerecord.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.bloodpressurerecord.BuildConfig
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.theme.AppMotion
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
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenDirectDownload: (() -> Unit)? = null
) {
    val manifest = state.manifest ?: return
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
                    text = if (state.required) "建议尽快更新" else "发现新版本",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("最新版本 ${manifest.version}", style = MaterialTheme.typography.titleMedium)
                    SecondaryText("当前版本 $currentVersion")
                    SecondaryText("发布时间：${formatUpdatePublishedTime(manifest.publishedAt)}")
                    if (state.required) {
                        SecondaryText("当前版本较旧，建议尽快升级。不升级也可继续离线记录血压、查看历史、使用提醒和备份。")
                    }
                }
                UpdateNotes(manifest.notes)
                SecondaryText(EXTERNAL_INSTALL_HINT)
                state.checkMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                AppPrimaryButton("前往浏览器下载更新", onDownload, Modifier.fillMaxWidth())
                AppSecondaryButton("3 天后再提醒", onSnooze, Modifier.fillMaxWidth())
                if (state.required) {
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("继续使用")
                    }
                } else {
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("跳过此版本")
                    }
                }
                ExternalDownloadLinks(onOpenReleases, onOpenDirectDownload)
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
    onDownload: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenDirectDownload: (() -> Unit)? = null
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
                val reducedMotion = LocalReducedMotion.current
                AnimatedContent(
                    targetState = state.checkState,
                    transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
                    label = "updateCheckState"
                ) { checkState ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (checkState) {
                            UpdateCheckState.IDLE -> Unit
                            UpdateCheckState.CHECKING -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                SecondaryText("正在获取最新版本信息")
                            }
                            UpdateCheckState.CURRENT -> Text("已是最新版本", style = MaterialTheme.typography.bodyMedium)
                            UpdateCheckState.AVAILABLE -> Text(
                                if (state.required) "当前版本较旧，建议尽快更新" else "有新版本可用",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            UpdateCheckState.FAILED -> Text(
                                state.checkMessage ?: "检查更新失败，请确认网络后重试。你可以继续离线使用，也可通过下方备用入口下载。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                if (state.checkState != UpdateCheckState.FAILED) {
                    state.checkMessage?.takeIf { it.isNotBlank() && it != "已是最新版本" }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        UpdatePreferenceSwitch(
            "自动检查更新", "打开应用时自动检查，成功后 24 小时内不再重复检查。",
            state.settings.autoCheckEnabled, onAutoCheckChanged
        )
        state.manifest?.let { manifest ->
            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${if (state.checkState == UpdateCheckState.FAILED) "上次获取的版本" else "最新版本"} ${manifest.version}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    SecondaryText("发布时间：${formatUpdatePublishedTime(manifest.publishedAt)}")
                    UpdateNotes(manifest.notes)
                    if (manifest.versionCode > BuildConfig.VERSION_CODE.toLong()) {
                        SecondaryText(EXTERNAL_INSTALL_HINT)
                        AppPrimaryButton("前往浏览器下载更新", onDownload, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        // The fixed Releases fallback remains available even without a valid manifest.
        ExternalDownloadLinks(onOpenReleases, onOpenDirectDownload)
        SecondaryText("检查更新会联网；下载由外部浏览器处理。健康记录、个人资料和备份文件不会上传。")
    }
}

@Composable
private fun ExternalDownloadLinks(
    onOpenReleases: () -> Unit,
    onOpenDirectDownload: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        onOpenDirectDownload?.let { openDirectDownload ->
            TextButton(onClick = openDirectDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("备用直连下载（浏览器打开）")
            }
        }
        TextButton(onClick = onOpenReleases, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("备用下载页（GitHub Releases）")
        }
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
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 12.dp),
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
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val EXTERNAL_INSTALL_HINT =
    "点击下载后将跳转浏览器下载安装包（APK），下载完成请直接打开并按提示覆盖安装。切勿先卸载旧版，以免丢失本地记录。"

private fun formatUpdatePublishedTime(timestamp: String): String = try {
    formatUpdateInstant(Instant.parse(timestamp))
} catch (_: java.time.DateTimeException) {
    "未知"
}

private fun formatUpdateCheckTime(timestamp: Long?): String = timestamp?.let {
    formatUpdateInstant(Instant.ofEpochMilli(it))
} ?: "尚未成功检查"

private fun formatUpdateInstant(instant: Instant): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(instant)
