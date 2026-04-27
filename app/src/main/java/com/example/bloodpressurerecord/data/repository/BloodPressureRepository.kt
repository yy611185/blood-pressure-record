package com.example.bloodpressurerecord.data.repository

import kotlinx.coroutines.flow.Flow

data class SessionReadingInput(
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?
)

data class SaveSessionInput(
    val measuredAt: Long,
    val scene: String,
    val note: String?,
    val symptoms: List<String>,
    val readings: List<SessionReadingInput>
)

data class SessionReading(
    val id: String,
    val orderIndex: Int,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?
)

data class SessionRecord(
    val id: String,
    val measuredAt: Long,
    val scene: String,
    val note: String?,
    val symptoms: List<String>,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?,
    val category: String,
    val highRiskAlertTriggered: Boolean,
    val readings: List<SessionReading>
)

interface BloodPressureRepository {
    fun observeSessionCount(): Flow<Int>
    fun observeSessions(): Flow<List<SessionRecord>>
    fun observeSession(sessionId: String): Flow<SessionRecord?>

    suspend fun saveSession(input: SaveSessionInput): Result<String>
    suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit>
    suspend fun deleteSession(sessionId: String): Result<Unit>
}
