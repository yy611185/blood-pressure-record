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
    val category: String
)

@Dao
interface MeasurementSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: MeasurementSessionEntity)

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

    @Transaction
    @Query("SELECT * FROM measurement_sessions ORDER BY measuredAt DESC")
    fun observeSessionsWithReadings(): Flow<List<MeasurementSessionWithReadings>>

    @Transaction
    @Query("SELECT * FROM measurement_sessions ORDER BY measuredAt DESC")
    suspend fun getAllSessionsWithReadings(): List<MeasurementSessionWithReadings>

    @Query(
        """
        SELECT id, measuredAt, avgSystolic, avgDiastolic, avgPulse, category
        FROM measurement_sessions
        WHERE measuredAt >= :startInclusive AND measuredAt <= :endInclusive
        ORDER BY measuredAt ASC
        """
    )
    fun observeTrendPoints(
        startInclusive: Long,
        endInclusive: Long
    ): Flow<List<TrendPointRow>>

    @Query(
        """
        SELECT id, measuredAt, avgSystolic, avgDiastolic, avgPulse, category
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
