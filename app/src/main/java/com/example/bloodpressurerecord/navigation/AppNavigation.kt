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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.bloodpressurerecord.ui.history.HistoryScreen
import com.example.bloodpressurerecord.ui.history.HistoryDetailScreen
import com.example.bloodpressurerecord.ui.history.HistoryViewModel
import com.example.bloodpressurerecord.ui.history.TrendViewModel
import com.example.bloodpressurerecord.ui.home.DashboardScreen
import com.example.bloodpressurerecord.ui.home.DashboardViewModel
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.record.AddMeasurementScreen
import com.example.bloodpressurerecord.ui.settings.SettingsScreen
import com.example.bloodpressurerecord.ui.settings.SettingsDataManagementScreen
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.ui.settings.SettingsProfileScreen
import com.example.bloodpressurerecord.ui.settings.SettingsReminderScreen
import com.example.bloodpressurerecord.ui.settings.SettingsDisplayScreen
import com.example.bloodpressurerecord.ui.settings.SettingsAppGuideScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoMeasurementTipsScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoReleaseNotesScreen
import com.example.bloodpressurerecord.ui.settings.SettingsDisclaimerScreen
import java.time.LocalDate

@Composable
fun BloodPressureAppRoot(showTrendChart: Boolean = true) {
    val navController = rememberNavController()
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    val application = LocalContext.current.applicationContext as BloodPressureApplication
    val factory = AppViewModelFactory(application)
    val tabs = listOfNotNull(
        AppDestination.Measure,
        AppDestination.History,
        AppDestination.Trend.takeIf { showTrendChart },
        AppDestination.Settings
    )
    val topLevelRoutes = setOf(
        AppDestination.Measure.route,
        AppDestination.History.route,
        AppDestination.Trend.route,
        AppDestination.Settings.route
    )
    val showBottomBar = current in topLevelRoutes

    LaunchedEffect(showTrendChart, current) {
        if (!showTrendChart && current == AppDestination.Trend.route) {
            navController.navigate(AppDestination.Measure.route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

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
                val dashboardVm: DashboardViewModel = viewModel(factory = factory)
                DashboardScreen(
                    viewModel = dashboardVm,
                    onAddMeasurement = { navController.navigate(AppDestination.AddMeasurement.route) },
                    onViewTodayRecords = {
                        navController.navigate(AppDestination.History.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        navController.currentBackStackEntry?.savedStateHandle?.set(
                            "history_open_date",
                            LocalDate.now().toString()
                        )
                    }
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
            composable(AppDestination.History.route) { backStack ->
                val historyVm: HistoryViewModel = viewModel(factory = factory)
                val requestedDate = backStack.savedStateHandle.get<String>("history_open_date")
                LaunchedEffect(requestedDate) {
                    requestedDate?.let {
                        runCatching { LocalDate.parse(it) }.getOrNull()
                            ?.let(historyVm::openDateWhenAvailable)
                        backStack.savedStateHandle.remove<String>("history_open_date")
                    }
                }
                HistoryScreen(
                    viewModel = historyVm,
                    onAddMeasurement = { navController.navigate(AppDestination.AddMeasurement.route) },
                    onOpenDetail = { sessionId ->
                        navController.navigate(AppDestination.HistoryDetail.route(sessionId))
                    }
                )
            }
            composable(AppDestination.Trend.route) {
                val vm: TrendViewModel = viewModel(factory = factory)
                com.example.bloodpressurerecord.ui.history.TrendScreen(
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
                HistoryDetailScreen(
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
                SettingsScreen(
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
                SettingsDataManagementScreen(viewModel = vm, onBack = { navController.popBackStack() })
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
