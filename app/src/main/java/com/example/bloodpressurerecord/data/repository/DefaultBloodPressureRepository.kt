package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionWithReadings
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.domain.calculator.AverageCalculator
import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.domain.calculator.HighRiskJudge
import com.example.bloodpressurerecord.domain.model.ReadingValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.UUID

class DefaultBloodPressureRepository(
    private val sessionDao: MeasurementSessionDao
) : BloodPressureRepository {

    override fun observeSessionCount(): Flow<Int> {
        return sessionDao.observeSessionsWithReadings().map { it.size }
    }

    override fun observeSessions(): Flow<List<SessionRecord>> {
        return sessionDao.observeSessionsWithReadings().map { list -> list.map { it.toRecord() } }
    }

    override fun observeSession(sessionId: String): Flow<SessionRecord?> {
        return sessionDao.observeSessionWithReadings(sessionId).map { it?.toRecord() }
    }

    override suspend fun saveSession(input: SaveSessionInput): Result<String> = runCatching {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = buildSessionEntity(
            sessionId = sessionId,
            input = input,
            createdAt = now,
            updatedAt = now
        )
        val readings = buildReadingEntities(sessionId, input.readings)
        sessionDao.insertSessionWithReadings(session, readings)
        sessionId
    }

    override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> = runCatching {
        val existing = sessionDao.getSessionWithReadings(sessionId)
            ?: error("记录不存在，无法编辑")
        val now = System.currentTimeMillis()
        val session = buildSessionEntity(
            sessionId = sessionId,
            input = input,
            createdAt = existing.session.createdAt,
            updatedAt = now
        )
        val readings = buildReadingEntities(sessionId, input.readings)
        sessionDao.updateSessionWithReadings(session, readings)
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        sessionDao.deleteSessionById(sessionId)
    }

    private fun buildSessionEntity(
        sessionId: String,
        input: SaveSessionInput,
        createdAt: Long,
        updatedAt: Long
    ): MeasurementSessionEntity {
        val average = AverageCalculator.calculate(
            input.readings.map { ReadingValue(it.systolic, it.diastolic, it.pulse) }
        )
        val category = CategoryCalculator.calculate(average.avgSystolic, average.avgDiastolic)
        val highRisk = HighRiskJudge.shouldTrigger(average.avgSystolic, average.avgDiastolic)
        val symptomsJson = if (input.symptoms.isEmpty()) null else JSONArray(input.symptoms).toString()
        return MeasurementSessionEntity(
            id = sessionId,
            measuredAt = input.measuredAt,
            scene = input.scene,
            note = input.note?.takeIf { it.isNotBlank() },
            symptomsJson = symptomsJson,
            avgSystolic = average.avgSystolic,
            avgDiastolic = average.avgDiastolic,
            avgPulse = average.avgPulse,
            category = category.name,
            highRiskAlertTriggered = highRisk,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun buildReadingEntities(
        sessionId: String,
        inputs: List<SessionReadingInput>
    ): List<MeasurementReadingEntity> {
        return inputs.mapIndexed { index, reading ->
            MeasurementReadingEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                orderIndex = index + 1,
                systolic = reading.systolic,
                diastolic = reading.diastolic,
                pulse = reading.pulse
            )
        }
    }

    private fun MeasurementSessionWithReadings.toRecord(): SessionRecord {
        val symptoms = parseSymptoms(session.symptomsJson)
        return SessionRecord(
            id = session.id,
            measuredAt = session.measuredAt,
            scene = session.scene,
            note = session.note,
            symptoms = symptoms,
            avgSystolic = session.avgSystolic,
            avgDiastolic = session.avgDiastolic,
            avgPulse = session.avgPulse,
            category = session.category,
            highRiskAlertTriggered = session.highRiskAlertTriggered,
            readings = readings.sortedBy { it.orderIndex }.map {
                SessionReading(
                    id = it.id,
                    orderIndex = it.orderIndex,
                    systolic = it.systolic,
                    diastolic = it.diastolic,
                    pulse = it.pulse
                )
            }
        )
    }

    private fun parseSymptoms(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { index -> arr.getString(index) }
        }.getOrDefault(emptyList())
    }
}
