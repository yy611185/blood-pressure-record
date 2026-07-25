package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.dao.TrendPointRow
import com.example.bloodpressurerecord.domain.model.TrendRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TrendRepository {
    fun observeRecords(startInclusive: Long, endInclusive: Long): Flow<List<TrendRecord>>

    suspend fun getRecords(startInclusive: Long, endExclusive: Long): List<TrendRecord>
}

class DefaultTrendRepository(
    private val sessionDao: MeasurementSessionDao
) : TrendRepository {
    override fun observeRecords(
        startInclusive: Long,
        endInclusive: Long
    ): Flow<List<TrendRecord>> {
        return sessionDao.observeTrendPoints(startInclusive, endInclusive)
            .map { rows -> rows.map { it.toRecord() } }
    }

    override suspend fun getRecords(
        startInclusive: Long,
        endExclusive: Long
    ): List<TrendRecord> {
        return sessionDao.getTrendPoints(startInclusive, endExclusive).map { it.toRecord() }
    }

    private fun TrendPointRow.toRecord(): TrendRecord {
        return TrendRecord(
            id = id,
            measuredAt = measuredAt,
            systolic = avgSystolic,
            diastolic = avgDiastolic,
            pulse = avgPulse,
            category = category
        )
    }
}
