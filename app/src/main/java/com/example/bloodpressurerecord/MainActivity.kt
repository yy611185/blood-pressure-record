package com.example.bloodpressurerecord

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bloodpressurerecord.ui.LocalAppFontScale
import com.example.bloodpressurerecord.navigation.BloodPressureAppRoot
import com.example.bloodpressurerecord.ui.scan.ScanCameraActive
import com.example.bloodpressurerecord.ui.scan.ScanVolumeKeyBus
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveSystemBars()
        requestHighestRefreshRate()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = AppViewModelFactory(application)
            )
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val currentDensity = LocalDensity.current
            val appFontScale = if (settingsUiState.isLargeTextEnabled) 1.15f else 1f

            BloodPressureRecordTheme {
                CompositionLocalProvider(
                    LocalAppFontScale provides appFontScale,
                    LocalDensity provides Density(
                        density = currentDensity.density,
                        fontScale = currentDensity.fontScale * appFontScale
                    )
                ) {
                    BloodPressureAppRoot(showTrendChart = settingsUiState.showTrendChart)
                }
            }
        }
    }

    /**
     * 让页面背景真正延伸到三键导航和全面屏手势区域。
     *
     * 不能使用 SystemBarStyle.auto：Android 10+ 会为透明三键导航强制叠加
     * 黑/白对比底，正是截图中底部突兀色块的来源。这里按当前主题显式选择
     * 图标明暗，并关闭系统对比层。
     */
    private fun enableImmersiveSystemBars() {
        val isDarkTheme =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val transparent = Color.TRANSPARENT
        val style = if (isDarkTheme) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
        enableEdgeToEdge(
            statusBarStyle = style,
            navigationBarStyle = style
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    /**
     * 申请当前分辨率下最高刷新率的显示模式。
     * 部分厂商 ROM 默认把第三方应用锁在 60Hz，主动声明后动画才能吃满高刷。
     * 系统仍可按温控/省电策略降频，这里只是表达偏好。
     */
    private fun requestHighestRefreshRate() {
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val currentMode = display?.mode ?: return
        val bestMode = display.supportedModes
            .filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: return
        if (bestMode.modeId != currentMode.modeId) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = bestMode.modeId
            }
        }
    }

    /** 拍照识别取景页激活时，把音量键转成快门；其余情况交给系统。 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (ScanCameraActive.isActive && isVolumeKey) {
            if (event.repeatCount == 0) {
                ScanVolumeKeyBus.emitShutter()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        return if (ScanCameraActive.isActive && isVolumeKey) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }
    }
}
