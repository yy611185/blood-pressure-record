package com.example.bloodpressurerecord.ui.report

import androidx.lifecycle.ViewModelStore
import com.example.bloodpressurerecord.data.repository.ReportRepository
import com.example.bloodpressurerecord.domain.report.BpReport
import com.example.bloodpressurerecord.domain.report.ReportLayoutPlanner
import com.example.bloodpressurerecord.domain.report.ReportLayoutSpec
import com.example.bloodpressurerecord.domain.report.ReportMeasurement
import com.example.bloodpressurerecord.domain.report.ReportOptions
import com.example.bloodpressurerecord.domain.report.ReportPeriod
import com.example.bloodpressurerecord.domain.report.ReportSnapshot
import com.example.bloodpressurerecord.domain.report.ReportTextMetrics
import com.example.bloodpressurerecord.domain.report.ReportUserProfile
import com.example.bloodpressurerecord.ui.home.MainDispatcherRule
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val stores = mutableListOf<ViewModelStore>()

    @After
    fun clearViewModels() {
        stores.forEach { it.clear() }
    }

    @Test
    fun `period is thirty local dates including today`() = runTest {
        val vm = model()
        val period = vm.uiState.value.period
        assertEquals(LocalDate.of(2026, 9, 12), period.startDate)
        assertEquals(LocalDate.of(2026, 10, 11), period.endDate)
        assertEquals(30, period.dayCount)
        assertEquals(zone, period.zoneId)
        advanceUntilIdle()
    }

    @Test
    fun `empty period stops before rendering and explains how to proceed`() = runTest {
        val generator = FakeReportGenerator()
        val vm = model(FakeReportRepository(ReportSnapshot()), generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()

        assertEquals(0, generator.reports.size)
        assertNull(vm.uiState.value.artifact)
        assertFalse(vm.uiState.value.isGenerating)
        assertFalse(vm.uiState.value.showIdentityConfirmation)
        assertTrue(vm.uiState.value.message.contains("请先记录血压"))
    }

    @Test
    fun `identity requires opt in for each export and fresh view model`() = runTest {
        val repository = FakeReportRepository()
        val generator = FakeReportGenerator()
        val vm = model(repository, generator)
        vm.requestPreview()
        assertTrue(vm.uiState.value.showIdentityConfirmation)
        assertFalse(vm.uiState.value.options.includeIdentity)
        vm.setIncludeIdentity(true)
        vm.confirmAndGenerate()
        advanceUntilIdle()

        assertTrue(repository.requests.single().second.includeIdentity)
        assertEquals(profile, generator.reports.single().profile)
        val preview = requireNotNull(vm.uiState.value.artifact)
        vm.returnToOptions()
        assertSame(preview, generator.discarded.single())
        assertNull(vm.uiState.value.artifact)
        assertFalse(vm.uiState.value.options.includeIdentity)

        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        assertFalse(repository.requests.last().second.includeIdentity)
        assertNull(generator.reports.last().profile)
        assertFalse(model().uiState.value.options.includeIdentity)
        advanceUntilIdle()
    }

    @Test
    fun `cancelled identity dialog is not carried into next confirmation`() = runTest {
        val vm = model()
        vm.requestPreview()
        vm.setIncludeIdentity(true)
        vm.dismissIdentityConfirmation()
        assertFalse(vm.uiState.value.showIdentityConfirmation)
        vm.requestPreview()
        assertFalse(vm.uiState.value.options.includeIdentity)
        advanceUntilIdle()
    }

    @Test
    fun `save uses preview artifact without loading new data or generating again`() = runTest {
        val repository = FakeReportRepository()
        val generator = FakeReportGenerator()
        val vm = model(repository, generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val preview = requireNotNull(vm.uiState.value.artifact)
        repository.snapshot = ReportSnapshot()
        var saved: ReportArtifact? = null

        vm.save { saved = it }
        advanceUntilIdle()

        assertSame(preview, saved)
        assertSame(preview, vm.uiState.value.artifact)
        assertEquals(1, repository.requests.size)
        assertEquals(1, generator.reports.size)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals("报告已保存。", vm.uiState.value.message)
    }

    @Test
    fun `save failure does not reveal exception health data or discard preview`() = runTest {
        val vm = model()
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val preview = vm.uiState.value.artifact
        vm.save { throw IllegalStateException("姓名 李某 血压 189/112") }
        advanceUntilIdle()

        assertEquals("保存失败，请重新选择位置后再试。", vm.uiState.value.message)
        assertSame(preview, vm.uiState.value.artifact)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `generation failure does not reveal exception health data`() = runTest {
        val generator = FakeReportGenerator().apply {
            generateAction = { throw IllegalStateException("李某 189/112 次数 30") }
        }
        val vm = model(generator = generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()

        assertEquals("报告生成失败，请重试。", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isGenerating)
        assertNull(vm.uiState.value.artifact)
    }

    @Test
    fun `repeated confirm does not start concurrent generation`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val generator = FakeReportGenerator().apply {
            generateAction = { report -> gate.await(); artifact(report) }
        }
        val repository = FakeReportRepository()
        val vm = model(repository, generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        runCurrent()
        assertTrue(vm.uiState.value.isGenerating)
        vm.confirmAndGenerate()
        vm.requestPreview()
        runCurrent()
        assertEquals(1, repository.requests.size)
        assertEquals(1, generator.reports.size)
        assertFalse(vm.uiState.value.showIdentityConfirmation)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isGenerating)
        assertTrue(vm.uiState.value.artifact != null)
    }

    @Test
    fun `return invalidates pending generation even when generator finishes after cancellation`() = runTest {
        lateinit var completion: Continuation<ReportArtifact>
        val generator = FakeReportGenerator().apply {
            generateAction = { suspendCoroutine { completion = it } }
        }
        val vm = model(generator = generator)
        vm.requestPreview()
        vm.setIncludeIdentity(true)
        vm.confirmAndGenerate()
        runCurrent()
        vm.returnToOptions()
        completion.resume(artifact(generator.reports.single()))
        advanceUntilIdle()

        assertNull(vm.uiState.value.artifact)
        assertFalse(vm.uiState.value.isGenerating)
        assertFalse(vm.uiState.value.options.includeIdentity)
        assertEquals(1, generator.discarded.size)
        assertEquals("", vm.uiState.value.message)
    }

    @Test
    fun `clearing screen discards its unshared preview`() = runTest {
        val generator = FakeReportGenerator()
        val vm = model(generator = generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val preview = requireNotNull(vm.uiState.value.artifact)
        stores.single().clear()
        stores.clear()
        assertSame(preview, generator.discarded.single())
    }

    private fun model(
        repository: FakeReportRepository = FakeReportRepository(),
        generator: FakeReportGenerator = FakeReportGenerator(),
        testDispatcher: TestDispatcher = dispatcher
    ): ReportViewModel = ReportViewModel(
        repository, generator, { now }, zone,
        calculationDispatcher = testDispatcher,
        ioDispatcher = testDispatcher
    ).also { vm ->
        stores += ViewModelStore().apply { put("report", vm) }
    }

    private class FakeReportRepository(var snapshot: ReportSnapshot = sampleSnapshot()) : ReportRepository {
        val requests = mutableListOf<Pair<ReportPeriod, ReportOptions>>()

        override suspend fun loadSnapshot(period: ReportPeriod, options: ReportOptions): ReportSnapshot {
            requests += period to options
            return snapshot
        }
    }

    private class FakeReportGenerator : ReportGenerator {
        val reports = mutableListOf<BpReport>()
        val discarded = mutableListOf<ReportArtifact>()
        var generateAction: suspend (BpReport) -> ReportArtifact = { artifact(it) }

        override suspend fun generate(report: BpReport): ReportArtifact {
            reports += report
            return generateAction(report)
        }

        override fun discard(artifact: ReportArtifact) { discarded += artifact }
        override fun cleanup() = Unit
    }

    private companion object {
        val zone: ZoneId = ZoneId.of("Asia/Taipei")
        val now: Long = LocalDate.of(2026, 10, 11).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val profile = ReportUserProfile("测试姓名", 60)

        fun sampleSnapshot() = ReportSnapshot(
            measurements = listOf(ReportMeasurement("session", now, 128, 78, 68)),
            profile = profile
        )

        fun artifact(report: BpReport): ReportArtifact = ReportArtifact(
            File("血压报告_20260912-20261011.pdf"),
            ReportLayoutPlanner.plan(report, ReportLayoutSpec(), ReportTextMetrics { text, size, _ -> text.length * size })
        )
    }
}
