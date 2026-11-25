package com.example.bloodpressurerecord.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bloodpressurerecord.AppViewModelFactory
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.BuildConfig
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppBottomDock
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
import com.example.bloodpressurerecord.ui.common.pageContentBottomPadding
import com.example.bloodpressurerecord.ui.common.rememberDockScrollState
import com.example.bloodpressurerecord.ui.history.EditSessionScreen
import com.example.bloodpressurerecord.ui.history.EditSessionViewModel
import com.example.bloodpressurerecord.ui.history.HistoryDetailScreen
import com.example.bloodpressurerecord.ui.history.HistoryDetailViewModel
import com.example.bloodpressurerecord.ui.history.HistoryScreen
import com.example.bloodpressurerecord.ui.history.HistoryViewModel
import com.example.bloodpressurerecord.ui.history.NoteSearchScreen
import com.example.bloodpressurerecord.ui.history.NoteSearchViewModel
import com.example.bloodpressurerecord.ui.history.TrendViewModel
import com.example.bloodpressurerecord.ui.home.DashboardScreen
import com.example.bloodpressurerecord.ui.home.DashboardViewModel
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.record.AddMeasurementScreen
import com.example.bloodpressurerecord.ui.report.ReportScreen
import com.example.bloodpressurerecord.ui.report.ReportViewModel
import com.example.bloodpressurerecord.ui.settings.SettingsAppGuideScreen
import com.example.bloodpressurerecord.ui.settings.SettingsDataManagementScreen
import com.example.bloodpressurerecord.ui.settings.SettingsDisplayScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoReleaseNotesScreen
import com.example.bloodpressurerecord.ui.settings.SettingsInfoScreen
import com.example.bloodpressurerecord.ui.settings.SettingsProfileScreen
import com.example.bloodpressurerecord.ui.settings.SettingsReminderScreen
import com.example.bloodpressurerecord.ui.settings.SettingsScreen
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.update.UpdateDialog
import com.example.bloodpressurerecord.update.UpdateViewModel
import com.example.bloodpressurerecord.update.rememberUpdatePlatformActions
import java.time.LocalDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val TopLevelRoutes = setOf(
    AppDestination.Measure.route,
    AppDestination.History.route,
    AppDestination.Trend.route,
    AppDestination.Settings.route
)

/** 本次转场是否为底部 Tab 平级切换（两端都是顶级页面）。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    return initialState.destination.route in TopLevelRoutes &&
        targetState.destination.route in TopLevelRoutes
}

@Composable
fun BloodPressureAppRoot(
    showTrendChart: Boolean = true,
    pendingDestination: String? = null,
    onConsumePendingDestination: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val current = currentEntry?.destination?.route
    val application = LocalContext.current.applicationContext as BloodPressureApplication
    val factory = remember(application) { AppViewModelFactory(application) }
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val trendEntry = currentEntry?.takeIf { it.destination.route == AppDestination.Trend.route }
    val trendVm = remember(trendEntry, factory) {
        trendEntry?.let { ViewModelProvider(it, factory)[TrendViewModel::class.java] }
    }
    val trendFullscreen = trendVm?.fullscreen?.collectAsStateWithLifecycle()?.value == true
    val updateVm: UpdateViewModel = viewModel(factory = factory)
    val updateState by updateVm.uiState.collectAsStateWithLifecycle()
    val dataActionBusy by application.appContainer.updatePromptGate.busy.collectAsStateWithLifecycle()
    val undoManager = application.appContainer.deletionUndoManager
    val currentUndoPrompt by undoManager.currentPrompt.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var updateForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val safeUpdateRoute = current in setOf(
        AppDestination.Measure.route,
        AppDestination.History.route,
        AppDestination.Trend.route,
        AppDestination.SettingsInfo.route,
        AppDestination.SettingsInfoReleaseNotes.route
    ) && !trendFullscreen
    val updatePromptAllowed = updateForeground && safeUpdateRoute && !dataActionBusy && pendingDestination == null
    val snackbar = remember { SnackbarHostState() }
    val undoSnackbar = remember { SnackbarHostState() }
    val updateActions = rememberUpdatePlatformActions(updateVm, snackbar)

    fun popIfCurrent(entry: NavBackStackEntry) {
        if (navController.currentBackStackEntry == entry) {
            navController.popBackStack()
        }
    }

    fun navigateFromEntry(entry: NavBackStackEntry, route: String) {
        if (navController.currentBackStackEntry == entry &&
            navController.currentDestination?.route != route
        ) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    val openAddMeasurement: () -> Unit = {
        if (navController.currentDestination?.route != AppDestination.AddMeasurement.route) {
            navController.navigate(AppDestination.AddMeasurement.route) {
                launchSingleTop = true
            }
        }
    }

    DisposableEffect(lifecycleOwner, updateVm) {
        updateVm.onForegroundChanged(updateForeground)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    updateForeground = true
                    updateVm.onForegroundChanged(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    updateForeground = false
                    updateVm.setPromptAllowed(false)
                    updateVm.onForegroundChanged(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            updateVm.setPromptAllowed(false)
            updateVm.onForegroundChanged(false)
        }
    }
    LaunchedEffect(updatePromptAllowed) { updateVm.setPromptAllowed(updatePromptAllowed) }

    // 在顶级页面打开外部录入请求，避免覆盖正在进行的子页面操作。
    LaunchedEffect(pendingDestination, current, trendFullscreen, dataActionBusy) {
        val destination = pendingDestination ?: return@LaunchedEffect
        val liveRoute = navController.currentDestination?.route ?: return@LaunchedEffect
        if (destination != AppDestination.AddMeasurement.route || liveRoute == destination) {
            onConsumePendingDestination()
            return@LaunchedEffect
        }
        if (liveRoute != current || liveRoute !in TopLevelRoutes || trendVm?.fullscreen?.value == true ||
            application.appContainer.updatePromptGate.busy.value
        ) return@LaunchedEffect
        navController.navigate(destination) {
            launchSingleTop = true
        }
        onConsumePendingDestination()
    }

    // 全局删除撤销提示与恢复结果反馈（独立于单页 ViewModel 生命周期）。
    LaunchedEffect(currentUndoPrompt?.tokenId) {
        val prompt = currentUndoPrompt ?: return@LaunchedEffect
        val result = undoSnackbar.showSnackbar(
            message = prompt.message,
            actionLabel = "撤销",
            duration = SnackbarDuration.Long
        )
        when (result) {
            SnackbarResult.ActionPerformed -> undoManager.undoAsync(prompt.tokenId)
            SnackbarResult.Dismissed -> undoManager.onPromptDismissedAsync(prompt.tokenId)
        }
    }
    LaunchedEffect(undoManager) {
        undoManager.feedback.collect { feedback ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                message = feedback.message,
                duration = if (feedback.isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    val tabs = remember(showTrendChart) {
        listOfNotNull(
            AppDestination.Measure,
            AppDestination.History,
            AppDestination.Trend.takeIf { showTrendChart },
            AppDestination.Settings
        )
    }
    val showBottomBar = current in TopLevelRoutes &&
        !(current == AppDestination.Trend.route && trendFullscreen)
    val dockScrollState = rememberDockScrollState(current)
    val snackbarBottomPadding = if (showBottomBar) {
        dockContentBottomPadding()
    } else {
        maxOf(
            pageContentBottomPadding(),
            WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        )
    }

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
        snackbarHost = {
            Column(Modifier.padding(bottom = snackbarBottomPadding)) {
                SnackbarHost(snackbar)
                SnackbarHost(undoSnackbar)
            }
        },
        // 系统栏 inset 全部交给页面自己处理（见 ui/common/AppInsets.kt）：
        // 根布局只做容器，避免出现「innerPadding + 页面 navigationBarsPadding」的重复叠加。
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = AppDestination.Measure.route,
                modifier = Modifier.fillMaxSize().then(
                    if (showBottomBar) Modifier.nestedScroll(dockScrollState.nestedScrollConnection) else Modifier
                ),
                enterTransition = {
                    if (reducedMotion) {
                        EnterTransition.None
                    } else if (isTabSwitch()) {
                        fadeIn(tween(durationMillis = 190, delayMillis = 40, easing = AppMotion.SmoothOutEasing))
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(durationMillis = 320, easing = AppMotion.SmoothOutEasing)
                        ) + fadeIn(tween(durationMillis = 190, easing = AppMotion.SmoothOutEasing))
                    }
                },
                exitTransition = {
                    if (reducedMotion) {
                        ExitTransition.None
                    } else if (isTabSwitch()) {
                        fadeOut(tween(durationMillis = 90, easing = AppMotion.SmoothInEasing))
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(durationMillis = 320, easing = AppMotion.SmoothOutEasing),
                            targetOffset = { it / 5 }
                        ) + fadeOut(tween(durationMillis = 180, easing = AppMotion.SmoothInEasing))
                    }
                },
                popEnterTransition = {
                    if (reducedMotion) {
                        EnterTransition.None
                    } else if (isTabSwitch()) {
                        fadeIn(tween(durationMillis = 190, delayMillis = 40, easing = AppMotion.SmoothOutEasing))
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(durationMillis = 280, easing = AppMotion.SmoothOutEasing),
                            initialOffset = { it / 5 }
                        ) + fadeIn(tween(durationMillis = 170, easing = AppMotion.SmoothOutEasing))
                    }
                },
                popExitTransition = {
                    if (reducedMotion) {
                        ExitTransition.None
                    } else if (isTabSwitch()) {
                        fadeOut(tween(durationMillis = 90, easing = AppMotion.SmoothInEasing))
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(durationMillis = 280, easing = AppMotion.SmoothOutEasing)
                        ) + fadeOut(tween(durationMillis = 200, easing = AppMotion.SmoothInEasing))
                    }
                }
            ) {
                composable(AppDestination.Measure.route) { backStack ->
                    val dashboardVm: DashboardViewModel = viewModel(factory = factory)
                    val openHistoryDate: (LocalDate) -> Unit = { targetDate ->
                        if (navController.currentBackStackEntry == backStack) {
                            navController.navigate(AppDestination.History.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                "history_open_date",
                                targetDate.toString()
                            )
                        }
                    }
                    DashboardScreen(
                        viewModel = dashboardVm,
                        onAddMeasurement = openAddMeasurement,
                        onOpenMedicationSettings = {
                            navigateFromEntry(backStack, AppDestination.SettingsReminder.route)
                        },
                        onOpenTrend = {
                            if (showTrendChart && navController.currentBackStackEntry == backStack) {
                                navController.navigate(AppDestination.Trend.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onViewTodayRecords = { openHistoryDate(LocalDate.now()) },
                        onViewDateRecords = openHistoryDate,
                        onOpenSessionDetail = { sessionId ->
                            if (sessionId.isNotBlank()) {
                                navigateFromEntry(backStack, AppDestination.HistoryDetail.route(sessionId))
                            }
                        }
                    )
                }
                composable(
                    route = AppDestination.AddMeasurement.route,
                    enterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(durationMillis = 320, easing = AppMotion.SmoothOutEasing)
                            ) + fadeIn(tween(durationMillis = 200, easing = AppMotion.SmoothOutEasing))
                        }
                    },
                    exitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = tween(durationMillis = 260, easing = AppMotion.SmoothInEasing)
                            ) + fadeOut(tween(durationMillis = 180, easing = AppMotion.SmoothInEasing))
                        }
                    },
                    popEnterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            fadeIn(tween(durationMillis = 180, easing = AppMotion.SmoothOutEasing))
                        }
                    },
                    popExitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = tween(durationMillis = 260, easing = AppMotion.SmoothInEasing)
                            ) + fadeOut(tween(durationMillis = 180, easing = AppMotion.SmoothInEasing))
                        }
                    }
                ) { backStack ->
                    val homeVm: HomeViewModel = viewModel(factory = factory)
                    AddMeasurementScreen(
                        viewModel = homeVm,
                        canConsumeSave = currentEntry == backStack,
                        isCurrentEntry = { navController.currentBackStackEntry == backStack },
                        onBack = { popIfCurrent(backStack) },
                        onSaved = {
                            if (navController.currentBackStackEntry == backStack) {
                                val previousRoute = navController.previousBackStackEntry?.destination?.route
                                if (previousRoute == null || previousRoute in TopLevelRoutes) {
                                    navController.navigate(AppDestination.Measure.route) {
                                        popUpTo(navController.graph.findStartDestination().id)
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.popBackStack()
                                }
                            }
                        },
                        onSavedFeedback = { sessionId, avgSys, avgDia, categoryLabel ->
                            val validId = sessionId.takeIf { it.isNotBlank() }
                            val bpText = if (avgSys != null && avgDia != null) {
                                "$avgSys/$avgDia mmHg（$categoryLabel）"
                            } else {
                                "血压记录"
                            }
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val result = snackbar.showSnackbar(
                                    message = "已保存 $bpText",
                                    actionLabel = if (validId != null) "再改改" else null,
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed && validId != null) {
                                    val targetRoute = AppDestination.HistoryEdit.route(validId)
                                     if (navController.currentDestination?.route in TopLevelRoutes) {
                                        navController.navigate(targetRoute) {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        }
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
                        onAddMeasurement = openAddMeasurement,
                        onOpenSearch = {
                            navigateFromEntry(backStack, AppDestination.NoteSearch.route)
                        },
                        onOpenDetail = { sessionId ->
                            if (sessionId.isNotBlank()) {
                                navigateFromEntry(backStack, AppDestination.HistoryDetail.route(sessionId))
                            }
                        }
                    )
                }
                composable(AppDestination.NoteSearch.route) { backStack ->
                    val searchVm: NoteSearchViewModel = viewModel(factory = factory)
                    NoteSearchScreen(
                        viewModel = searchVm,
                        onBack = { popIfCurrent(backStack) },
                        onOpenSession = { sessionId ->
                            if (sessionId.isNotBlank()) {
                                navigateFromEntry(backStack, AppDestination.HistoryLocation.route(sessionId))
                            }
                        }
                    )
                }
                composable(AppDestination.HistoryLocation.route) { backStack ->
                    val historyVm: HistoryViewModel = viewModel(factory = factory)
                    HistoryScreen(
                        viewModel = historyVm,
                        onBack = { popIfCurrent(backStack) },
                        onAddMeasurement = openAddMeasurement,
                        onOpenDetail = { sessionId ->
                            if (sessionId.isNotBlank()) {
                                navigateFromEntry(backStack, AppDestination.HistoryDetail.route(sessionId))
                            }
                        }
                    )
                }
                composable(AppDestination.Trend.route) { backStack ->
                    val vm: TrendViewModel = viewModel(factory = factory)
                    com.example.bloodpressurerecord.ui.history.TrendScreen(
                        viewModel = vm,
                        onBack = null,
                        onAddMeasurement = openAddMeasurement,
                        onOpenRecord = { id ->
                            if (id.isNotBlank()) {
                                navigateFromEntry(backStack, AppDestination.HistoryDetail.route(id))
                            }
                        }
                    )
                }
                composable(AppDestination.HistoryDetail.route) { backStack ->
                    val sessionId = backStack.arguments?.getString("sessionId").orEmpty()
                    val vm: HistoryDetailViewModel = viewModel(
                        factory = HistoryDetailViewModel.provideFactory(
                            sessionId = sessionId,
                            repository = application.appContainer.bloodPressureRepository,
                            deletionUndoManager = undoManager
                        )
                    )
                    HistoryDetailScreen(
                        viewModel = vm,
                        sessionId = sessionId,
                        onBack = { popIfCurrent(backStack) },
                        onEdit = { id ->
                            if (id.isNotBlank()) {
                                navigateFromEntry(backStack, AppDestination.HistoryEdit.route(id))
                            }
                        },
                        onDeleted = { popIfCurrent(backStack) }
                    )
                }
                composable(AppDestination.HistoryEdit.route) { backStack ->
                    val sessionId = backStack.arguments?.getString("sessionId").orEmpty()
                    val editFactory = remember(sessionId) {
                        EditSessionViewModel.provideFactory(
                            sessionId = sessionId,
                            repository = application.appContainer.bloodPressureRepository,
                            draftRepository = application.appContainer.sessionDraftRepository,
                            highRiskAlertEnabled = application.appContainer.settingsRepository
                                .observeSettings()
                                .map { it.appSettings.highRiskAlertEnabled }
                                .distinctUntilChanged(),
                            updatePromptGate = application.appContainer.updatePromptGate
                        )
                    }
                    val vm: EditSessionViewModel = viewModel(factory = editFactory)
                    EditSessionScreen(
                        viewModel = vm,
                        canConsumeSave = currentEntry == backStack,
                        isCurrentEntry = { navController.currentBackStackEntry == backStack },
                        onSaved = {
                            if (navController.currentBackStackEntry == backStack) {
                                navController.popBackStack()
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    snackbar.showSnackbar(
                                        message = "修改已保存",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        },
                        onBack = { popIfCurrent(backStack) }
                    )
                }
                composable(AppDestination.Settings.route) { backStack ->
                    val vm: SettingsViewModel = viewModel(factory = factory)
                    SettingsScreen(
                        viewModel = vm,
                        onOpenProfile = { navigateFromEntry(backStack, AppDestination.SettingsProfile.route) },
                        onOpenReminder = { navigateFromEntry(backStack, AppDestination.SettingsReminder.route) },
                        onOpenDisplay = { navigateFromEntry(backStack, AppDestination.SettingsDisplay.route) },
                        onOpenDataManagement = { navigateFromEntry(backStack, AppDestination.SettingsDataManagement.route) },
                        onOpenInfo = { navigateFromEntry(backStack, AppDestination.SettingsInfo.route) }
                    )
                }
                composable(AppDestination.SettingsProfile.route) { backStack ->
                    val vm: SettingsViewModel = viewModel(factory = factory)
                    SettingsProfileScreen(
                        viewModel = vm,
                        onBack = { popIfCurrent(backStack) },
                        onSavedFeedback = {
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                snackbar.showSnackbar("用户资料已保存", duration = SnackbarDuration.Short)
                            }
                        }
                    )
                }
                composable(AppDestination.SettingsReminder.route) { backStack ->
                    val vm: SettingsViewModel = viewModel(factory = factory)
                    SettingsReminderScreen(
                        viewModel = vm,
                        onBack = { popIfCurrent(backStack) }
                    )
                }
                composable(AppDestination.SettingsDisplay.route) { backStack ->
                    val vm: SettingsViewModel = viewModel(factory = factory)
                    SettingsDisplayScreen(viewModel = vm, onBack = { popIfCurrent(backStack) })
                }
                composable(AppDestination.SettingsDataManagement.route) { backStack ->
                    val vm: SettingsViewModel = viewModel(factory = factory)
                    SettingsDataManagementScreen(
                        viewModel = vm,
                        onBack = { popIfCurrent(backStack) },
                        onOpenDoctorReport = { navigateFromEntry(backStack, AppDestination.DoctorReport.route) }
                    )
                }
                composable(AppDestination.DoctorReport.route) { backStack ->
                    val vm: ReportViewModel = viewModel(factory = factory)
                    ReportScreen(viewModel = vm, onBack = { popIfCurrent(backStack) })
                }
                composable(AppDestination.SettingsInfo.route) { backStack ->
                    SettingsInfoScreen(
                        onBack = { popIfCurrent(backStack) },
                        onOpenAppGuide = { navigateFromEntry(backStack, AppDestination.SettingsInfoAppGuide.route) },
                        onOpenReleaseNotes = { navigateFromEntry(backStack, AppDestination.SettingsInfoReleaseNotes.route) },
                        updateViewModel = updateVm,
                        onDownloadUpdate = updateActions.download,
                        onOpenReleases = updateActions.openReleases,
                        onOpenDirectDownload = updateActions.openDirectDownload
                    )
                }
                composable(AppDestination.SettingsInfoAppGuide.route) { backStack ->
                    SettingsAppGuideScreen(onBack = { popIfCurrent(backStack) })
                }
                composable(AppDestination.SettingsInfoReleaseNotes.route) { backStack ->
                    SettingsInfoReleaseNotesScreen(onBack = { popIfCurrent(backStack) })
                }
            }

            // 悬浮 Dock：平滑滑入滑出，叠在 NavHost 之上。
            AnimatedVisibility(
                visible = showBottomBar,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    slideInVertically(
                        animationSpec = tween(durationMillis = 260, easing = AppMotion.SmoothOutEasing),
                        initialOffsetY = { it }
                    ) + fadeIn(tween(durationMillis = 180, easing = AppMotion.SmoothOutEasing))
                },
                exit = if (reducedMotion) {
                    ExitTransition.None
                } else {
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 200, easing = AppMotion.SmoothInEasing),
                        targetOffsetY = { it }
                    ) + fadeOut(tween(durationMillis = 150, easing = AppMotion.SmoothInEasing))
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AppBottomDock(
                    tabs = tabs,
                    currentRoute = current,
                    compact = dockScrollState.isCompact,
                    onAddMeasurement = openAddMeasurement,
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
    }

    if (updateState.dialogVisible && updatePromptAllowed) {
        UpdateDialog(
            state = updateState,
            currentVersion = BuildConfig.VERSION_NAME,
            onDownload = updateActions.download,
            onSnooze = updateVm::snooze,
            onSkip = updateVm::skip,
            onDismiss = updateVm::dismissDialog,
            onOpenReleases = updateActions.openReleases,
            onOpenDirectDownload = updateActions.openDirectDownload
        )
    }
}
