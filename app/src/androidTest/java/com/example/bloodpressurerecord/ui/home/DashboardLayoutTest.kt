package com.example.bloodpressurerecord.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bloodpressurerecord.data.repository.ClearAllDataResult
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.data.repository.SettingsBundle
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.UserProfile
import com.example.bloodpressurerecord.domain.time.naturalWeekDates
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.settings.SettingsProfileScreen
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun measurementValuesAndStatusesFitAt320DpAndFontScale2() {
        val today = LocalDate.of(2026, 10, 13)
        val zone = ZoneId.systemDefault()
        val morning = SessionSummary("morning", today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
            147, 89, 72, "STAGE1", "晨间", null, false)
        val evening = SessionSummary("evening", today.atTime(20, 0).atZone(zone).toInstant().toEpochMilli(),
            126, 78, 68, "NORMAL", "晚间", null, false)

        setAt320DpFontScale2 { MeasurementSlotsSection(morning, evening, onAdd = {}) }

        val morningBounds = composeRule.onNodeWithTag("measurement-slot-早上").fetchSemanticsNode().boundsInRoot
        val eveningBounds = composeRule.onNodeWithTag("measurement-slot-晚上").fetchSemanticsNode().boundsInRoot
        assertTrue("Large-text measurement cards should stack", eveningBounds.top >= morningBounds.bottom - 1f)
        assertTextInside("147/89", morningBounds)
        assertTextInside("126/78", eveningBounds)
        assertTextInside(CategoryPresentation.label("STAGE1"), morningBounds)
        assertTextInside(CategoryPresentation.label("NORMAL"), eveningBounds)
    }

    @Test
    fun sectionHeaderBreaksAndEmptyMedicationStateStaysNeutralAt320DpLargeText() {
        val today = LocalDate.of(2026, 10, 13)
        val state = DashboardUiState(
            today = today,
            latest = LatestSessionSummary("latest", today.atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 133, 83, "NORMAL", false),
            streakDays = 3,
            loading = false
        )
        setAt320DpFontScale2 {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionHeader("今日服药", "0/0 已打卡")
                ReadingHero(state)
            }
        }

        val title = composeRule.onNodeWithText("今日服药").fetchSemanticsNode().boundsInRoot
        val trailing = composeRule.onNodeWithText("0/0 已打卡").fetchSemanticsNode().boundsInRoot
        val header = composeRule.onNodeWithTag("section-header-今日服药").fetchSemanticsNode().boundsInRoot
        assertTrue("Header title must remain inside its bounds", title.left >= header.left && title.right <= header.right)
        assertTrue("Trailing copy should move to its own line", trailing.top >= title.bottom - 1f)
        assertTrue("Trailing copy must remain inside its bounds", trailing.left >= header.left && trailing.right <= header.right)
        assertTextLayoutFits("今日服药")
        assertTextLayoutFits("0/0 已打卡")
        repeat(3) {
            composeRule.onNodeWithContentDescription("小压，点一下换一句话")
                .performScrollTo().performClick()
        }
        composeRule.onNodeWithText("每天早晚各测一次，记在这里方便回看变化。").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("今天的药都吃过啦，真棒！").assertDoesNotExist()
    }

    @Test
    fun weekCardKeepsStreakAndAllWeekdayLabelsVisibleInLargeText() {
        val today = LocalDate.of(2026, 10, 14) // 周三，所属自然周为周一(10-12)→周日(10-18)
        val state = DashboardUiState(
            today = today,
            week = naturalWeekDates(today).mapIndexed { index, date ->
                DashboardWeekDay(date, recorded = index < 3)
            },
            streakDays = 21,
            showTrendChart = false,
            loading = false
        )

        val width = mutableStateOf(360.dp)
        val fontScale = mutableStateOf(1.15f)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                BloodPressureRecordTheme {
                    Surface {
                        Column(Modifier.width(width.value).padding(horizontal = 20.dp)) { WeekCard(state) }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val normalStreak = composeRule.onNodeWithTag("week-streak-summary").fetchSemanticsNode().boundsInRoot
        val normalWeekdays = composeRule.onNodeWithTag("week-day-indicators").fetchSemanticsNode().boundsInRoot
        assertTrue("Normal layout must give the streak summary width", normalStreak.width > 0f)
        assertTrue("Normal layout keeps the summary and week on one row",
            kotlin.math.abs(normalStreak.center.y - normalWeekdays.center.y) <= 1f)
        assertTrue("Weekday row follows the streak summary", normalWeekdays.left >= normalStreak.right - 1f)
        assertWeekCopyFits(normalStreak, normalWeekdays)

        composeRule.runOnIdle { width.value = 320.dp; fontScale.value = 2f }
        composeRule.waitForIdle()
        val streak = composeRule.onNodeWithTag("week-streak-summary").fetchSemanticsNode().boundsInRoot
        val weekdays = composeRule.onNodeWithTag("week-day-indicators").fetchSemanticsNode().boundsInRoot
        assertTrue("Large-text layout stacks week labels below the streak", weekdays.top >= streak.bottom - 1f)
        assertWeekCopyFits(streak, weekdays)
    }

    @Test
    fun weekCardRangeChartSwitchesSelectedDayAndOpensDateRecords() {
        val today = LocalDate.of(2026, 11, 25) // 周三
        val weekDates = naturalWeekDates(today)
        var openedDate: LocalDate? = null
        val state = DashboardUiState(
            today = today,
            week = weekDates.mapIndexed { index, date ->
                when (index) {
                    0 -> DashboardWeekDay(date, averageSystolic = 112, averageDiastolic = 76, sessionCount = 2, recorded = true)
                    1 -> DashboardWeekDay(date, averageSystolic = 111, averageDiastolic = 74, sessionCount = 1, recorded = true)
                    2 -> DashboardWeekDay(date, averageSystolic = 105, averageDiastolic = 70, sessionCount = 3, recorded = true)
                    else -> DashboardWeekDay(date, recorded = false)
                }
            },
            weekAverageSystolic = 109,
            weekAverageDiastolic = 73,
            streakDays = 82,
            showTrendChart = true,
            loading = false
        )

        composeRule.setContent {
            BloodPressureRecordTheme {
                Surface {
                    Column(Modifier.width(360.dp).padding(horizontal = 20.dp)) {
                        WeekCard(state, onOpenDateRecords = { openedDate = it })
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // 保留连续记录 + 右侧显示本周均值，默认选中本周最近有记录日（周三 105/70）
        composeRule.onNodeWithText("82 天", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("连续记录", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("109/73", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("105/70", useUnmergedTree = true).assertIsDisplayed()

        // 点击周一列切换单日详情条，并触发跳转历史指定日期
        composeRule.onNodeWithTag("week-day-column-0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("112/76", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("week-view-day-action").performClick()
        composeRule.waitForIdle()
        assertEquals(weekDates[0], openedDate)
    }

    @Test
    fun weekCardRangeChartFitsAt320DpAndFontScale2() {
        val today = LocalDate.of(2026, 11, 25)
        val weekDates = naturalWeekDates(today)
        val state = DashboardUiState(
            today = today,
            week = weekDates.mapIndexed { index, date ->
                when (index) {
                    0 -> DashboardWeekDay(date, averageSystolic = 142, averageDiastolic = 91, sessionCount = 2, recorded = true)
                    1 -> DashboardWeekDay(date, averageSystolic = 128, averageDiastolic = 82, sessionCount = 2, recorded = true)
                    2 -> DashboardWeekDay(date, averageSystolic = 112, averageDiastolic = 76, sessionCount = 2, recorded = true)
                    else -> DashboardWeekDay(date, recorded = false)
                }
            },
            weekAverageSystolic = 127,
            weekAverageDiastolic = 83,
            streakDays = 82,
            showTrendChart = true,
            loading = false
        )

        setAt320DpFontScale2 {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WeekCard(state)
            }
        }

        val streak = composeRule.onNodeWithTag("week-streak-summary").fetchSemanticsNode().boundsInRoot
        val average = composeRule.onNodeWithTag("week-average-summary").fetchSemanticsNode().boundsInRoot
        assertTrue("Large-text header should stack weekly average below streak", average.top >= streak.bottom - 1f)
        assertTextInside("82 天", streak)
        assertTextInside("连续记录", streak)
        assertTextInside("127/83", average)
        assertTextLayoutFits("112/76")
        assertTextLayoutFits("查看当天")
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
            val node = composeRule.onNodeWithTag("week-day-label-$index", useUnmergedTree = true).assertIsDisplayed()
            node.assertTextEquals(label)
            assertTextLayoutFits("week-day-label-$index")
        }
    }

    @Test
    fun profileGenderSegmentedOptionsStayUncroppedAt320DpAndFontScale2() {
        val repo = Proxy.newProxyInstance(
            SettingsRepository::class.java.classLoader,
            arrayOf(SettingsRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeSettings" -> flowOf(SettingsBundle(userProfile = UserProfile()))
                "clearAllData" -> Result.success(ClearAllDataResult(true, true, true, true))
                "toString" -> "ProfileTestSettingsRepository"
                else -> Unit
            }
        } as SettingsRepository
        val vm = SettingsViewModel(repo)

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BloodPressureRecordTheme {
                    Surface(Modifier.width(320.dp).height(640.dp)) {
                        SettingsProfileScreen(viewModel = vm, onBack = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()

        listOf("男", "女", "不填").forEach { option ->
            composeRule.onNodeWithText(option, useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            composeRule.onNodeWithText(option, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { layout ->
                assertEquals("Option $option should stay on single line", 1, layout.lineCount)
                assertFalse("Option $option must not exceed maxLines", layout.multiParagraph.didExceedMaxLines)
                assertTrue(
                    "Option $option overflows horizontally",
                    layout.getLineRight(0) - layout.getLineLeft(0) <= layout.size.width + 1f
                )
                assertFalse("Option $option must not be ellipsized", layout.isLineEllipsized(0))
            }
        }
    }

    private fun assertWeekCopyFits(streak: androidx.compose.ui.geometry.Rect, weekdays: androidx.compose.ui.geometry.Rect) {
        assertTextInside("21 天", streak)
        assertTextInside("连续记录", streak)
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
            val node = composeRule.onNodeWithTag("week-day-label-$index").assertIsDisplayed()
            node.assertTextEquals(label)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Weekday $label must fit its indicator", bounds.left >= weekdays.left && bounds.right <= weekdays.right)
            assertTextLayoutFits("week-day-label-$index")
        }
    }

    private fun assertTextInside(text: String, parent: androidx.compose.ui.geometry.Rect) {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("$text is clipped on the left", bounds.left >= parent.left - 1f)
        assertTrue("$text is clipped on the right", bounds.right <= parent.right + 1f)
        assertTrue("$text is clipped on the top", bounds.top >= parent.top - 1f)
        assertTrue("$text is clipped on the bottom", bounds.bottom <= parent.bottom + 1f)
        assertTextLayoutFits(text)
    }

    private fun assertTextLayoutFits(text: String) {
        val node = if (text.startsWith("week-day-label-")) {
            composeRule.onNodeWithTag(text, useUnmergedTree = true)
        } else {
            composeRule.onNodeWithText(text, useUnmergedTree = true)
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("No text layout result for $text", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse("Missing lines for $text", layout.multiParagraph.didExceedMaxLines)
            repeat(layout.lineCount) { line ->
                assertTrue("Text overflows horizontally: $text", layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue("Text overflows vertically: $text", layout.getLineBottom(line) <= layout.size.height + 1f)
                assertFalse("Text is ellipsized: $text", layout.isLineEllipsized(line))
            }
        }
    }

    private fun setAt320DpFontScale2(content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BloodPressureRecordTheme {
                    Surface {
                        Column(Modifier.width(320.dp).padding(horizontal = 20.dp)) {
                            content()
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
