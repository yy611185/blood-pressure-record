package com.example.bloodpressurerecord.ui.report

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloodpressurerecord.data.repository.ReportRepository
import com.example.bloodpressurerecord.data.repository.SettingsBundle
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.domain.report.BpReport
import com.example.bloodpressurerecord.domain.report.ReportLayoutPlanner
import com.example.bloodpressurerecord.domain.report.ReportMeasurement
import com.example.bloodpressurerecord.domain.report.ReportMedication
import com.example.bloodpressurerecord.domain.report.ReportMedicationTime
import com.example.bloodpressurerecord.domain.report.ReportOptions
import com.example.bloodpressurerecord.domain.report.ReportPeriod
import com.example.bloodpressurerecord.domain.report.ReportSnapshot
import com.example.bloodpressurerecord.domain.report.ReportUserProfile
import com.example.bloodpressurerecord.export.report.PdfReportRenderer
import com.example.bloodpressurerecord.export.report.ReportCache
import com.example.bloodpressurerecord.export.report.ReportTheme
import com.example.bloodpressurerecord.ui.settings.SettingsDataManagementScreen
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 固定测试数据驱动真实 Compose 流程，不启动分享、文件选择器或打印服务。 */
class ReportScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val store = ViewModelStore()
    private val root by lazy { File(context.cacheDir, "reports/report-screen-test-${UUID.randomUUID()}") }
    private val renderer by lazy { PdfReportRenderer("血压追踪") }
    private val cache by lazy { ReportCache(root) }
    private val repository = FixtureRepository()
    private lateinit var viewModel: ReportViewModel

    @After fun clearFixture() {
        instrumentation.runOnMainSync { store.clear() }
        if (root.exists()) root.deleteRecursively()
    }

    @Test fun invalidCustomDatesKeepDialogOpenAndNeverGenerate() {
        render()
        scrollTo("近 7 天")
        composeRule.onNodeWithText("近 7 天").performClick()
        composeRule.runOnIdle { assertEquals(7, viewModel.uiState.value.period.dayCount) }
        scrollTo("近 90 天")
        composeRule.onNodeWithText("近 90 天").performClick()
        scrollTo("自定义")
        composeRule.onNodeWithText("自定义").performClick()
        composeRule.onNodeWithText("开始日期").performTextReplacement("2025-10-11")
        composeRule.onNodeWithText("结束日期").performTextReplacement("2026-10-11")
        composeRule.onNodeWithText("确定").performClick()
        composeRule.onNodeWithText("自定义统计周期").assertIsDisplayed()
        composeRule.onNode(
            hasText("请选择不晚于今天的有效日期范围，最多 365 天。") and hasAnyAncestor(isDialog())
        ).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(90, viewModel.uiState.value.period.dayCount)
            assertTrue(repository.requests.isEmpty())
            assertFalse(viewModel.uiState.value.isGenerating)
        }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("导出医生报告").assertIsDisplayed()
    }

    @Test fun entryPeriodAndOutputActionsRemainReachableWithMaximumTextAndIdentityResets() {
        render(fontScale = 2f, fromDataManagement = true)
        scrollTo("导出医生报告 PDF")
        composeRule.onNodeWithText("导出医生报告 PDF").assertIsDisplayed().performClick()
        scrollTo("近 7 天")
        composeRule.onNodeWithText("近 7 天").performClick()
        beginPreview(includeIdentity = true)
        composeRule.runOnIdle { assertEquals("测试姓名", viewModel.uiState.value.artifact?.plan?.report?.profile?.name) }

        listOf("分享 PDF", "保存到文件", "打印报告", "保存摘要图片").forEach { label ->
            scrollTo(label)
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
        scrollTo("重新选择周期")
        composeRule.onNodeWithText("重新选择周期").performClick()
        scrollTo("生成预览")
        composeRule.onNodeWithText("生成预览").performClick()
        composeRule.onNodeWithText("是否包含姓名和年龄？").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOff()
        composeRule.runOnIdle { assertFalse(viewModel.uiState.value.options.includeIdentity) }
        composeRule.onNodeWithText("取消").performClick()
    }

    @Test fun privacySwitchesRegenerateNativePdfWithoutNotesAndMedications() {
        render()
        beginPreview()
        val first = viewModel.uiState.value.artifact
        composeRule.runOnIdle {
            assertEquals("测试备注", first?.plan?.report?.notes?.single()?.text)
            assertEquals("测试药品", first?.plan?.report?.medications?.single()?.name)
        }

        scrollTo("包含备注")
        composeRule.onNodeWithContentDescription("包含备注").performClick()
        waitForCurrentPreview()
        scrollTo("包含药品")
        composeRule.onNodeWithContentDescription("包含药品").performClick()
        waitForCurrentPreview()
        composeRule.onNodeWithContentDescription("包含备注").assertIsOff()
        composeRule.onNodeWithContentDescription("包含药品").assertIsOff()
        composeRule.runOnIdle {
            val artifact = requireNotNull(viewModel.uiState.value.artifact)
            assertTrue(artifact !== first)
            assertTrue(artifact.file.exists())
            assertTrue(artifact.plan.report.notes.isEmpty())
            assertTrue(artifact.plan.report.medications.isEmpty())
            assertEquals(ReportOptions(false, false, false), repository.requests.last())
            assertEquals(repository.requests.last(), viewModel.uiState.value.previewOptions)
        }
    }

    @Test fun failedPdfPreviewKeepsEveryOutputDisabled() {
        render(invalidPdf = true)
        beginPreview()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasText("此页预览失败，请重新生成。"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        listOf("分享 PDF", "保存到文件", "打印报告", "保存摘要图片").forEach { label ->
            scrollTo(label)
            composeRule.onNodeWithText(label).assertIsNotEnabled()
        }
        composeRule.runOnIdle { assertNotNull(viewModel.uiState.value.artifact) }
    }

    @Test fun restoredPreviewKeepsSameFileReadyButNewFailedPrivacyPreviewCannotInheritReadiness() {
        val restoration = StateRestorationTester(composeRule)
        var generateInvalidPdf = false
        val nativeGenerator = DefaultReportGenerator(renderer, cache)
        val invalidGenerator = InvalidPdfGenerator()
        val generator = object : ReportGenerator {
            override suspend fun generate(report: BpReport): ReportArtifact =
                if (generateInvalidPdf) invalidGenerator.generate(report) else nativeGenerator.generate(report)
            override fun discard(artifact: ReportArtifact) = cache.discard(artifact.file)
            override fun cleanup() = cache.cleanup()
        }
        render(fontScale = 2f, restoration = restoration, generatorOverride = generator)
        beginPreview()
        val original = requireNotNull(viewModel.uiState.value.artifact)
        assertEquals(2, original.plan.pageCount)
        scrollTo("第 1 页")
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasContentDescription("报告第 1 页")).fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("分享 PDF")
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasText("分享 PDF") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("分享 PDF").assertIsDisplayed().assertIsEnabled()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("分享 PDF").assertIsDisplayed().assertIsEnabled()
        composeRule.runOnIdle { assertTrue(original === viewModel.uiState.value.artifact) }

        // 新选项产生新匿名文件路径；读过旧摘要不能让新的无效 PDF 获得输出权限。
        generateInvalidPdf = true
        scrollTo("包含备注")
        composeRule.onNodeWithContentDescription("包含备注").performClick()
        waitForCurrentPreview()
        composeRule.runOnIdle {
            assertTrue(original.file.absolutePath != viewModel.uiState.value.artifact?.file?.absolutePath)
        }
        scrollTo("第 1 页")
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasText("此页预览失败，请重新生成。")).fetchSemanticsNodes().isNotEmpty()
        }
        listOf("分享 PDF", "保存到文件", "打印报告", "保存摘要图片").forEach { label ->
            scrollTo(label)
            composeRule.onNodeWithText(label).assertIsNotEnabled()
        }
    }

    private fun render(
        fontScale: Float = 1f,
        fromDataManagement: Boolean = false,
        invalidPdf: Boolean = false,
        restoration: StateRestorationTester? = null,
        generatorOverride: ReportGenerator? = null
    ) {
        viewModel = ReportViewModel(repository,
            generatorOverride ?: if (invalidPdf) InvalidPdfGenerator() else DefaultReportGenerator(renderer, cache),
            { now }, zone)
        store.put("report-screen", viewModel)
        val entered = mutableStateOf(!fromDataManagement)
        val settings = if (fromDataManagement) SettingsViewModel(fixtureSettings()).also { store.put("settings-screen", it) } else null
        val content: @Composable () -> Unit = {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BloodPressureRecordTheme {
                    Surface {
                        if (entered.value) ReportScreen(viewModel) {}
                        else SettingsDataManagementScreen(requireNotNull(settings), {}, { entered.value = true })
                    }
                }
            }
        }
        if (restoration == null) composeRule.setContent(content) else restoration.setContent(content)
    }

    private fun beginPreview(includeIdentity: Boolean = false) {
        scrollTo("生成预览")
        composeRule.onNodeWithText("生成预览").performClick()
        composeRule.onNodeWithText("是否包含姓名和年龄？").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOff()
        if (includeIdentity) composeRule.onNode(isToggleable()).performClick()
        composeRule.onNode(hasText("生成预览") and hasAnyAncestor(isDialog())).performClick()
        waitForCurrentPreview()
    }

    private fun waitForCurrentPreview() {
        composeRule.waitUntil(20_000) {
            viewModel.uiState.value.let { it.hasCurrentPreview && !it.isGenerating }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("医生报告预览").assertIsDisplayed()
    }

    private fun scrollTo(label: String) {
        if (viewModel.uiState.value.artifact == null) {
            composeRule.onNodeWithText(label).performScrollTo()
        } else {
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        }
    }

    private fun fixtureSettings() = Proxy.newProxyInstance(
        SettingsRepository::class.java.classLoader, arrayOf(SettingsRepository::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "observeSettings" -> flowOf(SettingsBundle())
            "toString" -> "ReportScreenFixtureSettings"
            else -> error("Unexpected settings call: ${method.name}")
        }
    } as SettingsRepository

    private class FixtureRepository : ReportRepository {
        val requests = mutableListOf<ReportOptions>()
        override suspend fun loadSnapshot(period: ReportPeriod, options: ReportOptions): ReportSnapshot {
            requests += options
            return ReportSnapshot(
                measurements = listOf(ReportMeasurement("fixture", now, 128, 78, 68, note = "测试备注")),
                profile = ReportUserProfile("测试姓名", 60),
                medications = listOf(ReportMedication(1, "测试药品", "5 mg", true,
                    listOf(ReportMedicationTime(1, "08:00", true))))
            )
        }
    }

    private inner class InvalidPdfGenerator : ReportGenerator {
        override suspend fun generate(report: BpReport): ReportArtifact {
            val file = cache.create(report.period)
            file.writeText("invalid-pdf fixture")
            return ReportArtifact(file, ReportLayoutPlanner.plan(report, ReportTheme.layoutSpec, renderer.textMetrics()))
        }
        override fun discard(artifact: ReportArtifact) = cache.discard(artifact.file)
        override fun cleanup() = cache.cleanup()
    }

    private companion object {
        val zone: ZoneId = ZoneId.of("Asia/Taipei")
        val now: Long = LocalDate.of(2026, 10, 11).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
    }
}
