package com.example.bloodpressurerecord.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.theme.AppDimensions

// calculateTopPadding() / calculateBottomPadding() 是 PaddingValues 的接口成员，
// 不是扩展函数，因此不需要（也不能）单独 import。

/**
 * 全应用页面的系统栏安全区约定（唯一入口，页面不要再自己拼 padding）。
 *
 * 根 Scaffold 已把 `contentWindowInsets` 归零，因此：
 * - 顶部：每个页面自己取 [statusBarTopPadding]；
 * - 底部：按页面类型取 [dockContentBottomPadding] 或 [pageContentBottomPadding]，
 *   两者都**已经包含**导航栏安全区，页面不要再额外加 `navigationBarsPadding()`。
 */

/**
 * 状态栏占位（已包含页面自身的常规顶部间距）。
 *
 * 取 `safeDrawing` 的顶部而不是只取 `statusBars`：前者同时覆盖状态栏与刘海/挖孔
 * 的 display cutout，在 Android 15 强制 edge-to-edge 的机型上也能给出非零值，
 * 因此标题不会被状态栏压住——且全程由系统 inset 推导，没有任何机型硬编码高度。
 */
@Composable
fun statusBarTopPadding(extra: Dp = 0.dp): Dp =
    WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + extra

/** 导航栏安全区高度，用于自定义贴底元素（如 Snackbar、Dock）。 */
@Composable
fun navigationBarHeight(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * 顶级页面（首页 / 历史 / 趋势 / 我的）内容的底部留白：
 * 导航栏安全区 + 悬浮 Dock 胶囊高度 + Dock 下边距 + 视觉收尾间距。
 *
 * 用 [asPaddingValues] 而不是 `navigationBarsPadding()`，因为这里需要的是
 * 可参与算术的**数值**（也便于直接作为 LazyColumn 的 contentPadding）。
 */
@Composable
fun dockContentBottomPadding(): Dp =
    navigationBarHeight() +
        AppDimensions.dockCapsuleHeight +
        AppDimensions.dockBottomMargin +
        AppDimensions.dockContentGap

/** 普通子页面内容的底部留白：导航栏安全区 + 视觉收尾间距。 */
@Composable
fun pageContentBottomPadding(): Dp =
    navigationBarHeight() + AppDimensions.pageBottomGap
