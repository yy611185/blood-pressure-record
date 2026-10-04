package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** 派生展示节点，不重算或改写已保存的 Session 代表值。 */
object TrendAggregationCalculator {
    fun build(
        records: List<TrendRecord>,
        range: TrendRange,
        nowMillis: Long,
        zoneId: ZoneId,
        aggregation: TrendAggregation,
        targetSystolic: Int? = null,
        targetDiastolic: Int? = null
    ): TrendSeries {
        // 复用原始筛选口径和完整期间统计，不能对每日均值再次等权平均。
        val rawSeries = TrendSeriesCalculator.build(
            records, range, nowMillis, zoneId, targetSystolic, targetDiastolic
        )
        if (aggregation == TrendAggregation.RAW) return rawSeries

        val endExclusive = if (nowMillis == Long.MAX_VALUE) Long.MAX_VALUE else nowMillis + 1L
        val points = rawSeries.points.groupBy { point ->
            Instant.ofEpochMilli(point.timestamp).atZone(zoneId).toLocalDate()
        }.map { (date, samples) ->
            val systolic = samples.map { it.systolic }.average().roundToInt()
            val diastolic = samples.map { it.diastolic }.average().roundToInt()
            val pulses = samples.mapNotNull { it.pulse }
            val firstTimestamp = samples.first().timestamp
            val lastTimestamp = samples.last().timestamp
            TrendPoint(
                id = "day:$date",
                // 实际样本的中点不会落在今天尚未发生的时段。
                timestamp = firstTimestamp + (lastTimestamp - firstTimestamp) / 2L,
                intervalStart = maxOf(
                    date.atStartOfDay(zoneId).toInstant().toEpochMilli(), rawSeries.rangeStart
                ),
                intervalEndExclusive = minOf(
                    date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(), endExclusive
                ),
                systolic = systolic,
                diastolic = diastolic,
                pulse = pulses.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                category = BloodPressureRules.category(systolic, diastolic).name,
                containsHighRiskReading = samples.any { it.containsHighRiskReading },
                recordCount = samples.size,
                aggregation = aggregation,
                systolicMin = samples.minOf { it.systolic },
                systolicMax = samples.maxOf { it.systolic },
                diastolicMin = samples.minOf { it.diastolic },
                diastolicMax = samples.maxOf { it.diastolic },
                pulseMin = pulses.minOrNull(),
                pulseMax = pulses.maxOrNull(),
                pulseRecordCount = pulses.size,
                sourceRecordIds = samples.map { it.id }
            )
        }
        return rawSeries.copy(
            points = points,
            aggregation = aggregation,
            yAxis = TrendSeriesCalculator.calculateYAxis(points, targetSystolic, targetDiastolic)
        )
    }
}
