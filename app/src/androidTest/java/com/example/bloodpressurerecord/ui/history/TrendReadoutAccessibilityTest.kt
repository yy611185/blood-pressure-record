package com.example.bloodpressurerecord.ui.history

import android.graphics.Bitmap
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
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
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
        renderReadout(width = 288.dp, fontScale = 1f, dark = false)
        captureIfRequested("trend-readout-normal")
        assertTextFitsAndActionsAreReachable()
        val previous = action("上一条").fetchSemanticsNode().boundsInRoot
        val reset = action("恢复").fetchSemanticsNode().boundsInRoot
        assertEquals(previous.top, reset.top, 1f)
    }

    @Test
    fun narrowLargeFontReflowsActionsAndPreservesAllText() {
        // 320dp screen minus page margins (40dp) and chart padding (32dp).
        renderReadout(width = 248.dp, fontScale = 2f, dark = true)
        captureIfRequested("trend-readout-small-large-dark")
        assertTextFitsAndActionsAreReachable()
        val previous = action("上一条").fetchSemanticsNode().boundsInRoot
        val details = action("明细").fetchSemanticsNode().boundsInRoot
        assertTrue(details.top >= previous.bottom)
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
        composeRule.onNodeWithContentDescription("收缩压 125，舒张压 82", substring = true)
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
                            onResetChart = { resetCount++ }
                        )
                    }
                }
            }
        }
    }

    private var selectedId: String? = null
    private var detailId: String? = null
    private var resetCount = 0

    private fun action(text: String) = composeRule.onNode(hasText(text) and hasClickAction())

    private fun assertTextFitsAndActionsAreReachable() {
        listOf("上一条", "下一条", "明细", "恢复").forEach {
            action(it).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
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
        action("恢复").performClick()
        assertEquals(1, resetCount)
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
