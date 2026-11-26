package com.example.bloodpressurerecord.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigationStateTest {

    @Test
    fun saveTicket_fromTopLevelMeasure_opensHistoryEditOnce() {
        val ticket = SaveEditActionTicket("session-101") { "history/edit/$it" }
        ticket.arm(entryId = "entry-measure", route = AppDestination.Measure.route)

        assertEquals(
            "history/edit/session-101",
            ticket.consumeTargetRoute(
                currentEntryId = "entry-measure",
                currentRoute = AppDestination.Measure.route
            )
        )
        // 重复点击不得二次导航
        assertNull(
            ticket.consumeTargetRoute(
                currentEntryId = "entry-measure",
                currentRoute = AppDestination.Measure.route
            )
        )
    }

    @Test
    fun saveTicket_fromHistoryLocationSubpage_opensHistoryEdit() {
        val ticket = SaveEditActionTicket("session-202") { "history/edit/$it" }
        // D-01: History -> NoteSearch -> HistoryLocation -> AddMeasurement 保存后返回 HistoryLocation
        ticket.arm(
            entryId = "entry-history-location-1",
            route = AppDestination.HistoryLocation.route
        )
        // 注册监听时的当前页回调不应误伤有效票据
        ticket.onDestinationChanged(
            currentEntryId = "entry-history-location-1",
            currentRoute = AppDestination.HistoryLocation.route
        )

        assertEquals(
            "history/edit/session-202",
            ticket.consumeTargetRoute(
                currentEntryId = "entry-history-location-1",
                currentRoute = AppDestination.HistoryLocation.route
            )
        )
    }

    @Test
    fun saveTicket_invalidatesPermanentlyWhenUserNavigatesAwayDuringSnackbar() {
        val ticket = SaveEditActionTicket("session-303") { "history/edit/$it" }
        ticket.arm(
            entryId = "entry-history-location-1",
            route = AppDestination.HistoryLocation.route
        )

        // 用户在 Snackbar 显示期间返回了 NoteSearch，即使随后又回到旧页或尝试触发也不得跳转
        ticket.onDestinationChanged(
            currentEntryId = "entry-note-search",
            currentRoute = AppDestination.NoteSearch.route
        )

        assertNull(
            ticket.consumeTargetRoute(
                currentEntryId = "entry-note-search",
                currentRoute = AppDestination.NoteSearch.route
            )
        )
        assertNull(
            ticket.consumeTargetRoute(
                currentEntryId = "entry-history-location-1",
                currentRoute = AppDestination.HistoryLocation.route
            )
        )
    }

    @Test
    fun saveTicket_rejectsBlankIdAndTrendFullscreen() {
        val blankTicket = SaveEditActionTicket("   ") { "history/edit/$it" }
        assertNull(blankTicket.validSessionId)
        blankTicket.arm(entryId = "entry-measure", route = AppDestination.Measure.route)
        assertNull(
            blankTicket.consumeTargetRoute(
                currentEntryId = "entry-measure",
                currentRoute = AppDestination.Measure.route
            )
        )

        val fullscreenTicket = SaveEditActionTicket("session-404") { "history/edit/$it" }
        fullscreenTicket.arm(entryId = "entry-trend", route = AppDestination.Trend.route)
        assertNull(
            fullscreenTicket.consumeTargetRoute(
                currentEntryId = "entry-trend",
                currentRoute = AppDestination.Trend.route,
                isTrendFullscreen = true
            )
        )
    }

    @Test
    fun topLevelRouteTracker_distinguishesPeerTabSwitchFromSubpageReturnAndTrendToggle() {
        val tracker = TopLevelRouteTracker()

        // 首次冷启动进入首页：不触发 Tab 切换淡入
        assertEquals(AppDestination.Measure.route, tracker.onRouteChanged(AppDestination.Measure.route, true))
        assertEquals(0, tracker.tabSwitchToken)
        assertNull(tracker.lastSwitchTargetRoute)

        // 切换到历史 Tab：产生平级切换信号
        assertEquals(AppDestination.History.route, tracker.onRouteChanged(AppDestination.History.route, true))
        assertEquals(1, tracker.tabSwitchToken)
        assertEquals(AppDestination.History.route, tracker.lastSwitchTargetRoute)

        // 进入搜索子页与定位子页：保留历史 Tab 选中态供 Dock 退场使用，清除平级入场信号
        assertEquals(AppDestination.History.route, tracker.onRouteChanged(AppDestination.NoteSearch.route, true))
        assertNull(tracker.lastSwitchTargetRoute)
        assertEquals(AppDestination.History.route, tracker.onRouteChanged(AppDestination.HistoryLocation.route, true))

        // 从子页返回历史页：不增加 tabSwitchToken，避免与返回视差动画叠加
        assertEquals(AppDestination.History.route, tracker.onRouteChanged(AppDestination.History.route, true))
        assertEquals(1, tracker.tabSwitchToken)
        assertNull(tracker.lastSwitchTargetRoute)

        // 切换到趋势后再关闭 showTrendChart：选中态安全回退到首页
        assertEquals(AppDestination.Trend.route, tracker.onRouteChanged(AppDestination.Trend.route, true))
        assertEquals(2, tracker.tabSwitchToken)
        assertEquals(AppDestination.Measure.route, tracker.onRouteChanged(null, showTrendChart = false))
    }
}
