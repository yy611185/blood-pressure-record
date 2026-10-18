package com.example.bloodpressurerecord.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import com.example.bloodpressurerecord.ui.home.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val owners = mutableMapOf<UpdateViewModel, ViewModelStore>()
    private val manifest = UpdateManifest.parse(requireNotNull(javaClass.getResource("/update/stable-schema1.json")).readText())

    @After
    fun clearModels() {
        owners.values.forEach { it.clear() }
    }

    @Test fun `optional browser reminder survives foreground return and rebuilding from disk`() = runTest {
        assertBrowserRoundTrip(manifest)
    }

    @Test fun `necessary browser reminder survives foreground return and rebuilding from disk`() = runTest {
        assertBrowserRoundTrip(manifest.copy(minSupportedVersionCode = 44))
    }

    @Test fun `browser hides reminder immediately while datastore write is still queued`() = runTest {
        val fixture = fixture()
        fixture.preferences.recordSuccessfulCheck(manifest, now())
        val vm = model(fixture.preferences)
        vm.onForegroundChanged(true)
        vm.setPromptAllowed(true)
        runCurrent()
        assertTrue(vm.uiState.value.dialogVisible)

        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        backgroundScope.launch {
            fixture.store.updateData {
                writeStarted.complete(Unit)
                releaseWrite.await()
                it
            }
        }
        writeStarted.await()
        vm.onBrowserOpened(manifest.versionCode)
        vm.onForegroundChanged(false)
        vm.onForegroundChanged(true)
        assertFalse(vm.uiState.value.dialogVisible)
        runCurrent()
        assertFalse(vm.uiState.value.dialogVisible)

        releaseWrite.complete(Unit)
        fixture.preferences.settings.first { it.snoozedVersionCode == manifest.versionCode }
        runCurrent()
        assertFalse(vm.uiState.value.dialogVisible)
    }

    @Test fun `higher automatic version is not suppressed by earlier browser visit`() = runTest {
        val fixture = fixture()
        fixture.preferences.recordSuccessfulCheck(manifest, now())
        val higher = manifest.copy(versionCode = 45)
        var requests = 0
        val vm = model(fixture.preferences) { requests++; higher }
        vm.onForegroundChanged(true)
        vm.setPromptAllowed(true)
        runCurrent()
        vm.onBrowserOpened(manifest.versionCode)
        runCurrent()

        advanceTimeBy(UpdatePolicy.CHECK_INTERVAL_MS)
        vm.onForegroundChanged(false)
        vm.onForegroundChanged(true)
        runCurrent()
        assertEquals(1, requests)
        assertEquals(higher.versionCode, vm.uiState.value.manifest?.versionCode)
        assertTrue(vm.uiState.value.dialogVisible)
    }

    @Test fun `browser visit consumes in flight manual prompt for same version only`() = runTest {
        val fixture = fixture()
        fixture.preferences.recordSuccessfulCheck(manifest, now())
        val response = CompletableDeferred<UpdateManifest>()
        val vm = model(fixture.preferences) { response.await() }
        vm.onForegroundChanged(true)
        vm.setPromptAllowed(true)
        runCurrent()
        vm.checkManually()
        runCurrent()
        assertEquals(UpdateCheckState.CHECKING, vm.uiState.value.checkState)

        vm.onBrowserOpened(manifest.versionCode)
        vm.onForegroundChanged(false)
        vm.onForegroundChanged(true)
        runCurrent()
        response.complete(manifest)
        runCurrent()
        assertEquals(UpdateCheckState.AVAILABLE, vm.uiState.value.checkState)
        assertFalse(vm.uiState.value.dialogVisible)

        vm.checkManually()
        runCurrent()
        assertTrue(vm.uiState.value.dialogVisible)
    }

    @Test fun `higher in flight manual result still prompts with automatic checks disabled`() = runTest {
        val fixture = fixture()
        fixture.preferences.recordSuccessfulCheck(manifest, now())
        fixture.preferences.setAutoCheckEnabled(false)
        val response = CompletableDeferred<UpdateManifest>()
        val vm = model(fixture.preferences) { response.await() }
        vm.onForegroundChanged(true)
        vm.setPromptAllowed(true)
        runCurrent()
        vm.checkManually()
        runCurrent()
        vm.onBrowserOpened(manifest.versionCode)
        response.complete(manifest.copy(versionCode = 45))
        runCurrent()
        assertEquals(45L, vm.uiState.value.manifest?.versionCode)
        assertTrue(vm.uiState.value.dialogVisible)
    }

    @Test fun `failed manual check preserves cache and never reports current`() = runTest {
        val fixture = fixture()
        fixture.preferences.recordSuccessfulCheck(manifest, now())
        val vm = model(fixture.preferences) { throw UpdateRequestException("offline") }
        vm.onForegroundChanged(true)
        runCurrent()
        vm.checkManually()
        runCurrent()
        assertEquals(UpdateCheckState.FAILED, vm.uiState.value.checkState)
        assertEquals("offline", vm.uiState.value.checkMessage)
        assertEquals(manifest, vm.uiState.value.manifest)
        assertEquals(manifest, UpdateRepository(fixture.preferences).cachedManifest())
    }

    @Test fun `busy page defers prompt and restores visible manual dialog`() = runTest {
        val fixture = fixture()
        fixture.preferences.recordSuccessfulCheck(manifest, now())
        val vm = model(fixture.preferences)
        vm.onForegroundChanged(true)
        runCurrent()
        assertFalse(vm.uiState.value.dialogVisible)
        vm.checkManually()
        runCurrent()
        assertFalse(vm.uiState.value.dialogVisible)
        vm.setPromptAllowed(true)
        assertTrue(vm.uiState.value.dialogVisible)
        vm.setPromptAllowed(false)
        assertFalse(vm.uiState.value.dialogVisible)
        vm.setPromptAllowed(true)
        assertTrue(vm.uiState.value.dialogVisible)
        vm.onForegroundChanged(false)
        assertFalse(vm.uiState.value.dialogVisible)
        vm.onForegroundChanged(true)
        assertTrue(vm.uiState.value.dialogVisible)
    }

    @Test fun `successful check persists cache and throttles automatic requests for twenty four hours`() = runTest {
        val fixture = fixture()
        var requests = 0
        val vm = model(fixture.preferences) { requests++; manifest }
        vm.onForegroundChanged(true)
        runCurrent()
        assertEquals(1, requests)
        assertEquals(now(), fixture.preferences.settings.first().lastSuccessfulCheckAt)
        assertEquals(manifest, UpdateRepository(fixture.preferences).cachedManifest())
        vm.onForegroundChanged(false)
        vm.onForegroundChanged(true)
        runCurrent()
        assertEquals(1, requests)
        advanceTimeBy(UpdatePolicy.CHECK_INTERVAL_MS - 1)
        vm.onForegroundChanged(true)
        runCurrent()
        assertEquals(1, requests)
        advanceTimeBy(1)
        vm.onForegroundChanged(true)
        runCurrent()
        assertEquals(2, requests)
    }

    @Test fun `automatic failure uses backoff while manual request can retry immediately`() = runTest {
        val fixture = fixture()
        var requests = 0
        val vm = model(fixture.preferences) { requests++; throw UpdateRequestException("offline") }
        vm.onForegroundChanged(true)
        runCurrent()
        assertEquals(1, requests)
        assertEquals(UpdateCheckState.FAILED, vm.uiState.value.checkState)
        assertNull(vm.uiState.value.checkMessage)
        vm.onForegroundChanged(false)
        vm.onForegroundChanged(true)
        runCurrent()
        assertEquals(1, requests)
        vm.checkManually()
        runCurrent()
        assertEquals(2, requests)
        assertEquals("offline", vm.uiState.value.checkMessage)
        advanceTimeBy(UpdatePolicy.FAILURE_BACKOFF_MS)
        vm.onForegroundChanged(true)
        runCurrent()
        assertEquals(3, requests)
    }

    @Test fun `failed browser open reports error without changing reminder or installation state`() = runTest {
        val fixture = fixture()
        fixture.preferences.recordSuccessfulCheck(manifest, now())
        val vm = model(fixture.preferences)
        vm.onForegroundChanged(true)
        vm.setPromptAllowed(true)
        runCurrent()
        vm.onBrowserOpenFailed("browser unavailable")
        assertEquals("browser unavailable", vm.uiState.value.checkMessage)
        assertEquals(UpdateCheckState.AVAILABLE, vm.uiState.value.checkState)
        assertTrue(vm.uiState.value.dialogVisible)
        assertNull(fixture.preferences.settings.first().snoozedVersionCode)
        vm.onBrowserOpened(manifest.versionCode)
        assertNull(vm.uiState.value.checkMessage)
        assertEquals(UpdateCheckState.AVAILABLE, vm.uiState.value.checkState)
        assertFalse(vm.uiState.value.dialogVisible)
    }

    private suspend fun TestScope.assertBrowserRoundTrip(update: UpdateManifest) {
        val fixture = fixture()
        fixture.preferences.recordSuccessfulCheck(update, now())
        var requests = 0
        val vm = model(fixture.preferences) { requests++; update }
        vm.onForegroundChanged(true)
        vm.setPromptAllowed(true)
        runCurrent()
        assertTrue(vm.uiState.value.dialogVisible)
        vm.onBrowserOpened(update.versionCode)
        vm.onForegroundChanged(false)
        vm.onForegroundChanged(true)
        assertFalse(vm.uiState.value.dialogVisible)
        runCurrent()
        val saved = fixture.preferences.settings.first()
        assertEquals(update.versionCode, saved.snoozedVersionCode)
        assertEquals(now() + UpdatePolicy.SNOOZE_MS, saved.snoozedUntil)
        assertEquals(update.versionCode, saved.requiredDismissedVersionCode)
        assertEquals(saved.snoozedUntil, saved.requiredDismissedUntil)
        assertEquals(UpdateCheckState.AVAILABLE, vm.uiState.value.checkState)
        assertEquals(0, requests)

        owners.remove(vm)?.clear()
        fixture.job.cancelAndJoin()
        val rebuilt = fixture(fixture.file)
        val restored = model(rebuilt.preferences) { requests++; update }
        restored.onForegroundChanged(true)
        restored.setPromptAllowed(true)
        runCurrent()
        assertFalse(restored.uiState.value.dialogVisible)
        assertEquals(update, restored.uiState.value.manifest)
        assertEquals(0, requests)
        restored.checkManually()
        runCurrent()
        assertTrue(restored.uiState.value.dialogVisible)
        assertEquals(1, requests)
    }

    private fun TestScope.now() = 1_000_000_000L + testScheduler.currentTime

    private fun TestScope.model(
        preferences: UpdatePreferences,
        fetch: suspend () -> UpdateManifest = { manifest }
    ): UpdateViewModel {
        val vm = UpdateViewModel(UpdateRepository(preferences, fetch), preferences, currentVersionCode = 43, clock = { now() })
        owners[vm] = ViewModelStore().apply { put("updates", vm) }
        return vm
    }

    private fun TestScope.fixture(file: File = File(temporaryFolder.newFolder(), "updates.preferences_pb")): Fixture {
        val job = SupervisorJob(backgroundScope.coroutineContext[Job])
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(backgroundScope.coroutineContext + job),
            produceFile = { file }
        )
        return Fixture(file, job, store, UpdatePreferences(store))
    }

    private data class Fixture(
        val file: File,
        val job: Job,
        val store: DataStore<Preferences>,
        val preferences: UpdatePreferences
    )
}
