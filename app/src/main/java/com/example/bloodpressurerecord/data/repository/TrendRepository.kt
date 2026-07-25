package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.dao.TrendPointRow
import com.example.bloodpressurerecord.domain.model.TrendRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TrendRepository {
    fun observeRecords(startInclusive: Long, endExclusive: Long): Flow<List<TrendRecord>>

    fun observeStatistics(startInclusive: Long, endExclusive: Long): Flow<PeriodStatistics>

    suspend fun getRecords(startInclusive: Long, endExclusive: Long): List<TrendRecord>
}

class DefaultTrendRepository(
    private val sessionDao: MeasurementSessionDao
) : TrendRepository {
    override fun observeRecords(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<TrendRecord>> {
        return sessionDao.observeTrendPoints(startInclusive, endExclusive)
            .map { rows -> rows.map { it.toRecord() } }
    }

    override suspend fun getRecords(
        startInclusive: Long,
        endExclusive: Long
    ): List<TrendRecord> {
        return sessionDao.getTrendPoints(startInclusive, endExclusive).map { it.toRecord() }
    }

    override fun observeStatistics(
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

    private fun TrendPointRow.toRecord(): TrendRecord {
        return TrendRecord(
            id = id,
            measuredAt = measuredAt,
            systolic = avgSystolic,
            diastolic = avgDiastolic,
            pulse = avgPulse,
            category = category,
            containsHighRiskReading = containsHighRiskReading
        )
    }
}
