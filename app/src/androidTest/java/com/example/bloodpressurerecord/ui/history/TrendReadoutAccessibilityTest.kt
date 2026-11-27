package com.example.bloodpressurerecord.ui.history

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.ui.common.FocusRingVisibleKey
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrendReadoutAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fiveRangesFitNormalPhoneWidth() {
        renderRangeControl(width = 280.dp, fontScale = 1f, dark = false)
        TrendRange.entries.forEach { range ->
            composeRule.onNodeWithText(range.label).assertIsDisplayed()
        }
        captureIfRequested("trend-ranges-normal")
        assertRangesAreReachable(expectSingleRow = true)
    }

    @Test
    fun fiveRangesStayReadableAndReachableWithLargeFont() {
        renderRangeControl(width = 280.dp, fontScale = 2f, dark = true)
        captureIfRequested("trend-ranges-small-large-dark")
        assertRangesAreReachable(expectSingleRow = false)
    }

    private fun renderRangeControl(width: Dp, fontScale: Float, dark: Boolean) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BloodPressureRecordTheme(darkTheme = dark) {
                    val selected = androidx.compose.runtime.remember { mutableStateOf(TrendRange.DAYS_30) }
                    Surface(Modifier.width(width).testTag("readout")) {
                        TrendRangeControl(selected.value, { selected.value = it })
                    }
                }
            }
        }
    }

    private fun assertRangesAreReachable(expectSingleRow: Boolean) {
        val containerBounds = composeRule.onNodeWithTag("readout").fetchSemanticsNode().boundsInRoot
        val tops = mutableListOf<Float>()
        TrendRange.entries.forEach { range ->
            val node = composeRule.onNodeWithText(range.label)
            node.assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
                .performClick()
                .assertIsSelected()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Range '${range.label}' overflows left bound: ${bounds.left} < ${containerBounds.left}",
                bounds.left >= containerBounds.left - 1f
            )
            assertTrue(
                "Range '${range.label}' overflows right bound: ${bounds.right} > ${containerBounds.right}",
                bounds.right <= containerBounds.right + 1f
            )
            tops += bounds.top
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(range.label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { layout ->
                assertEquals(1, layout.lineCount)
                assertTrue("Text exceeds width: ${layout.layoutInput.text}",
                    layout.getLineRight(0) - layout.getLineLeft(0) <= layout.size.width + 1f)
                assertTrue("Text exceeds height: ${layout.layoutInput.text}",
                    layout.getLineBottom(0) <= layout.size.height + 1f)
                assertFalse(layout.isLineEllipsized(0))
            }
        }
        val distinctRowTops = mutableListOf<Float>()
        tops.sorted().forEach { top ->
            if (distinctRowTops.none { kotlin.math.abs(it - top) <= 2f }) {
                distinctRowTops += top
            }
        }
        if (expectSingleRow) {
            assertEquals("1.0x ranges must fit in a single row", 1, distinctRowTops.size)
        } else {
            assertTrue(
                "2.0x ranges must form a balanced 2..3 row grid instead of a 5-row tower (rows=${distinctRowTops.size})",
                distinctRowTops.size in 2..3
            )
        }
    }

    private val points = (0..2).map { index ->
        TrendPoint(
            id = "reading-$index",
            timestamp = 1_790_340_000_000L + index * 60_000,
            intervalStart = 1_790_340_000_000L + index * 60_000,
            intervalEndExclusive = 1_790_340_000_001L + index * 60_000,
            systolic = 185,
            diastolic = 112,
            pulse = 100,
            category = "STAGE3",
            containsHighRiskReading = true,
            recordCount = 1,
            aggregation = TrendAggregation.RAW
        )
    }

    @Test
    fun normalFontKeepsCompactActionsWithoutClipping() {
        // 360dp 窄屏扣两侧 20dp 页边距与 TrendCard 内 16dp*2 内边距后可用宽度为 288dp。
        renderReadout(width = 288.dp, fontScale = 1f, dark = false)
        captureIfRequested("trend-readout-normal")
        assertTextFitsAndActionsAreReachable(expectSingleRow = true)
    }

    @Test
    fun widePhoneNormalFontKeepsActionsOnOneLine() {
        renderReadout(width = 321.dp, fontScale = 1f, dark = false)
        assertTextFitsAndActionsAreReachable(expectSingleRow = true)
    }

    @Test
    fun largeFontWrapsActionsAt360dp() {
        renderReadout(width = 288.dp, fontScale = 2f, dark = true)
        assertTextFitsAndActionsAreReachable(expectSingleRow = false)
    }

    @Test
    fun largeFontWrapsActionsAt320dp() {
        renderReadout(width = 248.dp, fontScale = 2f, dark = true)
        captureIfRequested("trend-readout-small-large-dark")
        assertTextFitsAndActionsAreReachable(expectSingleRow = false)
    }

    @Test
    fun readoutActionsReflectDisabledBoundsAndKeyboardFocusRing() {
        val currentSelection = mutableStateOf(points.first())
        composeRule.setContent {
            BloodPressureRecordTheme {
                Surface(modifier = Modifier.width(288.dp).testTag("readout")) {
                    TrendSelectionReadout(
                        points = points,
                        selectedPoint = currentSelection.value,
                        onPointSelected = { if (it != null) currentSelection.value = it },
                        onViewDayRecords = {},
                        onResetChart = {},
                        onLatest = {}
                    )
                }
            }
        }
        action("上一条").assertIsNotEnabled()
        action("下一条").assertIsEnabled()

        composeRule.runOnIdle { currentSelection.value = points.last() }
        action("上一条").assertIsEnabled()
        action("下一条").assertIsNotEnabled()

        val resetNode = action("复位视野")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        resetNode.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        resetNode.assertIsFocused().assert(SemanticsMatcher.expectValue(FocusRingVisibleKey, true))
    }

    @Test
    fun selectedReadingAndAccessibilityDescriptionFollowUpdatedValues() {
        val currentPoints = mutableStateOf(points)
        composeRule.setContent {
            BloodPressureRecordTheme {
                TrendSelectionReadout(
                    points = currentPoints.value,
                    selectedPoint = points[1],
                    onPointSelected = {},
                    onViewDayRecords = {},
                    onResetChart = {}
                )
            }
        }
        composeRule.runOnIdle {
            currentPoints.value = points.map {
                if (it.id == points[1].id) it.copy(systolic = 125, diastolic = 82) else it
            }
        }
        composeRule.onNodeWithText("125/82 mmHg", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("高压 125，低压 82", substring = true)
            .assertIsDisplayed()
    }

    private fun renderReadout(width: Dp, fontScale: Float, dark: Boolean) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BloodPressureRecordTheme(darkTheme = dark) {
                    Surface(
                        modifier = Modifier.width(width).testTag("readout"),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TrendSelectionReadout(
                            points = points,
                            selectedPoint = points[1],
                            onPointSelected = { selectedId = it?.id },
                            onViewDayRecords = { detailId = it.id },
                            onResetChart = { resetCount++ },
                            onLatest = { latestCount++ }
                        )
                    }
                }
            }
        }
    }

    private var selectedId: String? = null
    private var detailId: String? = null
    private var resetCount = 0
    private var latestCount = 0

    private fun action(text: String) = composeRule.onNode(hasText(text) and hasClickAction())

    private fun assertTextFitsAndActionsAreReachable(expectSingleRow: Boolean) {
        val containerBounds = composeRule.onNodeWithTag("readout").fetchSemanticsNode().boundsInRoot
        val actionLabels = listOf("上一条", "下一条", "明细", "最新", "复位视野")
        val tops = actionLabels.map { label ->
            val node = action(label)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Action '$label' overflows left bound: ${bounds.left} < ${containerBounds.left}",
                bounds.left >= containerBounds.left - 1f
            )
            assertTrue(
                "Action '$label' overflows right bound: ${bounds.right} > ${containerBounds.right}",
                bounds.right <= containerBounds.right + 1f
            )
            bounds.top
        }
        if (expectSingleRow) {
            val firstTop = tops.first()
            tops.forEach { top ->
                assertEquals("1.0x actions must stay on one line", firstTop, top, 1f)
            }
        } else {
            assertTrue(
                "2.0x actions should wrap naturally into multiple rows",
                (tops.maxOrNull() ?: 0f) - (tops.minOrNull() ?: 0f) > 1f
            )
        }
        val textNodes = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true
        )
        repeat(textNodes.fetchSemanticsNodes().size) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            textNodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            layouts.forEach { layout ->
                // Compose can retain a paragraph's wider measurement constraint even when
                // its final Text size wraps to the glyphs. Check the actual line bounds.
                assertFalse(
                    "Missing lines: ${layout.layoutInput.text}",
                    layout.multiParagraph.didExceedMaxLines
                )
                repeat(layout.lineCount) { line ->
                    assertTrue("Text exceeds width: ${layout.layoutInput.text}",
                        layout.getLineLeft(line) >= -1f &&
                            layout.getLineRight(line) <= layout.size.width + 1f)
                    assertTrue("Text exceeds height: ${layout.layoutInput.text}",
                        layout.getLineBottom(line) <= layout.size.height + 1f)
                    assertFalse(layout.isLineEllipsized(line))
                }
            }
        }
        action("上一条").performClick()
        assertEquals(points[0].id, selectedId)
        action("下一条").performClick()
        assertEquals(points[2].id, selectedId)
        action("明细").performClick()
        assertEquals(points[1].id, detailId)
        action("复位视野").performClick()
        assertEquals(1, resetCount)
        action("最新").performClick()
        assertEquals(1, latestCount)
    }

    private fun captureIfRequested(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureTrendReview") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "$name.png").outputStream().use { output ->
            composeRule.onNodeWithTag("readout").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
