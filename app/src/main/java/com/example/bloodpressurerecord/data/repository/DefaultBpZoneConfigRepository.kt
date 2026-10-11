package com.example.bloodpressurerecord.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bloodpressurerecord.domain.model.BpAxisConfig
import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import com.example.bloodpressurerecord.domain.model.BpZoneCuts
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 使用现有 appDataStore；DataStore 自身负责在 IO 协程中读写。 */
class DefaultBpZoneConfigRepository(
    private val dataStore: DataStore<Preferences>
) : BpZoneConfigRepository {
    override fun observeConfig(): Flow<BpZoneConfig> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> decode(preferences[configKey]) ?: BpZoneConfig.Default }
        .distinctUntilChanged()

    override suspend fun saveConfig(config: BpZoneConfig): Result<Unit> {
        if (!config.isValid()) {
            return Result.failure(IllegalArgumentException("Invalid blood pressure zone configuration"))
        }
        return try {
            // 单个 key 保存整套配置，避免读取到收缩压/舒张压的部分更新。
            dataStore.edit { preferences -> preferences[configKey] = encode(config) }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun encode(config: BpZoneConfig): String = listOf(
        config.sys.min, config.sys.max, config.sys.cuts.low, config.sys.cuts.normal, config.sys.cuts.high,
        config.dia.min, config.dia.max, config.dia.cuts.low, config.dia.cuts.normal, config.dia.cuts.high
    ).joinToString(",")

    private fun decode(value: String?): BpZoneConfig? {
        val parts = value?.split(',') ?: return null
        if (parts.size != 10) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        return BpZoneConfig(
            sys = BpAxisConfig(BpZoneCuts(numbers[2], numbers[3], numbers[4]), numbers[0], numbers[1]),
            dia = BpAxisConfig(BpZoneCuts(numbers[7], numbers[8], numbers[9]), numbers[5], numbers[6])
        ).takeIf { it.isValid() }
    }

    private companion object {
        val configKey = stringPreferencesKey("bp_zone_config_v1")
    }
}
