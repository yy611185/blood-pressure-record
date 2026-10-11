package com.example.bloodpressurerecord.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import com.example.bloodpressurerecord.domain.model.BpZoneCuts
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultBpZoneConfigRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val storeJobs = mutableListOf<Job>()

    @After
    fun closeStores() = runBlocking {
        storeJobs.forEach { it.cancelAndJoin() }
    }

    @Test
    fun `missing saved configuration returns default`() = runTest {
        val store = newStore(newFile())
        assertEquals(BpZoneConfig.Default, DefaultBpZoneConfigRepository(store).observeConfig().first())
    }

    @Test
    fun `invalid cuts are rejected without replacing saved config`() = runTest {
        val store = newStore(newFile())
        val repository = DefaultBpZoneConfigRepository(store)
        val saved = customConfig()
        repository.saveConfig(saved).getOrThrow()

        val invalidConfigs = listOf(
            saved.copy(sys = saved.sys.copy(cuts = BpZoneCuts(90, 90, 140))),
            saved.copy(dia = saved.dia.copy(cuts = BpZoneCuts(60, 90, 80))),
            saved.copy(sys = saved.sys.copy(cuts = BpZoneCuts(1, 120, 140))),
            saved.copy(dia = saved.dia.copy(max = 80))
        )
        invalidConfigs.forEach { invalid ->
            val result = repository.saveConfig(invalid)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(saved, repository.observeConfig().first())
        }
    }

    @Test
    fun `saving config updates the observed flow`() = runTest {
        val repository = DefaultBpZoneConfigRepository(newStore(newFile()))
        val emissions = mutableListOf<BpZoneConfig>()
        val initialRead = CompletableDeferred<Unit>()
        val collecting = launch {
            repository.observeConfig()
                .onEach { initialRead.complete(Unit) }
                .take(2)
                .toList(emissions)
        }
        // 等待默认值被实际读取后再写入，验证持续订阅而非重新订阅。
        initialRead.await()
        val saved = customConfig()
        repository.saveConfig(saved).getOrThrow()
        collecting.join()
        assertEquals(listOf(BpZoneConfig.Default, saved), emissions)
    }

    @Test
    fun `valid configuration survives rebuilding datastore from local file`() = runTest {
        val file = newFile()
        val firstStore = newStore(file)
        val saved = customConfig()
        DefaultBpZoneConfigRepository(firstStore).saveConfig(saved).getOrThrow()
        storeJobs.last().cancelAndJoin()

        val rebuilt = DefaultBpZoneConfigRepository(newStore(file))
        assertEquals(saved, rebuilt.observeConfig().first())
    }

    @Test
    fun `malformed or invalid stored payload falls back to default`() = runTest {
        val store = newStore(newFile())
        val repository = DefaultBpZoneConfigRepository(store)
        listOf(
            "broken",
            "70,180,90,120,140,40,110,60,80",
            "70,180,90,120,140,40,110,60,80,not-an-int",
            "70,180,90,90,140,40,110,60,80,90",
            "0,180,90,120,140,40,110,60,80,90",
            "70,180,90,120,140,40,110,60,80,2147483648"
        ).forEach { payload ->
            store.edit { it[stringPreferencesKey("bp_zone_config_v1")] = payload }
            assertEquals(BpZoneConfig.Default, repository.observeConfig().first())
        }
    }

    private fun customConfig(): BpZoneConfig = BpZoneConfig.Default.copy(
        sys = BpZoneConfig.Default.sys.copy(cuts = BpZoneCuts(85, 115, 145)),
        dia = BpZoneConfig.Default.dia.copy(cuts = BpZoneCuts(55, 75, 95))
    )

    private fun newFile(): File = File(temporaryFolder.newFolder(), "zones.preferences_pb")

    private fun newStore(file: File): DataStore<Preferences> {
        val job = SupervisorJob()
        storeJobs += job
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file }
        )
    }
}
