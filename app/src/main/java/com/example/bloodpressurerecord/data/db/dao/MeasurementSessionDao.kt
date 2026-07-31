package com.example.bloodpressurerecord.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionWithReadings
import kotlinx.coroutines.flow.Flow

data class TrendPointRow(
    val id: String,
    val measuredAt: Long,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?,
    val category: String,
    val containsHighRiskReading: Boolean
)

data class CalendarSessionSummaryRow(
    val measuredAt: Long,
    val containsHighRiskReading: Boolean
)

data class SessionSummaryRow(
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

data class LatestSessionSummaryRow(
    val id: String,
    val measuredAt: Long,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val category: String,
    val containsHighRiskReading: Boolean
)

data class PeriodStatisticsRow(
    val recordCount: Int,
    val averageSystolic: Double?,
    val averageDiastolic: Double?,
    val averagePulse: Double?,
    val highestSystolic: Int?,
    val highestDiastolic: Int?,
    val lowestSystolic: Int?,
    val lowestDiastolic: Int?,
    val highRiskCount: Int
)

@Dao
interface MeasurementSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: MeasurementSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<MeasurementSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<MeasurementReadingEntity>)

    @Transaction
    suspend fun insertSessionWithReadings(
        session: MeasurementSessionEntity,
        readings: List<MeasurementReadingEntity>
    ) {
        insertSession(session)
        insertReadings(readings)
    }

    @Transaction
    @Query("SELECT * FROM measurement_sessions WHERE id = :sessionId")
    suspend fun getSessionWithReadings(sessionId: String): MeasurementSessionWithReadings?

    @Transaction
    @Query("SELECT * FROM measurement_sessions WHERE id = :sessionId")
    fun observeSessionWithReadings(sessionId: String): Flow<MeasurementSessionWithReadings?>

    @Query(
        """
        SELECT id, measuredAt, avgSystolic, avgDiastolic, category,
               highRiskAlertTriggered AS containsHighRiskReading
        FROM measurement_sessions
        ORDER BY measuredAt DESC, id ASC
        LIMIT 1
        """
    )
    fun observeLatestSessionSummary(): Flow<LatestSessionSummaryRow?>

    @Transaction
    @Query("SELECT * FROM measurement_sessions ORDER BY measuredAt DESC")
    suspend fun getAllSessionsWithReadings(): List<MeasurementSessionWithReadings>

    @Transaction
    @Query(
        """
        SELECT * FROM measurement_sessions
        WHERE measuredAt >= :startInclusive AND measuredAt < :endExclusive
        ORDER BY measuredAt DESC
        """
    )
    suspend fun getSessionsWithReadingsInRange(
        startInclusive: Long,
        endExclusive: Long
    ): List<MeasurementSessionWithReadings>

    @Query(
        """
        SELECT measuredAt,
               highRiskAlertTriggered AS containsHighRiskReading
        FROM measurement_sessions
        WHERE measuredAt >= :startInclusive AND measuredAt < :endExclusive
        ORDER BY measuredAt ASC
        """
    )
    fun observeCalendarSessionSummaries(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<CalendarSessionSummaryRow>>

    @Query(
        """
        SELECT id, measuredAt, avgSystolic, avgDiastolic, avgPulse, category, scene,
               CASE WHEN note IS NULL THEN NULL ELSE SUBSTR(note, 1, 40) END AS noteSummary,
               highRiskAlertTriggered AS containsHighRiskReading
        FROM measurement_sessions
        WHERE measuredAt >= :startInclusive AND measuredAt < :endExclusive
        ORDER BY measuredAt ASC, id ASC
        """
    )
    fun observeSessionSummariesInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<SessionSummaryRow>>

    @Query(
        """
        SELECT COUNT(*) AS recordCount,
               AVG(avgSystolic) AS averageSystolic,
               AVG(avgDiastolic) AS averageDiastolic,
               AVG(avgPulse) AS averagePulse,
               MAX(avgSystolic) AS highestSystolic,
               MAX(avgDiastolic) AS highestDiastolic,
               MIN(avgSystolic) AS lowestSystolic,
               MIN(avgDiastolic) AS lowestDiastolic,
               COALESCE(SUM(CASE WHEN highRiskAlertTriggered = 1 THEN 1 ELSE 0 END), 0)
                   AS highRiskCount
        FROM measurement_sessions
        WHERE measuredAt >= :startInclusive AND measuredAt < :endExclusive
        """
    )
    fun observePeriodStatistics(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<PeriodStatisticsRow>

    @Query("SELECT id FROM measurement_sessions")
    suspend fun getAllSessionIds(): List<String>

    @Query("SELECT id FROM measurement_sessions WHERE id IN (:sessionIds)")
    suspend fun getExistingSessionIds(sessionIds: List<String>): List<String>

    @Query(
        """
        SELECT id, measuredAt, avgSystolic, avgDiastolic, avgPulse, category,
               highRiskAlertTriggered AS containsHighRiskReading
        FROM measurement_sessions
        WHERE measuredAt >= :startInclusive AND measuredAt < :endExclusive
        ORDER BY measuredAt ASC
        """
    )
    fun observeTrendPoints(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<TrendPointRow>>

    @Query(
        """
        SELECT id, measuredAt, avgSystolic, avgDiastolic, avgPulse, category,
               highRiskAlertTriggered AS containsHighRiskReading
        FROM measurement_sessions
        WHERE measuredAt >= :startInclusive AND measuredAt < :endExclusive
        ORDER BY measuredAt ASC
        """
    )
    suspend fun getTrendPoints(
        startInclusive: Long,
        endExclusive: Long
    ): List<TrendPointRow>

    @Query("SELECT COUNT(*) FROM measurement_sessions")
    suspend fun countSessions(): Int

    @Query("SELECT COUNT(*) FROM measurement_readings")
    suspend fun countReadings(): Int

    @Query("DELETE FROM measurement_readings WHERE sessionId = :sessionId")
    suspend fun deleteReadingsBySessionId(sessionId: String)

    @Query("DELETE FROM measurement_readings WHERE sessionId IN (:sessionIds)")
    suspend fun deleteReadingsBySessionIds(sessionIds: List<String>)

    @Query("DELETE FROM measurement_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Transaction
    suspend fun updateSessionWithReadings(
        session: MeasurementSessionEntity,
        readings: List<MeasurementReadingEntity>
    ) {
        deleteReadingsBySessionId(session.id)
        insertSession(session)
        insertReadings(readings)
    }

    @Query("DELETE FROM measurement_readings")
    suspend fun deleteAllReadings()

    @Query("DELETE FROM measurement_sessions")
    suspend fun deleteAllSessions()
}
