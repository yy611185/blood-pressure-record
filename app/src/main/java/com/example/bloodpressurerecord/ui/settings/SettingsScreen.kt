package com.example.bloodpressurerecord.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.RoundIconBadge
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.Sage200
import com.example.bloodpressurerecord.ui.theme.Sage800
import com.example.bloodpressurerecord.ui.theme.Terracotta200
import com.example.bloodpressurerecord.ui.theme.Terracotta800
import com.example.bloodpressurerecord.ui.theme.WarmTextFaint

@Composable
fun SettingsScreen(
    onOpenProfile: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val topBarScroll = rememberHideOnScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(topBarScroll.nestedScrollConnection)
    ) {
        AppTopBar(title = "设置", hideOnScroll = topBarScroll)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppDimensions.pageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingListItem(
                title = "用户资料",
                subtitle = "目标血压、年龄、性别等基础信息",
                icon = Icons.Outlined.Person,
                warm = true,
                onClick = onOpenProfile
            )
            SettingListItem(
                title = "提醒设置",
                subtitle = "晨间/晚间测量提醒和提醒时间",
                icon = Icons.Outlined.Notifications,
                warm = false,
                onClick = onOpenReminder
            )
            SettingListItem(
                title = "显示设置",
                subtitle = "趋势图、高风险提醒和大字显示",
                icon = Icons.Outlined.Visibility,
                warm = true,
                onClick = onOpenDisplay
            )
            SettingListItem(
                title = "数据管理",
                subtitle = "导出 Excel、本地备份和清空数据",
                icon = Icons.Outlined.Folder,
                warm = false,
                onClick = onOpenDataManagement
            )
            SettingListItem(
                title = "应用说明与更新说明",
                subtitle = "查看应用功能、使用边界和版本变化",
                icon = Icons.Outlined.Info,
                warm = true,
                onClick = onOpenInfo
            )

            Text(
                "数据只保存在这台手机上，不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
fun SettingListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    warm: Boolean = true
) {
    DataCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 橙绿交替的 46dp 圆形图标底座
            RoundIconBadge(
                icon = icon,
                containerColor = if (warm) Terracotta200 else Sage200,
                contentColor = if (warm) Terracotta800 else Sage800
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = WarmTextFaint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
