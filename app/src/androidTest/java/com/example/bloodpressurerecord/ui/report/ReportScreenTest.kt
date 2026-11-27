package com.example.bloodpressurerecord.ui.report

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloodpressurerecord.R
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
import kotlinx.coroutines.CompletableDeferred
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
    private var backDispatcher: OnBackPressedDispatcher? = null

    @After fun clearFixture() {
        instrumentation.runOnMainSync { store.clear() }
        if (root.exists()) root.deleteRecursively()
    }

    @Test fun clearDialogExportShortcutOnlyScrollsToExport() {
        render(fromDataManagement = true)
        composeRule.onNodeWithText("清空所有本地数据").performScrollTo().performClick()
        composeRule.onNodeWithText("先导出备份").performClick()
        composeRule.onNodeWithText("再次确认清空数据").assertDoesNotExist()
        composeRule.onNodeWithTag("exportAndBackupSectionTitle").assertIsDisplayed()
        composeRule.onNodeWithText("选择保存位置").assertDoesNotExist()
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

        // 近 90 天为 2026-07-14 至 2026-10-11；用日期选择器的文本模式输入 2025-07-14。
        composeRule.onNodeWithText("2026年7月14日").performClick()
        val inDatePicker = hasAnyAncestor(hasTestTag("reportDatePicker"))
        val inputModeLabel = context.getString(
            context.resources.getIdentifier("m3c_date_picker_switch_to_input_mode", "string", context.packageName)
        )
        composeRule.onNode(inDatePicker and hasContentDescription(inputModeLabel)).performClick()
        val dateParts = mapOf('y' to "2025", 'M' to "07", 'd' to "14")
        val dateDigits = android.text.format.DateFormat.getDateFormatOrder(context).joinToString("") { dateParts.getValue(it) }
        composeRule.onNode(inDatePicker and hasSetTextAction()).performTextReplacement(dateDigits)
        composeRule.onNodeWithTag("reportDatePickerConfirm").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("2025年7月14日").assertIsDisplayed()

        composeRule.onNodeWithTag("reportCustomPeriodConfirm").performClick()
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
        var enteredState: androidx.compose.runtime.MutableState<Boolean>? = null
        render(fontScale = 2f, fromDataManagement = true, onEnteredStateCreated = { enteredState = it })
        scrollTo("导出医生报告 PDF")
        composeRule.onNodeWithText("导出医生报告 PDF").assertIsDisplayed().performClick()
        scrollTo("近 7 天")
        composeRule.onNodeWithText("近 7 天").performClick()
        beginPreview(includeIdentity = true)
        composeRule.runOnIdle { assertEquals("测试姓名", viewModel.uiState.value.artifact?.plan?.report?.profile?.name) }

        OutputLabels.forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
        scrollTo("重新选择周期")
        composeRule.onNodeWithText("重新选择周期").performClick()
        scrollTo("包含姓名与年龄")
        composeRule.onNodeWithContentDescription("包含姓名与年龄").assertIsOff()
        composeRule.runOnIdle { assertFalse(viewModel.uiState.value.options.includeIdentity) }

        // 勾选身份信息后退出页面再重新进入，必须再次默认关闭且不记忆
        composeRule.onNodeWithContentDescription("包含姓名与年龄").performClick()
        composeRule.onNodeWithContentDescription("包含姓名与年龄").assertIsOn()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        composeRule.onNodeWithText("导出医生报告 PDF").performScrollTo().performClick()
        scrollTo("包含姓名与年龄")
        composeRule.onNodeWithContentDescription("包含姓名与年龄").assertIsOff()
        composeRule.runOnIdle {
            assertFalse(viewModel.uiState.value.options.includeIdentity)
            assertNotNull(enteredState)
        }
    }

    @Test fun privacySwitchesRegenerateNativePdfWithoutNotesAndMedications() {
        render()
        beginPreview()
        val first = viewModel.uiState.value.artifact
        composeRule.runOnIdle {
            assertEquals("测试备注", first?.plan?.report?.notes?.single()?.text)
            assertEquals("测试药品", first?.plan?.report?.medications?.single()?.name)
        }
        // B2 / AUD-29：首屏默认收折为逐项明示真实状态的隐私摘要条
        composeRule.onNodeWithText("姓名与年龄：已隐藏").assertIsDisplayed()
        composeRule.onNodeWithText("备注：已包含").assertIsDisplayed()
        composeRule.onNodeWithText("药品：已包含").assertIsDisplayed()

        scrollTo("调整隐私")
        composeRule.onNodeWithTag("reportPrivacyToggle").performClick()
        scrollTo("包含备注")
        composeRule.onNodeWithContentDescription("包含备注").performClick()
        waitForCurrentPreview()
        scrollTo("包含药品")
        composeRule.onNodeWithContentDescription("包含药品").performClick()
        waitForCurrentPreview()
        composeRule.onNodeWithContentDescription("包含备注").assertIsOff()
        composeRule.onNodeWithContentDescription("包含药品").assertIsOff()
        composeRule.onNodeWithText("备注：已隐藏").assertIsDisplayed()
        composeRule.onNodeWithText("药品：已隐藏").assertIsDisplayed()
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
        OutputLabels.forEach { label ->
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
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasText(PrimaryShareLabel) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(PrimaryShareLabel).assertIsDisplayed().assertIsEnabled()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasText(PrimaryShareLabel) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(PrimaryShareLabel).assertIsDisplayed().assertIsEnabled()
        composeRule.runOnIdle { assertTrue(original === viewModel.uiState.value.artifact) }

        // 新选项产生新匿名文件路径；读过旧摘要不能让新的无效 PDF 获得输出权限。
        generateInvalidPdf = true
        scrollTo("调整隐私")
        composeRule.onNodeWithTag("reportPrivacyToggle").performClick()
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
        OutputLabels.forEach { label ->
            composeRule.onNodeWithText(label).assertIsNotEnabled()
        }
    }

    @Test fun systemBackDuringSavingDoesNotExitPreviewOrDeleteArtifact() {
        var outerBackInvoked = false
        render(onBackOverride = { outerBackInvoked = true })
        beginPreview()
        val artifact = requireNotNull(viewModel.uiState.value.artifact)
        scrollTo("第 1 页")
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasText(PrimaryShareLabel) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        val saveGate = CompletableDeferred<Unit>()
        composeRule.runOnIdle {
            viewModel.save(artifact) {
                saveGate.await()
            }
        }
        composeRule.waitForIdle()
        assertTrue(viewModel.uiState.value.isSaving)

        composeRule.runOnIdle {
            checkNotNull(backDispatcher).onBackPressed()
        }
        composeRule.waitForIdle()
        assertFalse("System back must not fall through while saving", outerBackInvoked)
        composeRule.onNodeWithText("医生报告预览").assertIsDisplayed()
        composeRule.onNodeWithText("正在保存报告，请稍候…").assertIsDisplayed()
        assertTrue(artifact.file.exists())

        saveGate.complete(Unit)
        composeRule.waitUntil(10_000) { !viewModel.uiState.value.isSaving }
        composeRule.onNodeWithText("报告已保存。").assertIsDisplayed()
        composeRule.onNodeWithText(PrimaryShareLabel).assertIsEnabled()
    }

    private fun render(
        fontScale: Float = 1f,
        fromDataManagement: Boolean = false,
        invalidPdf: Boolean = false,
        restoration: StateRestorationTester? = null,
        generatorOverride: ReportGenerator? = null,
        onEnteredStateCreated: ((androidx.compose.runtime.MutableState<Boolean>) -> Unit)? = null,
        onBackOverride: (() -> Unit)? = null
    ) {
        viewModel = ReportViewModel(repository,
            generatorOverride ?: if (invalidPdf) InvalidPdfGenerator() else DefaultReportGenerator(renderer, cache),
            { now }, zone)
        store.put("report-screen", viewModel)
        val entered = mutableStateOf(!fromDataManagement)
        onEnteredStateCreated?.invoke(entered)
        val settings = if (fromDataManagement) SettingsViewModel(fixtureSettings()).also { store.put("settings-screen", it) } else null
        val content: @Composable () -> Unit = {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BloodPressureRecordTheme {
                    Surface {
                        if (entered.value) {
                            ReportScreen(viewModel) {
                                if (fromDataManagement) entered.value = false
                                else onBackOverride?.invoke()
                            }
                        } else {
                            SettingsDataManagementScreen(requireNotNull(settings), {}, { entered.value = true })
                        }
                    }
                }
            }
        }
        if (restoration == null) composeRule.setContent(content) else restoration.setContent(content)
    }

    private fun beginPreview(includeIdentity: Boolean = false) {
        composeRule.waitUntil(5_000) { viewModel.uiState.value.hasProfileIdentity }
        scrollTo("包含姓名与年龄")
        composeRule.onNodeWithContentDescription("包含姓名与年龄").assertIsOff()
        if (includeIdentity) {
            composeRule.onNodeWithContentDescription("包含姓名与年龄").performClick()
            composeRule.onNodeWithContentDescription("包含姓名与年龄").assertIsOn()
        }
        scrollTo("生成预览")
        composeRule.onNodeWithText("生成预览").performClick()
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
        override suspend fun hasProfileIdentity(): Boolean = true
        override suspend fun loadSnapshot(period: ReportPeriod, options: ReportOptions): ReportSnapshot {
            requests += options
            return ReportSnapshot(
                measurements = listOf(ReportMeasurement("fixture", now, 128, 78, 68, note = "测试备注")),
                profile = if (options.includeIdentity) ReportUserProfile("测试姓名", 60) else null,
                medications = if (options.includeMedications) {
                    listOf(ReportMedication(1, "测试药品", "5 mg", true,
                        listOf(ReportMedicationTime(1, "08:00", true))))
                } else {
                    emptyList()
                }
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
        const val PrimaryShareLabel = "分享 PDF（发给医生/家人）"
        val OutputLabels = listOf(PrimaryShareLabel, "存文件", "打印", "存摘要图")
        val zone: ZoneId = ZoneId.of("Asia/Taipei")
        val now: Long = LocalDate.of(2026, 10, 11).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
    }
}
