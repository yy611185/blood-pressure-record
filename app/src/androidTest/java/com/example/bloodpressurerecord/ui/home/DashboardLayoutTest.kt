package com.example.bloodpressurerecord.ui.home

import androidx.compose.foundation.layout.Column
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
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.time.LocalDate
import java.time.ZoneId
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
        composeRule.onNodeWithText("尚未设置服药提醒").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("今天的药都吃过啦，真棒！").assertDoesNotExist()
    }

    @Test
    fun weekCardKeepsStreakAndAllWeekdayLabelsVisibleInLargeText() {
        val sunday = LocalDate.of(2026, 10, 11)
        val state = DashboardUiState(
            today = sunday.plusDays(2),
            week = (0..6).map { DashboardWeekDay(sunday.plusDays(it.toLong()), recorded = it < 3) },
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

    private fun assertWeekCopyFits(streak: androidx.compose.ui.geometry.Rect, weekdays: androidx.compose.ui.geometry.Rect) {
        assertTextInside("21 天", streak)
        assertTextInside("连续记录", streak)
        listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { index, label ->
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
