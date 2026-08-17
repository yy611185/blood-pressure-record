package com.example.bloodpressurerecord.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.bloodpressurerecord.ui.scan.ScanCameraScreen
import com.example.bloodpressurerecord.ui.scan.ScanReviewScreen
import com.example.bloodpressurerecord.ui.scan.ScanViewModel
import com.example.bloodpressurerecord.ui.settings.SettingsScreen
import com.example.bloodpressurerecord.ui.settings.SettingsDataManagementScreen
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.ui.settings.SettingsProfileScreen
import com.example.bloodpressurerecord.ui.settings.SettingsReminderScreen
import com.example.bloodpressurerecord.ui.settings.SettingsDisplayScreen
import com.example.bloodpressurerecord.ui.settings.SettingsAppGuideScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoReleaseNotesScreen
import java.time.LocalDate
import kotlinx.coroutines.flow.map

private val TopLevelRoutes = setOf(
    AppDestination.Measure.route,
    AppDestination.History.route,
    AppDestination.Trend.route,
    AppDestination.Settings.route
)

/** Material 3 emphasized decelerate：进入/落位动画的标准减速曲线。 */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/** 本次转场是否为底部 Tab 平级切换（两端都是顶级页面）。 */
private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.isTabSwitch(): Boolean {
    return initialState.destination.route in TopLevelRoutes &&
        targetState.destination.route in TopLevelRoutes
}

@Composable
fun BloodPressureAppRoot(showTrendChart: Boolean = true) {
    val navController = rememberNavController()
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    val application = LocalContext.current.applicationContext as BloodPressureApplication
    val factory = remember(application) { AppViewModelFactory(application) }
    val tabs = remember(showTrendChart) {
        listOfNotNull(
            AppDestination.Measure,
            AppDestination.History,
            AppDestination.Trend.takeIf { showTrendChart },
            AppDestination.Settings
        )
    }
    val showBottomBar = current in TopLevelRoutes

    LaunchedEffect(showTrendChart, current) {
        if (!showTrendChart && current == AppDestination.Trend.route) {
            navController.navigate(AppDestination.Measure.route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                WarmBottomNavBar(
                    tabs = tabs,
                    currentRoute = current,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Measure.route,
            modifier = Modifier.padding(innerPadding),
            // 平级 Tab：轻量淡入淡出（无位移，避免两页同时做滑动合成掉帧）。
            // 父子层级：容器宽度滑动 + 减速曲线；Navigation 2.8 下 pop 转场
            // 自动接入 Android 13+ 预测式返回手势的进度。
            enterTransition = {
                if (isTabSwitch()) {
                    fadeIn(tween(durationMillis = 210, delayMillis = 60, easing = LinearOutSlowInEasing))
                } else {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(durationMillis = 350, easing = EmphasizedDecelerate)
                    ) + fadeIn(tween(durationMillis = 200, easing = LinearOutSlowInEasing))
                }
            },
            exitTransition = {
                if (isTabSwitch()) {
                    fadeOut(tween(durationMillis = 90, easing = FastOutLinearInEasing))
                } else {
                    // 被覆盖的父页轻微左移并淡出，形成层级纵深感。
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(durationMillis = 350, easing = EmphasizedDecelerate),
                        targetOffset = { it / 5 }
                    ) + fadeOut(tween(durationMillis = 200, easing = FastOutLinearInEasing))
                }
            },
            popEnterTransition = {
                if (isTabSwitch()) {
                    fadeIn(tween(durationMillis = 210, delayMillis = 60, easing = LinearOutSlowInEasing))
                } else {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(durationMillis = 300, easing = EmphasizedDecelerate),
                        initialOffset = { it / 5 }
                    ) + fadeIn(tween(durationMillis = 180, easing = LinearOutSlowInEasing))
                }
            },
            popExitTransition = {
                if (isTabSwitch()) {
                    fadeOut(tween(durationMillis = 90, easing = FastOutLinearInEasing))
                } else {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(durationMillis = 300, easing = EmphasizedDecelerate)
                    ) + fadeOut(tween(durationMillis = 220, easing = FastOutLinearInEasing))
                }
            }
        ) {
            composable(AppDestination.Measure.route) {
                val dashboardVm: DashboardViewModel = viewModel(factory = factory)
                DashboardScreen(
                    viewModel = dashboardVm,
                    onAddMeasurement = { navController.navigate(AppDestination.AddMeasurement.route) },
                    onScanMeasurement = { navController.navigate(AppDestination.ScanCamera.route) },
                    onOpenMedicationSettings = {
                        navController.navigate(AppDestination.SettingsReminder.route)
                    },
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
            composable(AppDestination.ScanCamera.route) {
                val scanVm: ScanViewModel = viewModel(factory = factory)
                ScanCameraScreen(
                    viewModel = scanVm,
                    onBack = { navController.popBackStack() },
                    onEnterReview = { navController.navigate(AppDestination.ScanReview.route) },
                    onManualEntry = {
                        navController.navigate(AppDestination.AddMeasurement.route) {
                            popUpTo(AppDestination.ScanCamera.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(AppDestination.ScanReview.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    runCatching { navController.getBackStackEntry(AppDestination.ScanCamera.route) }
                        .getOrNull()
                }
                if (parentEntry == null) {
                    LaunchedEffect(Unit) {
                        navController.navigate(AppDestination.ScanCamera.route) {
                            popUpTo(AppDestination.ScanReview.route) { inclusive = true }
                        }
                    }
                } else {
                    val scanVm: ScanViewModel = viewModel(parentEntry, factory = factory)
                    ScanReviewScreen(
                        viewModel = scanVm,
                        onBack = { navController.popBackStack() },
                        onSaved = {
                            navController.popBackStack(AppDestination.ScanCamera.route, inclusive = true)
                        }
                    )
                }
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
                val editFactory = remember(sessionId) {
                    EditSessionViewModel.provideFactory(
                        sessionId = sessionId,
                        repository = application.appContainer.bloodPressureRepository,
                        discardFirstReading = application.appContainer.settingsRepository
                            .observeSettings()
                            .map { it.appSettings.discardFirstReading }
                    )
                }
                val vm: EditSessionViewModel = viewModel(factory = editFactory)
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
        }
    }
}

/** 暖阳设计的浮动药丸底部导航条。 */
@Composable
private fun WarmBottomNavBar(
    tabs: List<AppDestination>,
    currentRoute: String?,
    onSelect: (AppDestination) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(4.dp, MaterialTheme.shapes.large, clip = false)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEach { destination ->
            val selected = currentRoute == destination.route
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(destination) }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    destination.icon,
                    contentDescription = destination.label,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    destination.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
