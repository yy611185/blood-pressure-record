package com.example.bloodpressurerecord.ui.report

import androidx.lifecycle.ViewModelStore
import com.example.bloodpressurerecord.data.repository.ReportRepository
import com.example.bloodpressurerecord.domain.report.BpReport
import com.example.bloodpressurerecord.domain.report.ReportLayoutPlanner
import com.example.bloodpressurerecord.domain.report.ReportLayoutSpec
import com.example.bloodpressurerecord.domain.report.ReportMeasurement
import com.example.bloodpressurerecord.domain.report.ReportMedication
import com.example.bloodpressurerecord.domain.report.ReportMedicationTime
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
    fun `recent periods include today and reject unsupported presets`() = runTest {
        val vm = model()
        vm.selectRecentPeriod(7)
        assertEquals(LocalDate.of(2026, 10, 5), vm.uiState.value.period.startDate)
        assertEquals(7, vm.uiState.value.period.dayCount)
        assertEquals(7, vm.uiState.value.periodPresetDays)
        vm.selectRecentPeriod(90)
        assertEquals(LocalDate.of(2026, 7, 14), vm.uiState.value.period.startDate)
        assertEquals(LocalDate.of(2026, 10, 11), vm.uiState.value.period.endDate)
        assertEquals(90, vm.uiState.value.period.dayCount)
        assertEquals(90, vm.uiState.value.periodPresetDays)
        val selected = vm.uiState.value.period
        vm.selectRecentPeriod(365)
        assertEquals(selected, vm.uiState.value.period)
        advanceUntilIdle()
    }

    @Test
    fun `custom period accepts inclusive 365 dates and refuses invalid ranges without replacing selection`() = runTest {
        val vm = model()
        val end = LocalDate.of(2026, 10, 11)
        val start = end.minusDays(364)
        assertTrue(vm.selectCustomPeriod(start, end))
        val selected = vm.uiState.value.period
        assertEquals(365, selected.dayCount)
        assertEquals(zone, selected.zoneId)
        assertNull(vm.uiState.value.periodPresetDays)

        listOf(start.minusDays(1) to end, end to start, end to end.plusDays(1)).forEach { (from, to) ->
            assertFalse(vm.selectCustomPeriod(from, to))
            assertEquals(selected, vm.uiState.value.period)
            assertTrue(vm.uiState.value.message.contains("365 天"))
        }
        assertTrue(vm.selectCustomPeriod(end, end))
        assertEquals(1, vm.uiState.value.period.dayCount)
        assertEquals("", vm.uiState.value.message)
        advanceUntilIdle()
    }

    @Test
    fun `changing period removes old preview and prevents saving before new confirmation`() = runTest {
        val generator = FakeReportGenerator()
        val vm = model(generator = generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val previous = requireNotNull(vm.uiState.value.artifact)

        vm.selectRecentPeriod(7)
        assertNull(vm.uiState.value.artifact)
        assertNull(vm.uiState.value.previewOptions)
        assertFalse(vm.uiState.value.canExport)
        assertSame(previous, generator.discarded.single())
        var saves = 0
        vm.save { saves++ }
        advanceUntilIdle()
        assertEquals(0, saves)
    }

    @Test
    fun `preview privacy changes reload selected options and remove protected data`() = runTest {
        val repository = FakeReportRepository(sampleSnapshot().copy(
            measurements = listOf(ReportMeasurement("session", now, 128, 78, 68, note = "私人备注")),
            medications = listOf(ReportMedication(1, "测试药品", "5 mg", true,
                listOf(ReportMedicationTime(1, "08:00", true))))
        ))
        val generator = FakeReportGenerator()
        val vm = model(repository, generator)
        vm.requestPreview()
        vm.setIncludeIdentity(true)
        vm.confirmAndGenerate()
        advanceUntilIdle()
        assertEquals("私人备注", generator.reports.last().notes.single().text)
        assertEquals("测试药品", generator.reports.last().medications.single().name)
        assertEquals(profile, generator.reports.last().profile)

        vm.setIncludeNotes(false)
        assertFalse(vm.uiState.value.canExport)
        advanceUntilIdle()
        assertFalse(repository.requests.last().second.includeNotes)
        assertTrue(generator.reports.last().notes.isEmpty())
        assertTrue(generator.reports.last().days.flatMap { it.morning + it.evening + it.other }.all { it.note == null })

        vm.setIncludeMedications(false)
        advanceUntilIdle()
        assertFalse(repository.requests.last().second.includeMedications)
        assertTrue(generator.reports.last().medications.isEmpty())
        vm.setIncludeIdentity(false)
        advanceUntilIdle()
        assertNull(generator.reports.last().profile)
        assertEquals(ReportOptions(false, false, false), vm.uiState.value.previewOptions)
        assertTrue(vm.uiState.value.canExport)
    }

    @Test
    fun `failed privacy regeneration blocks stale saving until retry succeeds`() = runTest {
        val generator = FakeReportGenerator()
        val vm = model(generator = generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val previous = vm.uiState.value.artifact
        generator.generateAction = { throw IllegalStateException("私人备注 血压 189/112") }

        vm.setIncludeNotes(false)
        var saves = 0
        vm.save { saves++ }
        advanceUntilIdle()
        assertSame(previous, vm.uiState.value.artifact)
        assertFalse(vm.uiState.value.canExport)
        assertEquals("报告生成失败，请重试。", vm.uiState.value.message)
        vm.save { saves++ }
        advanceUntilIdle()
        assertEquals(0, saves)

        generator.generateAction = { artifact(it) }
        vm.regeneratePreview()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canExport)
        assertEquals(vm.uiState.value.options, vm.uiState.value.previewOptions)
        assertSame(previous, generator.discarded.single())
        vm.save { saves++ }
        advanceUntilIdle()
        assertEquals(1, saves)
    }

    @Test
    fun `second privacy change discards cancelled late result and retains newest report`() = runTest {
        val generator = FakeReportGenerator()
        val vm = model(generator = generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val original = requireNotNull(vm.uiState.value.artifact)
        lateinit var completion: Continuation<ReportArtifact>
        generator.generateAction = { suspendCoroutine { completion = it } }
        vm.setIncludeNotes(false)
        runCurrent()
        val cancelledReport = generator.reports.last()
        assertTrue(vm.uiState.value.isGenerating)

        generator.generateAction = { artifact(it) }
        vm.setIncludeMedications(false)
        runCurrent()
        val newest = requireNotNull(vm.uiState.value.artifact)
        assertEquals(ReportOptions(false, false, false), vm.uiState.value.previewOptions)
        assertTrue(vm.uiState.value.canExport)
        val lateArtifact = artifact(cancelledReport)
        completion.resume(lateArtifact)
        advanceUntilIdle()

        assertSame(newest, vm.uiState.value.artifact)
        assertTrue(generator.discarded.any { it === original })
        assertTrue(generator.discarded.any { it === lateArtifact })
        assertFalse(generator.discarded.any { it === newest })
        assertFalse(vm.uiState.value.isGenerating)
    }

    @Test
    fun `manual regeneration failure invalidates matching previous preview`() = runTest {
        val generator = FakeReportGenerator()
        val vm = model(generator = generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val previous = vm.uiState.value.artifact
        assertTrue(vm.uiState.value.canExport)
        generator.generateAction = { throw IllegalStateException("render failure") }
        vm.regeneratePreview()
        assertNull(vm.uiState.value.previewOptions)
        advanceUntilIdle()

        assertSame(previous, vm.uiState.value.artifact)
        assertFalse(vm.uiState.value.hasCurrentPreview)
        assertFalse(vm.uiState.value.canExport)
        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()
        assertFalse(saved)
    }

    @Test
    fun `returning privacy option to old value cannot reenable failed stale preview`() = runTest {
        val generator = FakeReportGenerator()
        val vm = model(generator = generator)
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val previous = vm.uiState.value.artifact
        generator.generateAction = { throw IllegalStateException("render failure") }
        vm.setIncludeNotes(false)
        advanceUntilIdle()
        vm.setIncludeNotes(true)
        advanceUntilIdle()

        assertEquals(ReportOptions(), vm.uiState.value.options)
        assertSame(previous, vm.uiState.value.artifact)
        assertNull(vm.uiState.value.previewOptions)
        assertFalse(vm.uiState.value.canExport)
        var saves = 0
        vm.save { saves++ }
        advanceUntilIdle()
        assertEquals(0, saves)
    }

    @Test
    fun `return to options restores every privacy default and asks identity again`() = runTest {
        val vm = model()
        vm.setIncludeNotes(false)
        vm.setIncludeMedications(false)
        vm.requestPreview()
        vm.setIncludeIdentity(true)
        vm.confirmAndGenerate()
        advanceUntilIdle()
        vm.returnToOptions()

        assertEquals(ReportOptions(), vm.uiState.value.options)
        assertNull(vm.uiState.value.previewOptions)
        assertFalse(vm.uiState.value.canExport)
        vm.requestPreview()
        assertTrue(vm.uiState.value.showIdentityConfirmation)
        assertFalse(vm.uiState.value.options.includeIdentity)
        advanceUntilIdle()
    }

    @Test
    fun `saving disables duplicate output and option changes until completion`() = runTest {
        val vm = model()
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val options = vm.uiState.value.options
        val gate = CompletableDeferred<Unit>()
        var saves = 0
        vm.save { saves++; gate.await() }
        runCurrent()
        assertTrue(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.canExport)
        vm.setIncludeIdentity(true)
        vm.setIncludeNotes(false)
        vm.setIncludeMedications(false)
        vm.selectRecentPeriod(7)
        assertEquals(options, vm.uiState.value.options)
        assertEquals(30, vm.uiState.value.period.dayCount)
        vm.save { saves++ }
        runCurrent()
        assertEquals(1, saves)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canExport)
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
    fun `save callback for previous preview cannot write a newer privacy report`() = runTest {
        val vm = model()
        vm.requestPreview()
        vm.confirmAndGenerate()
        advanceUntilIdle()
        val expected = requireNotNull(vm.uiState.value.artifact)
        vm.setIncludeNotes(false)
        advanceUntilIdle()
        val current = requireNotNull(vm.uiState.value.artifact)
        assertTrue(current !== expected)
        assertTrue(vm.uiState.value.canExport)

        var saves = 0
        vm.save(expectedArtifact = expected) { saves++ }
        advanceUntilIdle()

        assertEquals(0, saves)
        assertSame(current, vm.uiState.value.artifact)
        assertEquals("报告已更改，请重新选择保存位置。", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isSaving)
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
