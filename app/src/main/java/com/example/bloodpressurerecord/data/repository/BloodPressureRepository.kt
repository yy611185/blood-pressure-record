package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.domain.model.AverageStrategy
import kotlinx.coroutines.flow.Flow

data class CalendarSessionSummary(
    val measuredAt: Long,
    val containsHighRiskReading: Boolean
)

data class SessionSummary(
    val id: String,
    val measuredAt: Long,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?,
    val category: String,
    val scene: String,
    val noteSummary: String?,
    val containsHighRiskReading: Boolean
)

data class LatestSessionSummary(
    val id: String,
    val measuredAt: Long,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val category: String,
    val containsHighRiskReading: Boolean
)

data class PeriodStatistics(
    val recordCount: Int = 0,
    val averageSystolic: Double? = null,
    val averageDiastolic: Double? = null,
    val averagePulse: Double? = null,
    val highestSystolic: Int? = null,
    val highestDiastolic: Int? = null,
    val lowestSystolic: Int? = null,
    val lowestDiastolic: Int? = null,
    val highRiskCount: Int = 0
)

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
    val readings: List<SessionReadingInput>,
    /** 平均值计算策略；保存时用于派生平均值与分级。 */
    val averageStrategy: AverageStrategy = AverageStrategy.ALL
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
    val containsHighRiskReading: Boolean,
    val readings: List<SessionReading>,
    val averageStrategy: AverageStrategy = AverageStrategy.ALL
)

interface BloodPressureRepository {
    fun observeSession(sessionId: String): Flow<SessionRecord?>
    fun observeLatestSessionSummary(): Flow<LatestSessionSummary?>
    fun observeCalendarSessionSummaries(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<CalendarSessionSummary>>
    fun observeSessionSummariesInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<SessionSummary>>
    fun observePeriodStatistics(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<PeriodStatistics>

    suspend fun saveSession(input: SaveSessionInput): Result<String>
    suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun restoreSession(session: SessionRecord): Result<Unit>
}
