package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionWithReadings
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.calculator.MeasurementDerivation
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.model.ReadingValue
import com.example.bloodpressurerecord.domain.time.MeasurementTimestampValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.UUID

class DefaultBloodPressureRepository(
    private val sessionDao: MeasurementSessionDao,
    /** 数据变化后的回调（用于刷新桌面小部件等）。 */
    private val onDataChanged: (() -> Unit)? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : BloodPressureRepository {

    override fun observeSession(sessionId: String): Flow<SessionRecord?> {
        return sessionDao.observeSessionWithReadings(sessionId).map { it?.toRecord() }
    }

    override fun observeLatestSessionSummary(): Flow<LatestSessionSummary?> {
        return sessionDao.observeLatestSessionSummary().map { row ->
            row?.let {
                LatestSessionSummary(
                    id = it.id,
                    measuredAt = it.measuredAt,
                    avgSystolic = it.avgSystolic,
                    avgDiastolic = it.avgDiastolic,
                    category = it.category,
                    containsHighRiskReading = it.containsHighRiskReading
                )
            }
        }
    }

    override fun observeCalendarSessionSummaries(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<CalendarSessionSummary>> {
        return sessionDao.observeCalendarSessionSummaries(startInclusive, endExclusive).map { rows ->
            rows.map {
                CalendarSessionSummary(
                    measuredAt = it.measuredAt,
                    noteSummary = it.noteSummary,
                    containsHighRiskReading = it.containsHighRiskReading
                )
            }
        }
    }

    override fun observeSessionSummariesInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<SessionSummary>> {
        return sessionDao.observeSessionSummariesInRange(startInclusive, endExclusive)
            .map { rows -> rows.map(::toSummary) }
    }

    override fun observePeriodStatistics(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<PeriodStatistics> {
        return sessionDao.observePeriodStatistics(startInclusive, endExclusive).map { row ->
            PeriodStatistics(
                recordCount = row.recordCount,
                averageSystolic = row.averageSystolic,
                averageDiastolic = row.averageDiastolic,
                averagePulse = row.averagePulse,
                highestSystolic = row.highestSystolic,
                highestDiastolic = row.highestDiastolic,
                lowestSystolic = row.lowestSystolic,
                lowestDiastolic = row.lowestDiastolic,
                highRiskCount = row.highRiskCount
            )
        }
    }

    override suspend fun saveSession(input: SaveSessionInput): Result<String> = runCatching {
        requireValidMeasurementTime(input.measuredAt)
        val sessionId = UUID.randomUUID().toString()
        val now = nowMillis()
        val session = buildSessionEntity(
            sessionId = sessionId,
            input = input,
            createdAt = now,
            updatedAt = now
        )
        val readings = buildReadingEntities(sessionId, input.readings)
        sessionDao.insertSessionWithReadings(session, readings)
        onDataChanged?.invoke()
        sessionId
    }

    override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> = runCatching {
        requireValidMeasurementTime(input.measuredAt)
        val existing = sessionDao.getSessionWithReadings(sessionId)
            ?: error("记录不存在，无法编辑")
        val now = nowMillis()
        val session = buildSessionEntity(
            sessionId = sessionId,
            input = input,
            createdAt = existing.session.createdAt,
            updatedAt = now
        )
        val readings = buildReadingEntities(sessionId, input.readings)
        check(sessionDao.updateSessionWithReadings(session, readings)) {
            "记录不存在，无法编辑"
        }
        onDataChanged?.invoke()
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        check(sessionDao.deleteSessionById(sessionId) == 1) { "记录不存在，无法删除" }
        onDataChanged?.invoke()
    }

    override suspend fun restoreSession(session: SessionRecord): Result<Unit> = runCatching {
        requireValidMeasurementTime(session.measuredAt)
        val orderedReadings = session.readings.sortedBy { it.orderIndex }.map {
            SessionReadingInput(it.systolic, it.diastolic, it.pulse)
        }
        val input = SaveSessionInput(
            measuredAt = session.measuredAt,
            scene = session.scene,
            note = session.note,
            symptoms = session.symptoms,
            readings = orderedReadings,
            averageStrategy = session.averageStrategy
        )
        val entity = buildSessionEntity(
            sessionId = session.id,
            input = input,
            createdAt = session.createdAt.takeIf { it > 0L } ?: nowMillis(),
            updatedAt = session.updatedAt.takeIf { it > 0L } ?: session.createdAt.takeIf { it > 0L }
                ?: nowMillis()
        )
        val readings = session.readings.sortedBy { it.orderIndex }.mapIndexed { index, reading ->
            MeasurementReadingEntity(
                id = reading.id,
                sessionId = session.id,
                orderIndex = index + 1,
                systolic = reading.systolic,
                diastolic = reading.diastolic,
                pulse = reading.pulse
            )
        }
        sessionDao.insertSessionWithReadings(entity, readings)
        onDataChanged?.invoke()
    }

    private fun buildSessionEntity(
        sessionId: String,
        input: SaveSessionInput,
        createdAt: Long,
        updatedAt: Long
    ): MeasurementSessionEntity {
        val readingValues = input.readings.map { ReadingValue(it.systolic, it.diastolic, it.pulse) }
        require(MeasurementInputRules.validateReadings(readingValues) == null) {
            "每次测量必须包含 ${MeasurementInputRules.MIN_READING_COUNT} 至 " +
                "${MeasurementInputRules.MAX_READING_COUNT} 组读数"
        }
        readingValues.forEachIndexed { index, reading ->
            require(MeasurementInputRules.validateReading(reading) == null) {
                "第 ${index + 1} 组读数不符合统一输入规则"
            }
        }
        val derived = MeasurementDerivation.derive(readingValues, input.averageStrategy)
        val symptomsJson = if (input.symptoms.isEmpty()) null else JSONArray(input.symptoms).toString()
        return MeasurementSessionEntity(
            id = sessionId,
            measuredAt = input.measuredAt,
            scene = input.scene,
            note = input.note?.takeIf { it.isNotBlank() },
            symptomsJson = symptomsJson,
            avgSystolic = derived.average.avgSystolic,
            avgDiastolic = derived.average.avgDiastolic,
            avgPulse = derived.average.avgPulse,
            averageStrategy = input.averageStrategy.name,
            category = derived.category.name,
            containsHighRiskReading = derived.containsHighRiskReading,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun requireValidMeasurementTime(measuredAt: Long) {
        require(MeasurementTimestampValidator.validate(measuredAt, nowMillis()) == null) {
            MeasurementTimestampValidator.FUTURE_MEASUREMENT_TIME_MESSAGE
        }
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
            averageStrategy = session.averageStrategy.toAverageStrategy(),
            category = session.category,
            containsHighRiskReading = session.containsHighRiskReading,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
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

    private fun toSummary(
        row: com.example.bloodpressurerecord.data.db.dao.SessionSummaryRow
    ): SessionSummary {
        return SessionSummary(
            id = row.id,
            measuredAt = row.measuredAt,
            avgSystolic = row.avgSystolic,
            avgDiastolic = row.avgDiastolic,
            avgPulse = row.avgPulse,
            category = row.category,
            scene = row.scene,
            noteSummary = row.noteSummary,
            containsHighRiskReading = row.containsHighRiskReading
        )
    }

    private fun parseSymptoms(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { index -> arr.getString(index) }
        }.getOrDefault(emptyList())
    }

    private fun String.toAverageStrategy(): AverageStrategy {
        return AverageStrategy.entries.firstOrNull { it.name == this } ?: AverageStrategy.ALL
    }
}
