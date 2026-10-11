package com.example.bloodpressurerecord.ui.settings

import android.net.Uri
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.repository.BpZoneConfigRepository
import com.example.bloodpressurerecord.data.repository.ClearAllDataResult
import com.example.bloodpressurerecord.data.repository.SettingsBundle
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.UserProfile
import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import com.example.bloodpressurerecord.domain.model.BpZoneCuts
import com.example.bloodpressurerecord.ui.home.BpZoneAxis
import com.example.bloodpressurerecord.ui.home.BpZoneField
import com.example.bloodpressurerecord.ui.home.BpZoneTargetDraft
import com.example.bloodpressurerecord.ui.home.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `early target editor waits for first persisted config`() = runTest {
        val delayedConfig = MutableSharedFlow<BpZoneConfig>()
        val custom = customConfig()
        val vm = SettingsViewModel(
            FakeTargetSettingsRepository(),
            zoneConfigRepository = FakeZoneConfigRepository(observedConfig = delayedConfig)
        )
        vm.openZoneTargetEditor()
        runCurrent()
        assertEquals(null, vm.zoneTargetEditorState.value)

        delayedConfig.emit(custom)
        advanceUntilIdle()
        assertEquals(BpZoneTargetDraft.from(custom), vm.zoneTargetEditorState.value?.draft)
    }

    @Test
    fun `dismissing editor before config loads cancels pending opening`() = runTest {
        val delayedConfig = MutableSharedFlow<BpZoneConfig>()
        val vm = SettingsViewModel(
            FakeTargetSettingsRepository(),
            zoneConfigRepository = FakeZoneConfigRepository(observedConfig = delayedConfig)
        )
        vm.openZoneTargetEditor()
        runCurrent()
        vm.dismissZoneTargetEditor()
        delayedConfig.emit(BpZoneConfig.Default)
        advanceUntilIdle()
        assertEquals(null, vm.zoneTargetEditorState.value)
    }

    @Test
    fun `target editor rejects invalid input and preserves draft after save failure`() = runTest {
        val configRepo = FakeZoneConfigRepository()
        val vm = SettingsViewModel(FakeTargetSettingsRepository(), zoneConfigRepository = configRepo)
        vm.openZoneTargetEditor()
        advanceUntilIdle()
        vm.updateZoneTargetField(BpZoneAxis.SYS, BpZoneField.LOW_CUT, "120")
        vm.saveZoneTargetEditor()
        advanceUntilIdle()
        assertEquals(0, configRepo.saveCalls)
        assertEquals(R.string.bp_zone_config_invalid, vm.zoneTargetEditorState.value?.errorRes)
        assertFalse(vm.zoneTargetEditorState.value!!.saving)

        vm.updateZoneTargetField(BpZoneAxis.SYS, BpZoneField.LOW_CUT, "91")
        assertEquals(null, vm.zoneTargetEditorState.value?.errorRes)
        configRepo.saveResult = Result.failure(IllegalStateException("Local write failed"))
        vm.saveZoneTargetEditor()
        advanceUntilIdle()
        assertEquals(1, configRepo.saveCalls)
        assertEquals(R.string.bp_zone_config_save_failed, vm.zoneTargetEditorState.value?.errorRes)
        assertEquals("91", vm.zoneTargetEditorState.value?.draft?.value(BpZoneAxis.SYS, BpZoneField.LOW_CUT))
        assertFalse(vm.zoneTargetEditorState.value!!.saving)

        vm.dismissZoneTargetEditor()
        assertEquals(null, vm.zoneTargetEditorState.value)
    }

    @Test
    fun `target editor uses saved config locks during saving and closes on success`() = runTest {
        val custom = customConfig()
        val configRepo = FakeZoneConfigRepository(custom)
        val gate = CompletableDeferred<Unit>()
        configRepo.saveGate = gate
        val vm = SettingsViewModel(FakeTargetSettingsRepository(), zoneConfigRepository = configRepo)
        advanceUntilIdle()
        vm.openZoneTargetEditor()
        assertEquals(BpZoneTargetDraft.from(custom), vm.zoneTargetEditorState.value?.draft)
        vm.updateZoneTargetField(BpZoneAxis.DIA, BpZoneField.NORMAL_CUT, "79")
        vm.saveZoneTargetEditor()
        runCurrent()
        assertTrue(vm.zoneTargetEditorState.value!!.saving)

        vm.saveZoneTargetEditor()
        vm.openZoneTargetEditor()
        vm.updateZoneTargetField(BpZoneAxis.DIA, BpZoneField.NORMAL_CUT, "78")
        vm.dismissZoneTargetEditor()
        runCurrent()
        assertEquals(1, configRepo.saveCalls)
        assertEquals("79", vm.zoneTargetEditorState.value?.draft?.value(BpZoneAxis.DIA, BpZoneField.NORMAL_CUT))

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(null, vm.zoneTargetEditorState.value)
        assertEquals(79, configRepo.config.value.dia.cuts.normal)
        vm.openZoneTargetEditor()
        assertEquals("79", vm.zoneTargetEditorState.value?.draft?.value(BpZoneAxis.DIA, BpZoneField.NORMAL_CUT))
    }

    @Test
    fun `throwing save shows failure and preserves editable draft`() = runTest {
        val configRepo = FakeZoneConfigRepository().apply {
            saveError = IllegalStateException("Local write failed")
        }
        val vm = SettingsViewModel(FakeTargetSettingsRepository(), zoneConfigRepository = configRepo)
        vm.openZoneTargetEditor()
        advanceUntilIdle()
        vm.updateZoneTargetField(BpZoneAxis.SYS, BpZoneField.LOW_CUT, "91")
        vm.saveZoneTargetEditor()
        advanceUntilIdle()
        assertEquals(R.string.bp_zone_config_save_failed, vm.zoneTargetEditorState.value?.errorRes)
        assertEquals("91", vm.zoneTargetEditorState.value?.draft?.value(BpZoneAxis.SYS, BpZoneField.LOW_CUT))
        assertFalse(vm.zoneTargetEditorState.value!!.saving)
    }

    @Test
    fun `missing target repository reports save failure`() = runTest {
        val vm = SettingsViewModel(FakeTargetSettingsRepository())
        vm.openZoneTargetEditor()
        advanceUntilIdle()
        vm.saveZoneTargetEditor()
        advanceUntilIdle()
        assertEquals(R.string.bp_zone_config_save_failed, vm.zoneTargetEditorState.value?.errorRes)
        assertFalse(vm.zoneTargetEditorState.value!!.saving)
    }

    @Test
    fun `reopening editor uses external config updates and discards cancelled draft`() = runTest {
        val configRepo = FakeZoneConfigRepository()
        val vm = SettingsViewModel(FakeTargetSettingsRepository(), zoneConfigRepository = configRepo)
        vm.openZoneTargetEditor()
        advanceUntilIdle()
        vm.updateZoneTargetField(BpZoneAxis.SYS, BpZoneField.LOW_CUT, "91")
        vm.dismissZoneTargetEditor()
        configRepo.config.value = customConfig()
        advanceUntilIdle()
        vm.openZoneTargetEditor()
        assertEquals(BpZoneTargetDraft.from(customConfig()), vm.zoneTargetEditorState.value?.draft)
    }

    private fun customConfig() = BpZoneConfig.Default.copy(
        sys = BpZoneConfig.Default.sys.copy(cuts = BpZoneCuts(90, 110, 130))
    )

    private class FakeZoneConfigRepository(
        initial: BpZoneConfig = BpZoneConfig.Default,
        private val observedConfig: Flow<BpZoneConfig>? = null
    ) : BpZoneConfigRepository {
        val config = MutableStateFlow(initial)
        var saveResult: Result<Unit> = Result.success(Unit)
        var saveGate: CompletableDeferred<Unit>? = null
        var saveError: Throwable? = null
        var saveCalls = 0

        override fun observeConfig(): Flow<BpZoneConfig> = observedConfig ?: config

        override suspend fun saveConfig(config: BpZoneConfig): Result<Unit> {
            saveCalls += 1
            if (!config.isValid()) return Result.failure(IllegalArgumentException("Invalid target config"))
            saveGate?.await()
            saveError?.let { throw it }
            if (saveResult.isSuccess) this.config.value = config
            return saveResult
        }
    }
}

internal class FakeTargetSettingsRepository : SettingsRepository {
    override fun observeSettings(): Flow<SettingsBundle> = flowOf(SettingsBundle())
    override suspend fun setLargeTextEnabled(enabled: Boolean) = Unit
    override suspend fun setHighRiskAlertEnabled(enabled: Boolean) = Unit
    override suspend fun setShowTrendChart(enabled: Boolean) = Unit
    override suspend fun setAppearanceMode(mode: String) = Unit
    override suspend fun setShowBuddy(enabled: Boolean) = Unit
    override suspend fun setDiscardFirstReading(enabled: Boolean) = Unit
    override suspend fun setMorningReminderEnabled(enabled: Boolean) = Unit
    override suspend fun setMorningReminderTime(value: String) = Unit
    override suspend fun setEveningReminderEnabled(enabled: Boolean) = Unit
    override suspend fun setEveningReminderTime(value: String) = Unit
    override suspend fun setMedicationReminderEnabled(enabled: Boolean) = Unit
    override suspend fun setMedicationCalendarSyncEnabled(enabled: Boolean) = Unit
    override suspend fun refreshReminders() = Unit
    override suspend fun saveUserProfile(profile: UserProfile) = Unit
    override suspend fun clearAllData(): Result<ClearAllDataResult> = Result.success(
        ClearAllDataResult(true, true, true, true)
    )
    override suspend fun exportBackupXlsxToUri(
        uri: Uri,
        fileNameHint: String,
        passphrase: CharArray?
    ): Result<String> = Result.success("")
    override suspend fun importBackupXlsxFromUri(
        uri: Uri,
        passphrase: CharArray?
    ): Result<String> = Result.success("")
}
