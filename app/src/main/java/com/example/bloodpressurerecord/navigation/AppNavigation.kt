package com.example.bloodpressurerecord.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bloodpressurerecord.AppViewModelFactory
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.ui.history.EditSessionScreen
import com.example.bloodpressurerecord.ui.history.EditSessionViewModel
import com.example.bloodpressurerecord.ui.history.HistoryDetailViewModel
import com.example.bloodpressurerecord.ui.history.HistoryScreenRevamp
import com.example.bloodpressurerecord.ui.history.HistoryDetailScreenRevamp
import com.example.bloodpressurerecord.ui.history.HistoryViewModel
import com.example.bloodpressurerecord.ui.home.DashboardScreen
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.record.AddMeasurementScreen
import com.example.bloodpressurerecord.ui.settings.SettingsScreenRevamp
import com.example.bloodpressurerecord.ui.settings.SettingsDataManagementScreenRevamp
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.ui.settings.SettingsProfileScreen
import com.example.bloodpressurerecord.ui.settings.SettingsReminderScreen
import com.example.bloodpressurerecord.ui.settings.SettingsDisplayScreen
import com.example.bloodpressurerecord.ui.settings.SettingsAppGuideScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoMeasurementTipsScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoReleaseNotesScreen
import com.example.bloodpressurerecord.ui.settings.SettingsDisclaimerScreen

@Composable
fun BloodPressureAppRoot() {
    val navController = rememberNavController()
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    val application = LocalContext.current.applicationContext as BloodPressureApplication
    val factory = AppViewModelFactory(application)
    val tabs = listOf(
        AppDestination.Measure,
        AppDestination.History,
        AppDestination.Trend,
        AppDestination.Settings
    )
    val topLevelRoutes = setOf(
        AppDestination.Measure.route,
        AppDestination.History.route,
        AppDestination.Trend.route,
        AppDestination.Settings.route
    )
    val showBottomBar = current in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { destination ->
                        val selected = current == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Measure.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 120, easing = LinearEasing)) +
                    slideInHorizontally(animationSpec = tween(durationMillis = 180, easing = LinearEasing)) { it / 12 }
            },
            exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 90, easing = LinearEasing)) +
                    slideOutHorizontally(animationSpec = tween(durationMillis = 160, easing = LinearEasing)) { -it / 16 }
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 120, easing = LinearEasing)) +
                    slideInHorizontally(animationSpec = tween(durationMillis = 180, easing = LinearEasing)) { -it / 12 }
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 90, easing = LinearEasing)) +
                    slideOutHorizontally(animationSpec = tween(durationMillis = 160, easing = LinearEasing)) { it / 16 }
            }
        ) {
            composable(AppDestination.Measure.route) {
                val historyVm: HistoryViewModel = viewModel(factory = factory)
                DashboardScreen(
                    historyViewModel = historyVm,
                    onAddMeasurement = { navController.navigate(AppDestination.AddMeasurement.route) }
                )
            }
            composable(AppDestination.AddMeasurement.route) {
                val homeVm: HomeViewModel = viewModel(factory = factory)
                AddMeasurementScreen(
                    viewModel = homeVm,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(AppDestination.History.route) {
                val historyVm: HistoryViewModel = viewModel(factory = factory)
                HistoryScreenRevamp(
                    viewModel = historyVm,
                    onAddMeasurement = { navController.navigate(AppDestination.AddMeasurement.route) },
                    onOpenDetail = { sessionId ->
                        navController.navigate(AppDestination.HistoryDetail.route(sessionId))
                    }
                )
            }
            composable(AppDestination.Trend.route) {
                val vm: HistoryViewModel = viewModel(factory = factory)
                com.example.bloodpressurerecord.ui.history.DoubleLineChartScreenZh(
                    viewModel = vm,
                    onBack = null
                )
            }
            composable(AppDestination.HistoryDetail.route) { backStack ->
                val sessionId = backStack.arguments?.getString("sessionId").orEmpty()
                val vm: HistoryDetailViewModel = viewModel(
                    factory = HistoryDetailViewModel.provideFactory(
                        sessionId = sessionId,
                        repository = application.appContainer.bloodPressureRepository
                    )
                )
                HistoryDetailScreenRevamp(
                    viewModel = vm,
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(AppDestination.HistoryEdit.route(id)) }
                )
            }
            composable(AppDestination.HistoryEdit.route) { backStack ->
                val sessionId = backStack.arguments?.getString("sessionId").orEmpty()
                val vm: EditSessionViewModel = viewModel(
                    factory = EditSessionViewModel.provideFactory(
                        sessionId = sessionId,
                        repository = application.appContainer.bloodPressureRepository
                    )
                )
                EditSessionScreen(
                    viewModel = vm,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppDestination.Settings.route) {
                SettingsScreenRevamp(
                    onOpenProfile = { navController.navigate(AppDestination.SettingsProfile.route) },
                    onOpenReminder = { navController.navigate(AppDestination.SettingsReminder.route) },
                    onOpenDisplay = { navController.navigate(AppDestination.SettingsDisplay.route) },
                    onOpenDataManagement = { navController.navigate(AppDestination.SettingsDataManagement.route) },
                    onOpenInfo = { navController.navigate(AppDestination.SettingsInfo.route) }
                )
            }
            composable(AppDestination.SettingsProfile.route) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsProfileScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(AppDestination.SettingsReminder.route) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsReminderScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(AppDestination.SettingsDisplay.route) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsDisplayScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(AppDestination.SettingsDataManagement.route) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsDataManagementScreenRevamp(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(AppDestination.SettingsInfo.route) {
                SettingsInfoScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAppGuide = { navController.navigate(AppDestination.SettingsInfoAppGuide.route) },
                    onOpenReleaseNotes = { navController.navigate(AppDestination.SettingsInfoReleaseNotes.route) }
                )
            }
            composable(AppDestination.SettingsInfoAppGuide.route) {
                SettingsAppGuideScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestination.SettingsInfoReleaseNotes.route) {
                SettingsInfoReleaseNotesScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestination.SettingsInfoMeasurementTips.route) {
                SettingsInfoMeasurementTipsScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestination.SettingsDisclaimer.route) {
                SettingsDisclaimerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
