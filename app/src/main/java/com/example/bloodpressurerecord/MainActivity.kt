package com.example.bloodpressurerecord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bloodpressurerecord.ui.LocalAppFontScale
import com.example.bloodpressurerecord.navigation.BloodPressureAppRoot
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = AppViewModelFactory(application)
            )
            val settingsUiState by settingsViewModel.uiState.collectAsState()
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
}
