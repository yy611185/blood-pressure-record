package com.example.bloodpressurerecord.ui.scan

import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.scan.PhotoSaver
import com.example.bloodpressurerecord.data.scan.ScanPhoto
import com.example.bloodpressurerecord.domain.ocr.OcrBlock
import com.example.bloodpressurerecord.domain.ocr.OcrResult
import com.example.bloodpressurerecord.mlkit.OcrEngine
import com.example.bloodpressurerecord.ui.home.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepository : BloodPressureRepository {
        var lastInput: SaveSessionInput? = null
        var saveResult: Result<String> = Result.success("session-1")
        override fun observeSession(sessionId: String): Flow<SessionRecord?> = flowOf(null)
        override fun observeLatestSessionSummary(): Flow<LatestSessionSummary?> = flowOf(null)
        override fun observeCalendarSessionSummaries(startInclusive: Long, endExclusive: Long) =
            flowOf(emptyList<CalendarSessionSummary>())
        override fun observeSessionSummariesInRange(startInclusive: Long, endExclusive: Long) =
            flowOf(emptyList<SessionSummary>())
        override fun observePeriodStatistics(startInclusive: Long, endExclusive: Long) =
            flowOf(PeriodStatistics())
        override suspend fun saveSession(input: SaveSessionInput): Result<String> {
            lastInput = input
            return saveResult
        }

        override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> =
            Result.success(Unit)

        override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession(session: SessionRecord): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeOcrEngine(var result: OcrResult) : OcrEngine {
        override suspend fun recognize(bitmap: android.graphics.Bitmap): OcrResult = result
    }

    private class FakePhotoSaver : PhotoSaver {
        var saved: MutableList<Pair<String, Int>> = mutableListOf()
        var fail = false
        override suspend fun save(sessionId: String, photos: List<ScanPhoto>): Result<Unit> {
            if (fail) return Result.failure(IllegalStateException("disk full"))
            photos.forEach { saved += sessionId to it.groupNumber }
            return Result.success(Unit)
        }
    }

    private fun threeLineResult(): OcrResult = OcrResult(
        1080, 2400,
        listOf(
            OcrBlock("103", 400f, 700f, 640f, 940f),
            OcrBlock("68", 400f, 1000f, 610f, 1240f),
            OcrBlock("69", 420f, 1320f, 620f, 1520f)
        )
    )

    private fun emptyResult(): OcrResult = OcrResult(1080, 2400, emptyList())

    private fun viewModel(
        repo: FakeRepository = FakeRepository(),
        ocr: FakeOcrEngine = FakeOcrEngine(OcrResult(0, 0, emptyList())),
        saver: FakePhotoSaver = FakePhotoSaver(),
        savePhotos: Boolean = false
    ) = ScanViewModel(
        repository = repo,
        ocrEngine = ocr,
        photoSaver = saver,
        saveScanPhotosEnabled = flowOf(savePhotos),
        discardFirstReading = flowOf(false)
    )

    @Test
    fun `initial state is camera with group 1`() {
        val vm = viewModel()
        assertEquals(ScanPhase.Camera, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.currentGroupNumber)
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `successful recognition adds group and allows save`() = runTest {
        val vm = viewModel(ocr = FakeOcrEngine(threeLineResult()))
        vm.onOcrReady(1, threeLineResult())
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(ScanPhase.Camera, state.phase)
        assertEquals(1, state.groups.size)
        assertEquals("103", state.groups[0].systolic)
        assertTrue(state.groups[0].isRecognized)
        assertTrue(state.message.contains("已识别"))
    }

    @Test
    fun `failed recognition does not occupy a group slot`() = runTest {
        val vm = viewModel(ocr = FakeOcrEngine(emptyResult()))
        vm.onOcrReady(1, emptyResult())
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.groups.isEmpty())
        assertTrue(state.messageIsError)
        assertEquals(ScanPhase.Camera, state.phase)
    }

    @Test
    fun `continue capturing advances to next group`() = runTest {
        val vm = viewModel(ocr = FakeOcrEngine(threeLineResult()))
        vm.onOcrReady(1, threeLineResult())
        advanceUntilIdle()
        vm.continueCapturing()
        assertEquals(2, vm.uiState.value.currentGroupNumber)
        assertEquals(ScanPhase.Camera, vm.uiState.value.phase)
    }

    @Test
    fun `third capture enters review automatically`() = runTest {
        val vm = viewModel(ocr = FakeOcrEngine(threeLineResult()))
        vm.onOcrReady(1, threeLineResult())
        advanceUntilIdle()
        vm.continueCapturing()
        vm.onOcrReady(2, threeLineResult())
        advanceUntilIdle()
        vm.continueCapturing()
        vm.onOcrReady(3, threeLineResult())
        advanceUntilIdle()
        vm.continueCapturing()
        assertEquals(ScanPhase.Review, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `retake replaces existing group`() = runTest {
        val vm = viewModel(ocr = FakeOcrEngine(threeLineResult()))
        vm.onOcrReady(1, threeLineResult())
        advanceUntilIdle()
        vm.retakeGroup(1)
        assertEquals(ScanPhase.Camera, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.message.contains("重拍"))
        val better = OcrResult(
            1080, 2400,
            listOf(
                OcrBlock("120", 400f, 700f, 640f, 940f),
                OcrBlock("80", 400f, 1000f, 610f, 1240f)
            )
        )
        vm.onOcrReady(1, better)
        advanceUntilIdle()
        assertEquals("120", vm.uiState.value.groups[0].systolic)
    }

    @Test
    fun `single group can be saved with minReadings 1`() = runTest {
        val repo = FakeRepository()
        val vm = viewModel(repo = repo, ocr = FakeOcrEngine(threeLineResult()))
        vm.onOcrReady(1, threeLineResult())
        advanceUntilIdle()
        vm.enterReview()
        assertTrue(vm.uiState.value.canSave)
        vm.onSaveClicked()
        advanceUntilIdle()
        assertNotNull(repo.lastInput)
        assertEquals(1, repo.lastInput!!.minReadings)
        assertEquals(1, repo.lastInput!!.readings.size)
        assertTrue(vm.uiState.value.saved)
        assertEquals(ScanPhase.Saved, vm.uiState.value.phase)
    }

    @Test
    fun `photo saver is not called when switch is off`() = runTest {
        val saver = FakePhotoSaver()
        val vm = viewModel(repo = FakeRepository(), saver = saver, savePhotos = false)
        vm.onOcrReady(1, threeLineResult())
        advanceUntilIdle()
        vm.enterReview()
        vm.onSaveClicked()
        advanceUntilIdle()
        assertTrue(saver.saved.isEmpty())
    }

    @Test
    fun `save failure shows error and stays editable`() = runTest {
        val repo = FakeRepository().apply { saveResult = Result.failure(IllegalStateException("db")) }
        val vm = viewModel(repo = repo, ocr = FakeOcrEngine(threeLineResult()))
        vm.onOcrReady(1, threeLineResult())
        advanceUntilIdle()
        vm.enterReview()
        vm.onSaveClicked()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.messageIsError)
        assertFalse(vm.uiState.value.saved)
    }

    @Test
    fun `abnormal values show confirm dialog before saving`() = runTest {
        val repo = FakeRepository()
        val vm = viewModel(repo = repo, ocr = FakeOcrEngine(threeLineResult()))
        val abnormal = OcrResult(
            1080, 2400,
            listOf(
                OcrBlock("120", 400f, 700f, 640f, 940f),
                OcrBlock("70", 400f, 1000f, 610f, 1240f),
                OcrBlock("230", 420f, 1320f, 620f, 1520f)
            )
        )
        vm.onOcrReady(1, abnormal)
        advanceUntilIdle()
        vm.enterReview()
        vm.onSaveClicked()
        assertTrue(vm.uiState.value.showAbnormalConfirmDialog)
        vm.confirmAbnormalAndContinue()
        advanceUntilIdle()
        assertNotNull(repo.lastInput)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `high risk values show confirm dialog before saving`() = runTest {
        val repo = FakeRepository()
        val vm = viewModel(repo = repo, ocr = FakeOcrEngine(threeLineResult()))
        val highRisk = OcrResult(
            1080, 2400,
            listOf(
                OcrBlock("185", 400f, 700f, 640f, 940f),
                OcrBlock("120", 400f, 1000f, 610f, 1240f)
            )
        )
        vm.onOcrReady(1, highRisk)
        advanceUntilIdle()
        vm.enterReview()
        vm.onSaveClicked()
        assertTrue(vm.uiState.value.showHighRiskDialog)
        vm.confirmHighRiskAndContinue()
        advanceUntilIdle()
        assertNotNull(repo.lastInput)
        assertTrue(vm.uiState.value.saved)
    }
}
