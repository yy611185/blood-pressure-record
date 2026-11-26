package com.example.bloodpressurerecord.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bloodpressurerecord.AppViewModelFactory
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.BuildConfig
import com.example.bloodpressurerecord.reminder.MedicationReminderConfirmationRequest
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.home.MedicationReminderConfirmationDialog
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
import kotlinx.coroutines.job
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

/**
 * 轻量追踪顶级 Tab 选中态与平级切换信号：
 * - 进入子页期间稳定保留所属顶级 Tab 供 Dock 退场渲染，关闭趋势入口时自动回退首页；
 * - 仅在「顶级页 -> 另一顶级页」时发出平级入场信号，子页返回与冷启动不误触淡入。
 */
internal class TopLevelRouteTracker(initialRoute: String = AppDestination.Measure.route) {
    var activeTopLevelRoute: String = initialRoute
        private set
    private var lastObservedRoute: String? = null
    var tabSwitchToken: Int = 0
        private set
    var lastSwitchTargetRoute: String? = null
        private set

    fun onRouteChanged(currentRoute: String?, showTrendChart: Boolean): String {
        if (currentRoute != null) {
            val previous = lastObservedRoute
            if (currentRoute != previous) {
                if (previous != null && previous in TopLevelRoutes && currentRoute in TopLevelRoutes) {
                    tabSwitchToken += 1
                    lastSwitchTargetRoute = currentRoute
                } else if (currentRoute !in TopLevelRoutes) {
                    lastSwitchTargetRoute = null
                }
                lastObservedRoute = currentRoute
            }
            if (currentRoute in TopLevelRoutes) {
                activeTopLevelRoute = currentRoute
            }
        }
        if (!showTrendChart && activeTopLevelRoute == AppDestination.Trend.route) {
            activeTopLevelRoute = AppDestination.Measure.route
        }
        return activeTopLevelRoute
    }
}

/**
 * 新增测量保存后的 Snackbar「再改改」安全门控（兼容顶级页与 HistoryLocation 等子页返回契约）：
 * - 保存返回落位后绑定当前 NavBackStackEntry ID 与路由；
 * - 展示期间一旦用户导航离开该条目立即永久失效，杜绝跨页误跳与重复触发。
 */
internal class SaveEditActionTicket(
    rawSessionId: String?,
    private val routeBuilder: (String) -> String = AppDestination.HistoryEdit::route
) {
    val validSessionId: String? = rawSessionId?.takeIf { it.isNotBlank() }
    var landingEntryId: String? = null
        private set
    var landingRoute: String? = null
        private set
    private var armed = false
    private var invalidated = false
    private var consumed = false

    fun arm(entryId: String?, route: String?) {
        if (validSessionId == null || entryId.isNullOrBlank() || route.isNullOrBlank() ||
            route == AppDestination.AddMeasurement.route
        ) {
            invalidated = true
            return
        }
        landingEntryId = entryId
        landingRoute = route
        armed = true
    }

    fun onDestinationChanged(currentEntryId: String?, currentRoute: String?): Boolean {
        if (!armed || invalidated || consumed) return false
        if (currentEntryId != landingEntryId || currentRoute != landingRoute) {
            invalidated = true
            return true
        }
        return false
    }

    fun invalidate() {
        invalidated = true
    }

    fun consumeTargetRoute(
        currentEntryId: String?,
        currentRoute: String?,
        isTrendFullscreen: Boolean = false
    ): String? {
        val id = validSessionId ?: return null
        if (!armed || invalidated || consumed || isTrendFullscreen) return null
        if (currentEntryId == null || currentEntryId != landingEntryId) return null
        if (currentRoute == null || currentRoute != landingRoute) return null
        val targetRoute = routeBuilder(id)
        if (currentRoute == targetRoute || currentRoute == AppDestination.AddMeasurement.route) return null
        consumed = true
        return targetRoute
    }
}

/**
 * 顶级页面稳定实心底板容器：
 * 外层始终保持 100% 不透明背景色（彻底杜绝露底闪空与前后页文字叠影），
 * 平级切换时仅内层内容做 0.88 -> 1.0 的轻量光学落位；0x 模式与子页返回保持 1.0f 直出。
 */
@Composable
internal fun TopLevelTabSurface(
    route: String,
    tabSwitchToken: Int,
    shouldAnimateArrival: Boolean,
    reducedMotion: Boolean,
    content: @Composable () -> Unit
) {
    val alphaAnim = remember(route) { Animatable(1f) }
    var handledToken by remember(route) { mutableStateOf(0) }
    val pendingSettle = !reducedMotion && shouldAnimateArrival &&
        tabSwitchToken > 0 && handledToken != tabSwitchToken
    LaunchedEffect(route, tabSwitchToken, shouldAnimateArrival, reducedMotion) {
        if (reducedMotion || !shouldAnimateArrival) {
            if (shouldAnimateArrival) handledToken = tabSwitchToken
            alphaAnim.snapTo(1f)
        } else if (pendingSettle) {
            handledToken = tabSwitchToken
            alphaAnim.snapTo(0.88f)
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 110, easing = AppMotion.SmoothOutEasing)
            )
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = when {
                        reducedMotion || !shouldAnimateArrival -> 1f
                        pendingSettle -> 0.88f
                        else -> alphaAnim.value
                    }
                }
        ) {
            content()
        }
    }
}

@Composable
fun BloodPressureAppRoot(
    showTrendChart: Boolean = true,
    pendingDestination: String? = null,
    onConsumePendingDestination: () -> Unit = {},
    pendingMedicationConfirmation: MedicationReminderConfirmationRequest? = null,
    onConsumePendingMedicationConfirmation: (MedicationReminderConfirmationRequest) -> Unit = {}
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
    val updatePromptAllowed = updateForeground && safeUpdateRoute && !dataActionBusy &&
        pendingDestination == null && pendingMedicationConfirmation == null
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

    // 从服药通知进入时，若当前位于其他顶层页面则切回测量首页以便查看今日服药；
    // 若正在子页（如录入/编辑/历史详情），保留原返回栈（不破坏 D-01），直接在当前页弹出核对确认框。
    var navigatedMedConfirmRequest by remember { mutableStateOf<MedicationReminderConfirmationRequest?>(null) }
    LaunchedEffect(pendingMedicationConfirmation, current, trendFullscreen, dataActionBusy) {
        val request = pendingMedicationConfirmation ?: run {
            navigatedMedConfirmRequest = null
            return@LaunchedEffect
        }
        if (navigatedMedConfirmRequest == request || dataActionBusy || trendFullscreen) return@LaunchedEffect
        val liveRoute = navController.currentDestination?.route ?: return@LaunchedEffect
        if (liveRoute in TopLevelRoutes && liveRoute != AppDestination.Measure.route) {
            navController.navigate(AppDestination.Measure.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        navigatedMedConfirmRequest = request
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
    var savedTopLevelRoute by rememberSaveable { mutableStateOf(AppDestination.Measure.route) }
    val tabTracker = remember { TopLevelRouteTracker(savedTopLevelRoute) }
    val activeTopLevelRoute = tabTracker.onRouteChanged(current, showTrendChart)
    if (savedTopLevelRoute != activeTopLevelRoute) {
        savedTopLevelRoute = activeTopLevelRoute
    }
    val dockScrollState = rememberDockScrollState(activeTopLevelRoute)
    LaunchedEffect(trendFullscreen) {
        if (!trendFullscreen) dockScrollState.expand()
    }
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
                    if (reducedMotion || isTabSwitch()) {
                        // 顶级 Tab 容器层 0ms 交接，由 TopLevelTabSurface 实心底板承接，不受 push/pop Z 轴或连点中断影响。
                        EnterTransition.None
                    } else if (initialState.destination.route == AppDestination.AddMeasurement.route) {
                        fadeIn(tween(durationMillis = 180, easing = AppMotion.SmoothOutEasing))
                    } else {
                        // 子页作为 100% 不透明实心层滑入，防止透出底层页面文字形成重影。
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(durationMillis = 280, easing = AppMotion.SmoothOutEasing)
                        )
                    }
                },
                exitTransition = {
                    if (reducedMotion || isTabSwitch()) {
                        ExitTransition.None
                    } else if (targetState.destination.route == AppDestination.AddMeasurement.route) {
                        ExitTransition.KeepUntilTransitionsFinished
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(durationMillis = 280, easing = AppMotion.SmoothOutEasing),
                            targetOffset = { it / 4 }
                        )
                    }
                },
                popEnterTransition = {
                    if (reducedMotion || isTabSwitch() || initialState.destination.route == AppDestination.AddMeasurement.route) {
                        EnterTransition.None
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(durationMillis = 260, easing = AppMotion.SmoothOutEasing),
                            initialOffset = { it / 4 }
                        )
                    }
                },
                popExitTransition = {
                    if (reducedMotion || isTabSwitch()) {
                        ExitTransition.None
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(durationMillis = 260, easing = AppMotion.SmoothOutEasing)
                        )
                    }
                }
            ) {
                composable(AppDestination.Measure.route) { backStack ->
                    TopLevelTabSurface(
                        route = AppDestination.Measure.route,
                        tabSwitchToken = tabTracker.tabSwitchToken,
                        shouldAnimateArrival = tabTracker.lastSwitchTargetRoute == AppDestination.Measure.route,
                        reducedMotion = reducedMotion
                    ) {
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
                }
                composable(
                    route = AppDestination.AddMeasurement.route,
                    enterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(durationMillis = 300, easing = AppMotion.SmoothOutEasing)
                            )
                        }
                    },
                    exitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = tween(durationMillis = 250, easing = AppMotion.SmoothInEasing)
                            )
                        }
                    },
                    popEnterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            fadeIn(
                                animationSpec = tween(durationMillis = 180, easing = AppMotion.SmoothOutEasing),
                                initialAlpha = 0.92f
                            )
                        }
                    },
                    popExitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = tween(durationMillis = 250, easing = AppMotion.SmoothInEasing)
                            )
                        }
                    }
                ) { backStack ->
                    val homeVm: HomeViewModel = viewModel(factory = factory)
                    var pendingSaveTicket by remember { mutableStateOf<SaveEditActionTicket?>(null) }
                    AddMeasurementScreen(
                        viewModel = homeVm,
                        canConsumeSave = currentEntry == backStack,
                        isCurrentEntry = { navController.currentBackStackEntry == backStack },
                        onBack = { popIfCurrent(backStack) },
                        onSaved = {
                            if (navController.currentBackStackEntry == backStack) {
                                val previousRoute = navController.previousBackStackEntry?.destination?.route
                                if (previousRoute == AppDestination.Measure.route) {
                                    navController.popBackStack()
                                } else if (previousRoute == null || previousRoute in TopLevelRoutes) {
                                    navController.navigate(AppDestination.Measure.route) {
                                        popUpTo(navController.graph.findStartDestination().id)
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.popBackStack()
                                }
                                pendingSaveTicket?.arm(
                                    entryId = navController.currentBackStackEntry?.id,
                                    route = navController.currentDestination?.route
                                )
                            } else {
                                pendingSaveTicket?.invalidate()
                            }
                        },
                        onSavedFeedback = { sessionId, avgSys, avgDia, categoryLabel ->
                            pendingSaveTicket?.invalidate()
                            val ticket = SaveEditActionTicket(sessionId)
                            pendingSaveTicket = ticket
                            val bpText = if (avgSys != null && avgDia != null) {
                                "$avgSys/$avgDia mmHg（$categoryLabel）"
                            } else {
                                "血压记录"
                            }
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val snackbarJob = coroutineContext.job
                                val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
                                    if (ticket.onDestinationChanged(
                                            currentEntryId = controller.currentBackStackEntry?.id,
                                            currentRoute = destination.route
                                        )
                                    ) {
                                        snackbarJob.cancel()
                                    }
                                }
                                navController.addOnDestinationChangedListener(listener)
                                try {
                                    val result = snackbar.showSnackbar(
                                        message = "已保存 $bpText",
                                        actionLabel = if (ticket.validSessionId != null) "再改改" else null,
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        val targetRoute = ticket.consumeTargetRoute(
                                            currentEntryId = navController.currentBackStackEntry?.id,
                                            currentRoute = navController.currentDestination?.route,
                                            isTrendFullscreen = trendVm?.fullscreen?.value == true
                                        )
                                        if (targetRoute != null) {
                                            navController.navigate(targetRoute) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                } finally {
                                    navController.removeOnDestinationChangedListener(listener)
                                }
                            }
                        }
                    )
                }
                composable(AppDestination.History.route) { backStack ->
                    TopLevelTabSurface(
                        route = AppDestination.History.route,
                        tabSwitchToken = tabTracker.tabSwitchToken,
                        shouldAnimateArrival = tabTracker.lastSwitchTargetRoute == AppDestination.History.route,
                        reducedMotion = reducedMotion
                    ) {
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
                    TopLevelTabSurface(
                        route = AppDestination.Trend.route,
                        tabSwitchToken = tabTracker.tabSwitchToken,
                        shouldAnimateArrival = tabTracker.lastSwitchTargetRoute == AppDestination.Trend.route,
                        reducedMotion = reducedMotion
                    ) {
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
                    TopLevelTabSurface(
                        route = AppDestination.Settings.route,
                        tabSwitchToken = tabTracker.tabSwitchToken,
                        shouldAnimateArrival = tabTracker.lastSwitchTargetRoute == AppDestination.Settings.route,
                        reducedMotion = reducedMotion
                    ) {
                        val vm: SettingsViewModel = viewModel(factory = factory)
                        SettingsScreen(
                            viewModel = vm,
                            onOpenProfile = { navigateFromEntry(backStack, AppDestination.SettingsProfile.route) },
                            onOpenReminder = { navigateFromEntry(backStack, AppDestination.SettingsReminder.route) },
                            onOpenDataManagement = { navigateFromEntry(backStack, AppDestination.SettingsDataManagement.route) },
                            onOpenInfo = { navigateFromEntry(backStack, AppDestination.SettingsInfo.route) }
                        )
                    }
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
                    currentRoute = activeTopLevelRoute,
                    compact = dockScrollState.isCompact,
                    active = showBottomBar,
                    onAddMeasurement = { if (showBottomBar) openAddMeasurement() },
                    onSelect = { destination ->
                        if (showBottomBar) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }

    if (pendingMedicationConfirmation != null && !dataActionBusy) {
        MedicationReminderConfirmationDialog(
            request = pendingMedicationConfirmation,
            medicationRepository = application.appContainer.medicationRepository,
            onDismissNotification = { timeId, epochDay ->
                scope.launch {
                    runCatching {
                        application.appContainer.medicationReminderCoordinator.dismissTakenNotification(timeId, epochDay)
                    }
                }
            },
            onDismiss = { onConsumePendingMedicationConfirmation(pendingMedicationConfirmation) },
            onFeedback = { message ->
                scope.launch {
                    snackbar.showSnackbar(message = message, duration = SnackbarDuration.Short)
                }
            }
        )
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
