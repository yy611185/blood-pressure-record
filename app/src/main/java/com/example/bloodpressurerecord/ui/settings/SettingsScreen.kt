package com.example.bloodpressurerecord.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.RoundIconBadge
import com.example.bloodpressurerecord.ui.common.SegmentedPillGroup
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.home.Buddy

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenProfile: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkEnabled = when (state.appearanceMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(title = "我的")
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimensions.pageHorizontalPadding)
                .padding(
                    top = statusBarTopPadding(),
                    bottom = dockContentBottomPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onOpenProfile).heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Buddy(category = "正常", modifier = Modifier.size(54.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.name.ifBlank { "我的资料" }, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            val details = listOfNotNull(
                                state.ageText.takeIf { it.isNotBlank() }?.let { "$it 岁" },
                                state.gender.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            Text(details.ifBlank { "设置年龄、性别和目标值" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "编辑资料",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetTile("目标高压", state.targetSystolicText.ifBlank { "—" }, Modifier.weight(1f))
                        TargetTile("目标低压", state.targetDiastolicText.ifBlank { "—" }, Modifier.weight(1f))
                    }
                    Text("平均值怎么算", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                    SegmentedPillGroup(
                        options = listOf("全部组平均", "不计第一组"),
                        selectedIndex = if (state.discardFirstReading) 1 else 0,
                        onSelect = { index -> viewModel.setDiscardFirstReading(index == 1) }
                    )
                    Text("只影响之后的新记录；高风险判断始终检查每组读数。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingsSectionTitle("提醒")
            SettingsGroup {
                SettingRow("提醒中心", "早晚测量、服药和日历同步", Icons.Outlined.Notifications, onOpenReminder)
            }
            SettingsSectionTitle("显示")
            SettingsGroup {
                SettingSwitchRow("大字模式", "字号放大，按钮更好按", Icons.Outlined.Visibility,
                    state.isLargeTextEnabled, viewModel::setLargeTextEnabled)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow("深色模式", if (state.appearanceMode == "system") "跟随系统" else "手动设置",
                    Icons.Outlined.DarkMode, darkEnabled) {
                    viewModel.setAppearanceMode(if (it) "dark" else "light")
                }
                if (state.appearanceMode != "system") {
                    TextButton(onClick = { viewModel.setAppearanceMode("system") },
                        modifier = Modifier.fillMaxWidth()) { Text("恢复跟随系统") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow("显示小压", "首页小伙伴会跟着血压变表情", Icons.Outlined.FavoriteBorder,
                    state.showBuddy, viewModel::setShowBuddy)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow("显示设置", "趋势图和高风险提醒", Icons.Outlined.Visibility, onOpenDisplay)
            }
            SettingsSectionTitle("数据")
            SettingsGroup {
                SettingRow("导出、加密备份与导入", "数据保存在本机", Icons.Outlined.Folder,
                    onOpenDataManagement)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow("关于与更新说明", "应用功能和版本变化", Icons.Outlined.Info, onOpenInfo)
            }
            Surface(shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Shield, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column {
                        Text("数据只在这台手机上。", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("不注册、不上传。卸载或换机前请先导出备份。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (value == "—") value else "< $value",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(start = 2.dp, top = 12.dp),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RoundIconBadge(icon, MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String, subtitle: String, icon: ImageVector, checked: Boolean, onChecked: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
        .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RoundIconBadge(icon, MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun SettingListItem(
    title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, warm: Boolean = true
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) { SettingRow(title, subtitle, icon, onClick) }
}
