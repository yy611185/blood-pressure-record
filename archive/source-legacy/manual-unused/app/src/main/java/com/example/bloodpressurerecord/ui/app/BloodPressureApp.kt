package com.example.bloodpressurerecord.ui.app

import androidx.compose.runtime.Composable
import com.example.bloodpressurerecord.navigation.BloodPressureAppRoot
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel

@Deprecated("请改用 navigation.BloodPressureAppRoot")
@Composable
fun BloodPressureApp(settingsViewModel: SettingsViewModel) {
    BloodPressureAppRoot()
}
