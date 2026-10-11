package com.example.bloodpressurerecord.ui.home

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.SessionReading
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.domain.calculator.LocateBpZoneUseCase
import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import com.example.bloodpressurerecord.ui.settings.SettingsScreen
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BpRangeBarTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun emptyAnnouncesInstructionWithoutEditAction() {
        composeRule.setContent {
            BloodPressureRecordTheme { Surface { BpRangeBar(BpRangeUiState.Empty) } }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.bp_range_empty_description)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.bp_range_edit_hint)).assertDoesNotExist()
    }

    @Test
    fun readyAnnouncesNumbersAndZoneWithoutEditAction() {
        val state = ready(119, 75)
        composeRule.setContent {
            BloodPressureRecordTheme { Surface { BpRangeBar(state) } }
        }
        composeRule.onNodeWithContentDescription(description(state)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.bp_range_edit_hint)).assertDoesNotExist()
    }

    @Test
    fun allRequestedStatesRenderInBothThemesOnNarrowWidthWithLargeText() {
        val states = listOf(BpRangeUiState.Empty, ready(119, 75), ready(130, 85), ready(160, 100))
        val current = mutableStateOf<BpRangeUiState>(states.first())
        val dark = mutableStateOf(false)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                BloodPressureRecordTheme(darkTheme = dark.value) {
                    Surface(Modifier.width(320.dp)) {
                        BpRangeBar(current.value, Modifier.testTag("bp-range"))
                    }
                }
            }
        }
        listOf(false, true).forEach { darkTheme ->
            states.forEachIndexed { index, state ->
                composeRule.runOnIdle { dark.value = darkTheme; current.value = state }
                composeRule.onNodeWithContentDescription(description(state)).assertIsDisplayed()
                composeRule.onNodeWithTag("bp-range").assertIsDisplayed()
                captureIfRequested("bp-range-${if (darkTheme) "dark" else "light"}-$index")
            }
        }
    }

    @Test
    fun disabledSystemAnimationsMoveMarkerDirectlyToNewPosition() {
        val originalScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        setAnimationScale(0f)
        try {
            val current = mutableStateOf<BpRangeUiState>(ready(119, 75))
            composeRule.setContent {
                BloodPressureRecordTheme {
                    Surface(Modifier.width(320.dp)) {
                        BpRangeBar(current.value, Modifier.testTag("bp-range"))
                    }
                }
            }
            composeRule.mainClock.autoAdvance = false
            composeRule.runOnIdle { current.value = ready(250, 150) }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            val bitmap = composeRule.onNodeWithTag("bp-range").captureToImage().asAndroidBitmap()
            // 在三角形靠底部的位置取像素：直接位于98%，无需等待400ms动画。
            val pxPerDp = bitmap.width / 320f
            val pixel = bitmap.getPixel((bitmap.width * .98f).toInt(), (18f * pxPerDp).toInt())
            assertEquals(0xFF27201B.toInt(), pixel)
            assertTrue(composeRule.mainClock.currentTime < 400L)
        } finally {
            composeRule.mainClock.autoAdvance = true
            setAnimationScale(originalScale)
        }
    }

    @Test
    fun editorFieldsAndSaveActionRemainReachableWithLargeText() {
        var saved = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                BloodPressureRecordTheme {
                    BpZoneTargetSheet(
                        BpZoneTargetEditorUiState(BpZoneTargetDraft.from(BpZoneConfig.Default)),
                        onChange = { _, _, _ -> },
                        onSave = { saved = true },
                        onDismiss = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.bp_zone_config_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.bp_zone_config_save))
            .performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(saved)
    }

    @Test
    fun settingsEntryOpensPersistedRangeEditor() {
        val container = (context.applicationContext as BloodPressureApplication).appContainer
        val viewModel = SettingsViewModel(container.settingsRepository, zoneConfigRepository = container.bpZoneConfigRepository)
        val store = ViewModelStore().apply { put("range-settings", viewModel) }
        try {
            composeRule.setContent {
                BloodPressureRecordTheme {
                    Box(Modifier.testTag("settings-capture")) {
                        SettingsScreen(viewModel, {}, {}, {}, {}, {})
                    }
                }
            }
            composeRule.onNodeWithText("血压目标范围").performScrollTo().assertIsDisplayed().performClick()
            composeRule.onNodeWithText(context.getString(R.string.bp_zone_config_title)).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.bp_zone_config_cancel)).performScrollTo().performClick()
            composeRule.onNodeWithText(context.getString(R.string.bp_zone_config_title)).assertDoesNotExist()
            captureIfRequested("settings-entry", "settings-capture")
        } finally {
            composeRule.runOnIdle { store.clear() }
        }
    }

    @Test
    fun heroKeepsPulseOnRightAndFullPressureVisibleOnSmallScreensAndLargeText() {
        val width = mutableStateOf(390.dp)
        val fontScale = mutableStateOf(1f)
        val dark = mutableStateOf(false)
        val measuredAt = LocalDate.now().atTime(13, 41).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state = mutableStateOf(DashboardUiState(
            latest = LatestSessionSummary("hero", measuredAt, 133, 83, "HIGH_NORMAL", false),
            latestSession = SessionRecord(
                "hero", measuredAt, "下午", null, emptyList(), 133, 83, 67, "HIGH_NORMAL", false,
                listOf(SessionReading("first", 0, 133, 83, 67), SessionReading("second", 1, 133, 83, 67))
            ),
            bpRange = ready(133, 83), loading = false
        ))
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                BloodPressureRecordTheme(darkTheme = dark.value) {
                    Surface {
                        Column(Modifier.width(width.value).testTag("hero-capture").padding(20.dp)) {
                            ReadingHero(state.value)
                        }
                    }
                }
            }
        }
        listOf(Triple(390.dp, 1f, false), Triple(320.dp, 1f, false), Triple(320.dp, 1.5f, true))
            .forEach { (screenWidth, scale, darkTheme) ->
                composeRule.runOnIdle { width.value = screenWidth; fontScale.value = scale; dark.value = darkTheme }
                val pressure = composeRule.onNodeWithText("133/83", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val pulse = composeRule.onNodeWithText("67 次/分", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Pulse must sit to the right of pressure", pulse.left >= pressure.right)
                assertTrue("Pulse must share the reading row", pulse.top < pressure.bottom && pulse.bottom > pressure.top)
                composeRule.onNodeWithText("正常高值").assertDoesNotExist()
                composeRule.onNodeWithText(context.getString(R.string.bp_range_edit_hint)).assertDoesNotExist()
                assertHeroTextFits()
                captureIfRequested("hero-${screenWidth.value.toInt()}-${scale}-${if (darkTheme) "dark" else "light"}", "hero-capture")
            }
        composeRule.runOnIdle {
            state.value = state.value.copy(
                latest = state.value.latest!!.copy(avgSystolic = 185, avgDiastolic = 121, category = "STAGE3", containsHighRiskReading = true),
                latestSession = state.value.latestSession!!.copy(avgSystolic = 185, avgDiastolic = 121, avgPulse = null),
                bpRange = ready(185, 121)
            )
        }
        composeRule.onNodeWithText("185/121", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("67 次/分", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("高风险读数").assertIsDisplayed()
        assertHeroTextFits()
        captureIfRequested("hero-320-large-dark-risk-no-pulse", "hero-capture")
    }

    private fun assertHeroTextFits() {
        val textNodes = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true
        )
        repeat(textNodes.fetchSemanticsNodes().size) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            textNodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { layout ->
                assertFalse("Missing lines: ${layout.layoutInput.text}", layout.multiParagraph.didExceedMaxLines)
                repeat(layout.lineCount) { line ->
                    assertTrue("Clipped width: ${layout.layoutInput.text}", layout.getLineRight(line) <= layout.size.width + 1f)
                    assertTrue("Clipped height: ${layout.layoutInput.text}", layout.getLineBottom(line) <= layout.size.height + 1f)
                    assertFalse(layout.isLineEllipsized(line))
                }
            }
        }
    }

    private fun ready(sys: Int, dia: Int): BpRangeUiState.Ready {
        val position = checkNotNull(LocateBpZoneUseCase()(sys, dia, BpZoneConfig.Default))
        return BpRangeUiState.Ready(sys, dia, position, position.zone.labelRes())
    }

    private fun description(state: BpRangeUiState): String = when (state) {
        BpRangeUiState.Empty -> context.getString(R.string.bp_range_empty_description)
        is BpRangeUiState.Ready -> context.getString(R.string.bp_range_description, state.sys, state.dia, context.getString(state.zoneLabelRes))
    }

    private fun setAnimationScale(scale: Float) {
        instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale $scale")
            .use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
    }

    private fun captureIfRequested(name: String, tag: String = "bp-range") {
        if (InstrumentationRegistry.getArguments().getString("captureBpRangeReview") != "true") return
        File(context.cacheDir, "$name.png").outputStream().use { output ->
            composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
