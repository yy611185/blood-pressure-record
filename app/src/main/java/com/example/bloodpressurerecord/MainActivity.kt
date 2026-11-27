package com.example.bloodpressurerecord

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.navigation.BloodPressureAppRoot
import com.example.bloodpressurerecord.reminder.MedicationReminderConfirmationRequest
import com.example.bloodpressurerecord.reminder.MedicationReminders
import com.example.bloodpressurerecord.ui.LocalAppFontScale
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import com.example.bloodpressurerecord.ui.theme.DarkColors
import com.example.bloodpressurerecord.ui.theme.WarmBackground

class MainActivity : ComponentActivity() {
    private var pendingDestination by mutableStateOf<String?>(null)
    private var medicationConfirmations by mutableStateOf(emptyList<MedicationReminderConfirmationRequest>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDestination = if (savedInstanceState == null) {
            intent?.getStringExtra(EXTRA_OPEN_DESTINATION)?.takeIf { it.isNotBlank() }
        } else {
            savedInstanceState.getString(STATE_PENDING_DESTINATION)?.takeIf { it.isNotBlank() }
        }
        medicationConfirmations = if (savedInstanceState == null) {
            listOfNotNull(MedicationReminders.parseConfirmationRequest(this, intent))
        } else {
            (savedInstanceState.getLongArray(STATE_PENDING_MEDICATIONS) ?: longArrayOf()).asList().chunked(3)
                .filter { it.size == 3 }
                .map { (id, timeId, day) -> MedicationReminderConfirmationRequest(id, timeId, day) }
        }
        intent?.removeExtra(EXTRA_OPEN_DESTINATION)
        MedicationReminders.clearConfirmationExtras(intent)
        requestHighestRefreshRate()
        val appContainer = (application as BloodPressureApplication).appContainer
        setContent {
            val startupState by appContainer.startupSettings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            if (!startupState.isReady) {
                SideEffect { enableImmersiveSystemBars(systemDark) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (systemDark) DarkColors.background else WarmBackground)
                )
                return@setContent
            }

            val appSettings = startupState.settings
            val currentDensity = LocalDensity.current
            val appFontScale = if (appSettings.largeTextEnabled) 1.15f else 1f
            // 与系统大字相乘时做合理上限钳制，避免 2.3x+ 导致极端裁切
            val effectiveFontScale = (currentDensity.fontScale * appFontScale).coerceAtMost(MAX_EFFECTIVE_FONT_SCALE)
            val darkTheme = when (appSettings.appearanceMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            SideEffect { enableImmersiveSystemBars(darkTheme) }

            BloodPressureRecordTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalAppFontScale provides appFontScale,
                    LocalDensity provides Density(
                        density = currentDensity.density,
                        fontScale = effectiveFontScale
                    )
                ) {
                    BloodPressureAppRoot(
                        showTrendChart = appSettings.showTrendChart,
                        pendingDestination = pendingDestination,
                        onConsumePendingDestination = { pendingDestination = null },
                        pendingMedicationConfirmation = medicationConfirmations.firstOrNull(),
                        onConsumePendingMedicationConfirmation = { request ->
                            if (medicationConfirmations.firstOrNull() == request) {
                                medicationConfirmations = medicationConfirmations.drop(1)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingDestination?.takeIf { it.isNotBlank() }?.let { route ->
            outState.putString(STATE_PENDING_DESTINATION, route)
        }
        outState.putLongArray(STATE_PENDING_MEDICATIONS, medicationConfirmations.flatMap {
            listOf(it.medicationId, it.timeId, it.dateEpochDay)
        }.toLongArray())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val route = intent.getStringExtra(EXTRA_OPEN_DESTINATION)?.takeIf { it.isNotBlank() }
        val medConfirm = MedicationReminders.parseConfirmationRequest(this, intent)
        intent.removeExtra(EXTRA_OPEN_DESTINATION)
        MedicationReminders.clearConfirmationExtras(intent)
        if (route != null) {
            pendingDestination = route
        }
        if (medConfirm != null && medConfirm !in medicationConfirmations) {
            medicationConfirmations = medicationConfirmations + medConfirm
        }
    }

    /**
     * 让页面背景真正延伸到三键导航和全面屏手势区域。
     */
    private fun enableImmersiveSystemBars(isDarkTheme: Boolean) {
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

    companion object {
        const val EXTRA_OPEN_DESTINATION = "open_destination"
        private const val STATE_PENDING_DESTINATION = "state_pending_destination"
        private const val STATE_PENDING_MEDICATIONS = "state_pending_medications"
        private const val MAX_EFFECTIVE_FONT_SCALE = 2.0f
    }
}
