package com.example.bloodpressurerecord.ui.common

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.MainActivity
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionReadingInput
import com.example.bloodpressurerecord.navigation.AppDestination
import com.example.bloodpressurerecord.navigation.TopLevelTabSurface
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.history.CalendarDay
import com.example.bloodpressurerecord.ui.history.CalendarDaySummary
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class InteractionAndMotionRegressionTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun focusRing_andKeyboardActivation_workImmediatelyInNormalAndReducedMotion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var backClicks = 0
            var selectedPillIndex by mutableStateOf(0)
            var chipSelected by mutableStateOf(false)
            var reducedMotion by mutableStateOf(false)

            scenario.onActivity { activity ->
                activity.setContentView(
                    androidx.compose.ui.platform.ComposeView(activity).apply {
                        setContent {
                            CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                                BloodPressureRecordTheme {
                                    Column(Modifier.padding(16.dp)) {
                                        AppBackIconButton(onClick = { backClicks += 1 })
                                        SegmentedPillGroup(
                                            options = listOf("日历", "近期"),
                                            selectedIndex = selectedPillIndex,
                                            onSelect = { selectedPillIndex = it }
                                        )
                                        SessionChoiceChip(
                                            text = "头晕",
                                            selected = chipSelected,
                                            onClick = { chipSelected = !chipSelected }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            composeRule.waitForIdle()
            val backNode = composeRule.onNodeWithContentDescription(context.getString(R.string.back))
            backNode.assert(SemanticsMatcher.expectValue(FocusRingVisibleKey, false))
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            backNode.performSemanticsAction(SemanticsActions.RequestFocus)
            composeRule.waitForIdle()
            backNode.assertIsFocused()
            backNode.assert(SemanticsMatcher.expectValue(FocusRingVisibleKey, true))
            backNode.performKeyInput { pressKey(Key.Enter) }
            composeRule.waitForIdle()
            assertEquals(1, backClicks)

            // 切换到 0x 减少动态效果：焦点环应零延迟即时显示，键盘可切换分段与标签
            reducedMotion = true
            composeRule.mainClock.autoAdvance = false
            try {
                val recentOption = composeRule.onNodeWithText("近期")
                recentOption.performSemanticsAction(SemanticsActions.RequestFocus)
                composeRule.mainClock.advanceTimeByFrame()
                recentOption.assert(SemanticsMatcher.expectValue(FocusRingVisibleKey, true))
                recentOption.performKeyInput { pressKey(Key.Enter) }
                composeRule.mainClock.advanceTimeByFrame()
                assertEquals(1, selectedPillIndex)

                val chipNode = composeRule.onNodeWithText("头晕")
                chipNode.performSemanticsAction(SemanticsActions.RequestFocus)
                composeRule.mainClock.advanceTimeByFrame()
                chipNode.assert(SemanticsMatcher.expectValue(FocusRingVisibleKey, true))
                chipNode.performKeyInput { pressKey(Key.Enter) }
                composeRule.mainClock.advanceTimeByFrame()
                assertTrue(chipSelected)
            } finally {
                composeRule.mainClock.autoAdvance = true
            }
        }
    }

    @Test
    fun edgeTouchHitTarget_remainsStableWhilePressableScaleShrinks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var backClicks = 0
            var chipClicks = 0
            scenario.onActivity { activity ->
                activity.setContentView(
                    androidx.compose.ui.platform.ComposeView(activity).apply {
                        setContent {
                            CompositionLocalProvider(LocalReducedMotion provides false) {
                                BloodPressureRecordTheme {
                                    Column(Modifier.padding(24.dp)) {
                                        AppBackIconButton(onClick = { backClicks += 1 })
                                        SessionChoiceChip(
                                            text = "边缘点击测试",
                                            selected = false,
                                            onClick = { chipClicks += 1 }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            try {
                val backNode = composeRule.onNodeWithContentDescription(context.getString(R.string.back))
                backNode.assertHeightIsAtLeast(48.dp)
                // 在距离左边缘仅 1.5px 处按下；若点击层在 0.93x 缩放层内部，缩入约 1.7dp 后抬手会脱靶。
                backNode.performTouchInput {
                    down(Offset(x = 1.5f, y = centerY))
                }
                composeRule.mainClock.advanceTimeBy(180)
                backNode.performTouchInput {
                    up()
                }
                composeRule.mainClock.advanceTimeByFrame()
                assertEquals("Edge press on AppBackIconButton must not slip out when scaled", 1, backClicks)

                val chipNode = composeRule.onNodeWithText("边缘点击测试")
                chipNode.performTouchInput {
                    down(Offset(x = width - 1.5f, y = centerY))
                }
                composeRule.mainClock.advanceTimeBy(180)
                chipNode.performTouchInput {
                    up()
                }
                composeRule.mainClock.advanceTimeByFrame()
                assertEquals("Edge press on SessionChoiceChip must not slip out when scaled", 1, chipClicks)
            } finally {
                composeRule.mainClock.autoAdvance = true
            }
        }
    }

    @Test
    fun dockExitDuringCollapse_freezesLayoutAndClearsActionableSemantics() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var compact by mutableStateOf(false)
            var active by mutableStateOf(true)
            var addClicks = 0
            val tabs = listOf(
                AppDestination.Measure,
                AppDestination.History,
                AppDestination.Trend,
                AppDestination.Settings
            )

            scenario.onActivity { activity ->
                activity.setContentView(
                    androidx.compose.ui.platform.ComposeView(activity).apply {
                        setContent {
                            CompositionLocalProvider(LocalReducedMotion provides false) {
                                BloodPressureRecordTheme {
                                    Box(Modifier.fillMaxWidth().testTag("dockHost")) {
                                        AppBottomDock(
                                            tabs = tabs,
                                            currentRoute = AppDestination.Measure.route,
                                            onAddMeasurement = { addClicks += 1 },
                                            onSelect = {},
                                            compact = compact,
                                            active = active,
                                            modifier = Modifier.testTag("bottomDock")
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("记一次血压").assertIsDisplayed()

            composeRule.mainClock.autoAdvance = false
            try {
                // Dock 刚开始收缩 48ms 时立即进入退场态（模拟刚滚列表就点 + 打开新增页）
                compact = true
                composeRule.mainClock.advanceTimeBy(48)
                active = false
                composeRule.mainClock.advanceTimeByFrame()

                // 退场期间可操作语义应立即清空，避免误导读屏或二次点击
                composeRule.onNodeWithContentDescription("记一次血压").assertDoesNotExist()
                composeRule.onNodeWithText(AppDestination.History.label).assertDoesNotExist()

                val widthAtFreeze = composeRule.onNodeWithTag("bottomDock")
                    .fetchSemanticsNode().boundsInRoot.width
                composeRule.mainClock.advanceTimeBy(220)
                val widthLater = composeRule.onNodeWithTag("bottomDock")
                    .fetchSemanticsNode().boundsInRoot.width
                assertTrue(
                    "Dock width must stay frozen during exit: before=$widthAtFreeze, after=$widthLater",
                    abs(widthLater - widthAtFreeze) <= 1f
                )
                assertEquals(0, addClicks)
            } finally {
                composeRule.mainClock.autoAdvance = true
            }
        }
    }

    @Test
    fun reducedMotion_wheelDragSnapsImmediately_andCalendarPulseResetsCleanly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var submitted: Pair<Int, Int>? = null
            var reducedMotion by mutableStateOf(true)
            var daySelected by mutableStateOf(false)
            val sampleDate = LocalDate.of(2026, 7, 25)

            scenario.onActivity { activity ->
                activity.setContentView(
                    androidx.compose.ui.platform.ComposeView(activity).apply {
                        setContent {
                            CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                                BloodPressureRecordTheme {
                                    Column {
                                        CalendarDay(
                                            date = sampleDate,
                                            summary = CalendarDaySummary(sampleDate, recordCount = 2, containsHighRisk = false),
                                            selected = daySelected,
                                            today = false,
                                            currentDate = sampleDate,
                                            onClick = { daySelected = true },
                                            onDoubleClick = {}
                                        )
                                        WheelTimePickerDialog(
                                            title = "选择时间",
                                            initialHour = 10,
                                            initialMinute = 30,
                                            onDismiss = {},
                                            onConfirm = { h, m -> submitted = h to m }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            try {
                // 0x 下拖动小时滚轮半格以上并松手：应在单帧内立即吸附到位，无惯性多帧滑行
                val hour10 = composeRule.onNodeWithContentDescription("10 时")
                val itemHeight = hour10.fetchSemanticsNode().boundsInRoot.height
                hour10.performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -itemHeight * 0.85f))
                    up()
                }
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.onNodeWithContentDescription("11 时").assertIsSelected()

                // 验证 1x 脉冲进行中途切换到 0x 不会残留缩放或卡住
                reducedMotion = false
                composeRule.mainClock.advanceTimeByFrame()
                daySelected = true
                composeRule.mainClock.advanceTimeBy(32)
                reducedMotion = true
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.onNodeWithContentDescription("7月25日，有2条记录，可选择，已选择")
                    .assertIsSelected()

                composeRule.onNodeWithText("确定").performClick()
                composeRule.mainClock.advanceTimeByFrame()
                assertEquals(11 to 30, submitted)
            } finally {
                composeRule.mainClock.autoAdvance = true
            }
        }
    }

    @Test
    fun narrow320dp_and2xFontScale_keepsInteractiveControlsAccessible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(
                    androidx.compose.ui.platform.ComposeView(activity).apply {
                        setContent {
                            val density = LocalDensity.current
                            CompositionLocalProvider(
                                LocalDensity provides Density(density.density, fontScale = 2.0f),
                                LocalReducedMotion provides true
                            ) {
                                BloodPressureRecordTheme {
                                    Column(Modifier.width(320.dp).padding(12.dp)) {
                                        AppBackIconButton(onClick = {})
                                        SegmentedPillGroup(
                                            options = listOf("日历", "近期"),
                                            selectedIndex = 0,
                                            onSelect = {}
                                        )
                                        SessionChoiceChip(
                                            text = "没睡好",
                                            selected = true,
                                            onClick = {}
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(context.getString(R.string.back))
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithText("日历").assertIsDisplayed().assertIsSelected()
            composeRule.onNodeWithText("近期").assertIsDisplayed()
            composeRule.onNodeWithText("没睡好")
                .assertIsDisplayed()
                .assertIsSelected()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun tabArrivalSettlesAtFullOpacityAfterTheFirstRecomposition() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var tabSwitchToken by mutableStateOf(0)
            scenario.onActivity { activity ->
                activity.setContentView(
                    androidx.compose.ui.platform.ComposeView(activity).apply {
                        setContent {
                            BloodPressureRecordTheme {
                                Box(Modifier.size(72.dp).testTag("tab-surface")) {
                                    TopLevelTabSurface(
                                        route = AppDestination.Settings.route,
                                        tabSwitchToken = tabSwitchToken,
                                        shouldAnimateArrival = tabSwitchToken > 0,
                                        reducedMotion = false
                                    ) {
                                        Box(Modifier.matchParentSize().background(Color.Black))
                                    }
                                }
                            }
                        }
                    }
                )
            }
            val surface = composeRule.onNodeWithTag("tab-surface")
            composeRule.runOnIdle { tabSwitchToken = 1 }
            composeRule.waitForIdle()
            val pixels = surface.captureToImage().toPixelMap()
            assertEquals(Color.Black, pixels[pixels.width / 2, pixels.height / 2])
        }
    }

    @Test
    fun subpageHistoryLocation_saveMeasurement_snackbarEditActionOpensHistoryEdit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as BloodPressureApplication
        val repository = application.appContainer.bloodPressureRepository
        org.junit.Assume.assumeTrue(
            "Fixture requires an empty measurement draft",
            application.appContainer.sessionDraftRepository.load("add_session").getOrThrow() == null
        )
        val testStartMillis = System.currentTimeMillis()
        val uniqueNote = "头晕定位回归_$testStartMillis"
        val createdNoteMarker = "D01新建回归_$testStartMillis"
        val pastMillis = LocalDate.now()
            .minusMonths(2)
            .withDayOfMonth(10)
            .atTime(9, 15)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val seededId = runBlocking {
            repository.saveSession(
                SaveSessionInput(
                    measuredAt = pastMillis,
                    scene = "居家",
                    note = uniqueNote,
                    symptoms = listOf("头晕"),
                    readings = listOf(
                        SessionReadingInput(systolic = 128, diastolic = 82, pulse = 70),
                        SessionReadingInput(systolic = 130, diastolic = 84, pulse = 72)
                    )
                )
            ).getOrThrow()
        }

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                composeRule.waitForIdle()

                // 1. History -> NoteSearch -> HistoryLocation
                composeRule.onNodeWithText(AppDestination.History.label).performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithContentDescription(context.getString(R.string.note_search_title))
                    .performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithText("头晕").performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodes(
                        androidx.compose.ui.test.hasText(uniqueNote, substring = true)
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNode(
                    androidx.compose.ui.test.hasText(uniqueNote, substring = true)
                ).performClick()
                composeRule.waitForIdle()

                // 确认处于子页 HistoryLocation（顶部带返回按钮）
                composeRule.onNodeWithContentDescription(context.getString(R.string.back))
                    .assertIsDisplayed()

                // 2. 在 HistoryLocation 中切到「近期」（本周无记录）并点击「记一次血压」打开 AddMeasurement
                composeRule.onNodeWithTag("history-records-list").performScrollToIndex(0)
                composeRule.onNodeWithText(context.getString(R.string.history_mode_recent))
                    .performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodes(
                        androidx.compose.ui.test.hasText("记一次血压")
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("记一次血压").performScrollTo().performClick()
                composeRule.waitForIdle()

                // 3. 录入两组有效读数并写入仅归属本测试的唯一标记，确保清理绝不误删非测试记录
                composeRule.onNodeWithContentDescription("第 1 组高压（收缩压）").performTextInput("122")
                composeRule.onNodeWithContentDescription("第 1 组低压（舒张压）").performTextInput("78")
                composeRule.onNodeWithContentDescription("第 2 组高压（收缩压）").performTextInput("124")
                composeRule.onNodeWithContentDescription("第 2 组低压（舒张压）").performTextInput("80")
                composeRule.onNodeWithText("补充情况（选填）").performScrollTo().performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithText("有没有不舒服？").performScrollTo().performTextInput(createdNoteMarker)
                composeRule.onNodeWithText("保存记录").performScrollTo().performClick()

                // 4. D-01 契约：保存后返回子页 HistoryLocation，且点击 Snackbar「再改改」成功打开修改读数页
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodes(
                        androidx.compose.ui.test.hasText("再改改")
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText(context.getString(R.string.history_title)).assertIsDisplayed()
                composeRule.onNodeWithText("再改改").performClick()

                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodes(
                        androidx.compose.ui.test.hasText("修改读数")
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("修改读数").assertIsDisplayed()

                // 5. 从修改读数页返回仍回到 HistoryLocation 子页
                composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithText(context.getString(R.string.history_title)).assertIsDisplayed()
                composeRule.onNodeWithContentDescription(context.getString(R.string.back)).assertIsDisplayed()
            }
        } finally {
            runBlocking {
                val windowStart = testStartMillis - 60_000L
                val windowEnd = System.currentTimeMillis() + 60_000L
                val candidates = repository.observeSessionSummariesInRange(windowStart, windowEnd).first()
                val testOwnedIds = candidates.mapNotNull { summary ->
                    val detail = repository.observeSession(summary.id).first()
                    summary.id.takeIf { detail?.symptomNote == createdNoteMarker }
                }
                (testOwnedIds + seededId).distinct().forEach { id ->
                    runCatching { repository.deleteSession(id) }
                }
            }
        }
    }
}
