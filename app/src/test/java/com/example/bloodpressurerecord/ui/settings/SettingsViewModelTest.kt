package com.example.bloodpressurerecord.ui.settings

import android.net.Uri
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.repository.BpZoneConfigRepository
import com.example.bloodpressurerecord.data.repository.ClearAllDataResult
import com.example.bloodpressurerecord.data.repository.SettingsBundle
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.UserProfile
import com.example.bloodpressurerecord.data.repository.backup.BackupExportPayload
import com.example.bloodpressurerecord.data.repository.backup.BackupExportPlan
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `clearing records discards a paused backup and rejects its file picker result`() = runTest {
        val clearGate = CompletableDeferred<Unit>()
        var clearCalls = 0
        val repository = object : SettingsRepository by FakeTargetSettingsRepository() {
            override suspend fun prepareBackupExport() = Result.success(BackupExportPlan.fromPayload(
                BackupExportPayload(instructions = emptyList(), measurements = emptyList(),
                    userProfile = emptyList(), meta = emptyList())
            ))

            override suspend fun clearAllData(): Result<ClearAllDataResult> {
                clearCalls++
                if (clearCalls == 1) return Result.success(ClearAllDataResult(false, false, false, false))
                clearGate.await()
                return Result.success(ClearAllDataResult(true, false, true, true, listOf("设置未重置")))
            }
        }
        val vm = SettingsViewModel(repository)
        val passphrase = "private-test".toCharArray()
        vm.prepareBackupExport("old", passphrase)
        vm.pauseBackupExport()
        vm.confirmClearAll()
        assertEquals(1, vm.uiState.value.exportVolumeCount)
        assertEquals("private-test", passphrase.concatToString())

        vm.requestEncryptedBackupExport()
        val oldRequest = requireNotNull(vm.uiState.value.exportVolumeRequestId)
        vm.pauseBackupExport()
        vm.confirmClearAll()
        vm.confirmClearAll()
        assertEquals(2, clearCalls)
        assertTrue(vm.uiState.value.isDataActionRunning)
        clearGate.complete(Unit)
        advanceUntilIdle()
        assertTrue(passphrase.all { it == '\u0000' })
        assertEquals(0, vm.uiState.value.exportVolumeCount)
        assertEquals("", vm.uiState.value.exportFileName)
        assertFalse(vm.uiState.value.exportEncrypted)
        vm.requestBackupExport()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.exportEncrypted)
        assertNotNull(vm.uiState.value.exportVolumeRequestId)
        assertEquals(vm.uiState.value.exportVolumeRequestId, vm.uiState.value.autoLaunchExportRequestId)
        val newState = vm.uiState.value
        vm.onExportLocationResult(oldRequest, null)
        assertEquals(newState, vm.uiState.value)
    }

    @Test
    fun `paused plain export prompts before switching to encrypted and preserves progress if cancelled`() = runTest {
        val repository = object : SettingsRepository by FakeTargetSettingsRepository() {
            override suspend fun prepareBackupExport() = Result.success(
                BackupExportPlan.fromPayload(
                    BackupExportPayload(
                        instructions = emptyList(),
                        measurements = emptyList(),
                        userProfile = emptyList(),
                        meta = emptyList()
                    )
                )
            )
        }
        val vm = SettingsViewModel(repository)
        vm.prepareBackupExport("plain_backup")
        assertFalse(vm.uiState.value.exportEncrypted)
        assertTrue(vm.uiState.value.exportFileName.endsWith(".xlsx"))
        vm.pauseBackupExport()
        assertEquals(null, vm.uiState.value.exportVolumeRequestId)
        assertEquals(1, vm.uiState.value.exportVolumeCount)

        // 点击加密导出不得直接恢复明文计划，应先弹出格式切换确认
        vm.requestEncryptedBackupExport()
        assertEquals(true, vm.uiState.value.pendingExportFormatSwitchToEncrypted)
        assertEquals(null, vm.uiState.value.exportVolumeRequestId)
        assertFalse(vm.uiState.value.showExportPassphraseDialog)
        assertFalse(vm.uiState.value.exportEncrypted)

        // 保留原导出：原明文进度保持不变，点明文入口可正常继续
        vm.dismissSwitchExportFormat()
        assertEquals(null, vm.uiState.value.pendingExportFormatSwitchToEncrypted)
        vm.requestBackupExport()
        assertTrue(vm.uiState.value.exportVolumeRequestId != null)
        assertTrue(vm.uiState.value.exportFileName.endsWith(".xlsx"))
        assertFalse(vm.uiState.value.exportEncrypted)

        // 再次暂停后确认切换为加密导出：放弃明文计划并进入设置口令弹窗
        vm.pauseBackupExport()
        vm.requestEncryptedBackupExport()
        assertEquals(true, vm.uiState.value.pendingExportFormatSwitchToEncrypted)
        vm.confirmSwitchExportFormat()
        assertEquals(null, vm.uiState.value.pendingExportFormatSwitchToEncrypted)
        assertEquals(0, vm.uiState.value.exportVolumeCount)
        assertTrue(vm.uiState.value.showExportPassphraseDialog)

        val passphrase = "strong-passphrase".toCharArray()
        vm.prepareBackupExport("encrypted_backup", passphrase)
        assertTrue(vm.uiState.value.exportEncrypted)
        assertTrue(vm.uiState.value.exportFileName.endsWith(".bpx"))
        assertTrue(vm.uiState.value.exportVolumeRequestId != null)
    }

    @Test
    fun `paused encrypted export prompts before switching to plain and wipes passphrase on switch`() = runTest {
        val payload = BackupExportPayload(
            instructions = emptyList(),
            measurements = emptyList(),
            userProfile = emptyList(),
            meta = emptyList()
        )
        val multiVolumePlan = BackupExportPlan(listOf(payload, payload))
        val repository = object : SettingsRepository by FakeTargetSettingsRepository() {
            override suspend fun prepareBackupExport() = Result.success(multiVolumePlan)
        }
        val vm = SettingsViewModel(repository)
        val passphrase = "secret-password".toCharArray()
        vm.prepareBackupExport("enc_multi", passphrase)
        assertTrue(vm.uiState.value.exportEncrypted)
        assertEquals(2, vm.uiState.value.exportVolumeCount)
        assertTrue(vm.uiState.value.exportFileName.endsWith(".bpx"))

        vm.pauseBackupExport()
        assertEquals(null, vm.uiState.value.exportVolumeRequestId)

        // 点击明文导出不得直接恢复加密分卷，也不得提前清零口令
        vm.requestBackupExport()
        assertEquals(false, vm.uiState.value.pendingExportFormatSwitchToEncrypted)
        assertEquals(null, vm.uiState.value.exportVolumeRequestId)
        assertEquals(null, vm.uiState.value.autoLaunchExportRequestId)
        assertEquals("secret-password", passphrase.concatToString())

        // 保留原导出：加密分卷可继续恢复
        vm.dismissSwitchExportFormat()
        vm.requestEncryptedBackupExport()
        assertTrue(vm.uiState.value.exportVolumeRequestId != null)
        assertEquals(null, vm.uiState.value.autoLaunchExportRequestId)
        assertTrue(vm.uiState.value.exportEncrypted)
        assertTrue(vm.uiState.value.exportFileName.endsWith(".bpx"))

        // 确认切换为明文：清零旧口令、清空旧分卷并直接准备明文导出（该仓库为 2 卷计划，故展示第 1 卷进度而非单卷自动拉起）
        vm.pauseBackupExport()
        vm.requestBackupExport()
        vm.confirmSwitchExportFormat()
        advanceUntilIdle()
        assertTrue(passphrase.all { it == '\u0000' })
        assertEquals(2, vm.uiState.value.exportVolumeCount)
        assertFalse(vm.uiState.value.exportEncrypted)
        assertTrue(vm.uiState.value.exportFileName.endsWith(".xlsx"))
        assertNotNull(vm.uiState.value.exportVolumeRequestId)
        assertNull(vm.uiState.value.autoLaunchExportRequestId)
    }

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

    @Test
    fun `resetZoneTargetToDefault restores default cuts inside open editor`() = runTest {
        val configRepo = FakeZoneConfigRepository(initial = customConfig())
        val vm = SettingsViewModel(FakeTargetSettingsRepository(), zoneConfigRepository = configRepo)
        vm.openZoneTargetEditor()
        advanceUntilIdle()
        assertEquals("110", vm.zoneTargetEditorState.value?.draft?.value(BpZoneAxis.SYS, BpZoneField.NORMAL_CUT))

        vm.resetZoneTargetToDefault()
        assertEquals(BpZoneTargetDraft.from(BpZoneConfig.Default), vm.zoneTargetEditorState.value?.draft)
    }

    @Test
    fun `updating reminder time auto enables disabled reminder and profile dirty tracks unsaved edits`() = runTest {
        var morningScheduleCalls = 0
        var scheduledTime = ""
        val repo = object : SettingsRepository by FakeTargetSettingsRepository() {
            override suspend fun setMorningReminderSchedule(time: String, enabled: Boolean) {
                scheduledTime = time
                if (enabled) morningScheduleCalls += 1
            }
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.morningReminderEnabled)
        vm.updateMorningTime("08:15")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.morningReminderEnabled)
        assertEquals("08:15", vm.uiState.value.morningReminderTime)
        assertEquals("08:15", scheduledTime)
        assertEquals(1, morningScheduleCalls)
        assertTrue(vm.uiState.value.message.contains("早间提醒"))

        assertFalse(vm.uiState.value.isProfileDirty)
        vm.updateName("张阿姨")
        assertTrue(vm.uiState.value.isProfileDirty)
        vm.discardProfileChanges()
        assertFalse(vm.uiState.value.isProfileDirty)
        assertEquals("", vm.uiState.value.name)
    }

    @Test
    fun `reminder schedule failure rolls back optimistic state and reports error`() = runTest {
        val repo = object : SettingsRepository by FakeTargetSettingsRepository() {
            override suspend fun setMorningReminderSchedule(time: String, enabled: Boolean) {
                throw IllegalStateException("DataStore IO error")
            }
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()

        vm.updateMorningTime("06:45")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.morningReminderEnabled)
        assertEquals("07:30", vm.uiState.value.morningReminderTime)
        assertTrue(vm.uiState.value.messageIsError)
    }

    @Test
    fun `profile save locks concurrent clicks and preserves dirty state on failure`() = runTest {
        val saveGate = CompletableDeferred<Unit>()
        var saveCount = 0
        var shouldFail = false
        val repo = object : SettingsRepository by FakeTargetSettingsRepository() {
            override suspend fun saveUserProfile(profile: UserProfile) {
                saveCount += 1
                saveGate.await()
                if (shouldFail) throw IllegalStateException("disk full")
            }
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()

        vm.updateName("王大爷")
        var savedCallbacks = 0
        vm.saveUserProfile { savedCallbacks += 1 }
        vm.saveUserProfile { savedCallbacks += 1 }
        assertTrue(vm.uiState.value.isSavingProfile)
        // 保存期间的修改与放弃都被锁定忽略
        vm.updateName("被忽略的并发修改")
        vm.discardProfileChanges()
        assertEquals("王大爷", vm.uiState.value.name)
        assertEquals(1, saveCount)

        saveGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSavingProfile)
        assertFalse(vm.uiState.value.isProfileDirty)
        assertEquals(1, savedCallbacks)

        // 失败路径保留未保存状态并给出错误提示，不触发返回回调
        val failGate = CompletableDeferred<Unit>().apply { complete(Unit) }
        shouldFail = true
        val failingRepo = object : SettingsRepository by FakeTargetSettingsRepository() {
            override suspend fun saveUserProfile(profile: UserProfile) {
                failGate.await()
                throw IllegalStateException("disk full")
            }
        }
        val vmFail = SettingsViewModel(failingRepo)
        advanceUntilIdle()
        vmFail.updateName("李阿姨")
        var failCallbacks = 0
        vmFail.saveUserProfile { failCallbacks += 1 }
        advanceUntilIdle()
        assertEquals(0, failCallbacks)
        assertTrue(vmFail.uiState.value.isProfileDirty)
        assertTrue(vmFail.uiState.value.messageIsError)
    }

    @Test
    fun `clear in progress blocks cancel or backup export and marks partial issues as error`() = runTest {
        val clearGate = CompletableDeferred<Unit>()
        val repo = object : SettingsRepository by FakeTargetSettingsRepository() {
            override suspend fun clearAllData(): Result<ClearAllDataResult> {
                clearGate.await()
                return Result.success(
                    ClearAllDataResult(
                        databaseCleared = true,
                        settingsCleared = false,
                        remindersRescheduled = true,
                        widgetRefreshed = true,
                        warnings = listOf("偏好设置重置未完成")
                    )
                )
            }
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()

        vm.requestClearAll()
        assertTrue(vm.uiState.value.showClearConfirm)
        vm.confirmClearAll()
        assertTrue(vm.uiState.value.isDataActionRunning)
        assertEquals(SettingsDataAction.CLEAR, vm.uiState.value.activeDataAction)

        // 清空进行中禁止关闭弹窗或切去导出备份
        vm.dismissClearAll()
        vm.requestBackupExport()
        assertTrue(vm.uiState.value.showClearConfirm)
        assertNull(vm.uiState.value.exportVolumeRequestId)
        assertNull(vm.uiState.value.autoLaunchExportRequestId)

        clearGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isDataActionRunning)
        assertFalse(vm.uiState.value.showClearConfirm)
        assertTrue(vm.uiState.value.messageIsError)
        assertTrue(vm.uiState.value.message.contains("部分清理未完成"))
    }

    @Test
    fun `single volume plain export auto launches once and cancel or launch failure never loops`() = runTest {
        var prepareCalls = 0
        val repo = object : SettingsRepository by FakeTargetSettingsRepository() {
            override suspend fun prepareBackupExport(): Result<BackupExportPlan> {
                prepareCalls += 1
                return Result.success(
                    BackupExportPlan.fromPayload(
                        BackupExportPayload(
                            instructions = emptyList(),
                            measurements = emptyList(),
                            userProfile = emptyList(),
                            meta = emptyList()
                        )
                    )
                )
            }
        }
        val vm = SettingsViewModel(repo)

        // 1. 首次点击明文主按钮：直接完成单卷准备并签发一次性自动拉起令牌
        vm.requestBackupExport()
        advanceUntilIdle()
        assertEquals(1, prepareCalls)
        val firstReq = requireNotNull(vm.uiState.value.exportVolumeRequestId)
        assertEquals(firstReq, vm.uiState.value.autoLaunchExportRequestId)
        assertTrue(vm.uiState.value.exportFileName.endsWith(".xlsx"))

        // 2. UI 消费令牌并拉起 SAF：重组重复调用 consume 必须返回 false
        assertTrue(vm.consumeAutoLaunchExportRequest(firstReq))
        assertNull(vm.uiState.value.autoLaunchExportRequestId)
        assertFalse(vm.consumeAutoLaunchExportRequest(firstReq))
        assertTrue(vm.beginBackupLocation(firstReq))

        // 3. 用户在 SAF 中点击取消（uri = null）：收起请求 ID 且不重拉 SAF、不补弹确认框，保留已准备计划
        vm.finishExportBackupLocation(firstReq, null)
        assertNull(vm.uiState.value.exportVolumeRequestId)
        assertNull(vm.uiState.value.autoLaunchExportRequestId)
        assertEquals(1, vm.uiState.value.exportVolumeCount)
        assertTrue(vm.uiState.value.message.contains("保存已取消"))

        // 4. 用户再次主动点击「继续保存明文 Excel」：复用已准备计划（不重复读库），签发新一次性令牌
        vm.requestBackupExport()
        assertEquals(1, prepareCalls)
        val secondReq = requireNotNull(vm.uiState.value.exportVolumeRequestId)
        assertEquals(secondReq, vm.uiState.value.autoLaunchExportRequestId)

        // 5. 模拟系统文件选择器启动异常：清理请求状态并报错，绝不死循环重拉
        assertTrue(vm.consumeAutoLaunchExportRequest(secondReq))
        assertTrue(vm.beginBackupLocation(secondReq))
        vm.onBackupLocationLaunchFailed()
        assertFalse(vm.uiState.value.isChoosingBackupLocation)
        assertNull(vm.uiState.value.exportVolumeRequestId)
        assertNull(vm.uiState.value.autoLaunchExportRequestId)
        assertTrue(vm.uiState.value.messageIsError)
    }

    @Test
    fun `appendMedicationDosageUnit appends only to bare numbers and never duplicates or overwrites units`() {
        assertEquals("1 片", appendMedicationDosageUnit("1", "片"))
        assertEquals("0.5 粒", appendMedicationDosageUnit(" 0.5 ", "粒"))
        assertEquals("1/2 片", appendMedicationDosageUnit("1/2", "片"))
        assertEquals("5 mg", appendMedicationDosageUnit("5", "mg"))

        // 已有相同或不同单位：绝不重复追加，也绝不替换已有单位
        assertEquals("1 片", appendMedicationDosageUnit("1 片", "片"))
        assertEquals("1片", appendMedicationDosageUnit("1片", "片"))
        assertEquals("5 mg", appendMedicationDosageUnit("5 mg", "片"))
        assertEquals("1 粒", appendMedicationDosageUnit("1 粒", "丸"))

        // 空白或复杂剂量描述：绝不臆造数字，也绝不覆盖复杂表达
        assertEquals("", appendMedicationDosageUnit("", "片"))
        assertEquals("   ", appendMedicationDosageUnit("   ", "mg"))
        assertEquals("早1片晚半片", appendMedicationDosageUnit("早1片晚半片", "片"))
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
    override suspend fun getWritableMedicationCalendars() =
        emptyList<com.example.bloodpressurerecord.data.calendar.MedicationCalendarTarget>()
    override suspend fun setMedicationCalendar(calendarId: Long?, includeDetails: Boolean) = Unit
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
