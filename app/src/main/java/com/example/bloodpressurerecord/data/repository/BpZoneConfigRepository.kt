package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import kotlinx.coroutines.flow.Flow

interface BpZoneConfigRepository {
    fun observeConfig(): Flow<BpZoneConfig>
    suspend fun saveConfig(config: BpZoneConfig): Result<Unit>
}
