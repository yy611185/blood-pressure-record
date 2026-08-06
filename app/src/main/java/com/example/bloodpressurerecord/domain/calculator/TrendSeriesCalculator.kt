package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

object TrendSeriesCalculator {
    const val CHART_SAFE_MIN = 40
    /** 输入规则允许的最高收缩压；261–300 必须仍可在趋势图中看见。 */
    const val CHART_SAFE_MAX = 300
    private const val CHART_AXIS_MAX = 320

    fun rangeStart(range: TrendRange, nowMillis: Long, zoneId: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return when (range) {
            TrendRange.DAYS_7 -> today.minusDays(6).atStartOfDay(zoneId).toInstant().toEpochMilli()
            TrendRange.DAYS_30 -> today.minusDays(29).atStartOfDay(zoneId).toInstant().toEpochMilli()
            TrendRange.ALL -> 0L
        }
    }

    fun build(
        records: List<TrendRecord>,
        range: TrendRange,
        nowMillis: Long,
        zoneId: ZoneId,
        targetSystolic: Int? = null,
        targetDiastolic: Int? = null
    ): TrendSeries {
        val requestedStart = rangeStart(range, nowMillis, zoneId)
        val sorted = records
            .asSequence()
            .filter { it.measuredAt in requestedStart..nowMillis }
            .sortedBy { it.measuredAt }
            .toList()
        val points = when (range) {
            TrendRange.ALL -> sorted.toDailyPoints(zoneId)
            TrendRange.DAYS_7,
            TrendRange.DAYS_30 -> sorted.map { it.toRawPoint() }
        }
        val seriesStart = when {
            range != TrendRange.ALL -> requestedStart
            points.isNotEmpty() -> points.first().intervalStart
            else -> Instant.ofEpochMilli(nowMillis)
                .atZone(zoneId)
                .toLocalDate()
                .minusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        }
        val seriesEnd = nowMillis.coerceAtLeast(seriesStart + 1L)

        return TrendSeries(
            range = range,
            points = points,
            rawRecordCount = sorted.size,
            averageSystolic = sorted.takeIf { it.isNotEmpty() }
                ?.map { it.systolic }
                ?.average()
                ?.roundToInt(),
            averageDiastolic = sorted.takeIf { it.isNotEmpty() }
                ?.map { it.diastolic }
                ?.average()
                ?.roundToInt(),
            yAxis = calculateYAxis(points, targetSystolic, targetDiastolic),
            rangeStart = seriesStart,
            rangeEnd = seriesEnd
        )
    }

    fun calculateYAxis(
        points: List<TrendPoint>,
        targetSystolic: Int?,
        targetDiastolic: Int?
    ): TrendYAxis {
        val values = buildList {
            points.forEach { point ->
                // 仅把超出输入规则的历史脏值限制到合法边界；合法的 261–300
                // 不再被旧的 260 上限吞掉。
                add(point.systolic.coerceIn(40, CHART_SAFE_MAX))
                add(point.diastolic.coerceIn(20, 200))
            }
            add(90)
            add(140)
            targetSystolic?.takeIf { it in 40..CHART_SAFE_MAX }?.let(::add)
            targetDiastolic?.takeIf { it in 20..200 }?.let(::add)
        }
        val rawMin = values.minOrNull() ?: 80
        val rawMax = values.maxOrNull() ?: 160
        val min = (floor((rawMin - 10) / 10.0) * 10).toInt().coerceAtLeast(20)
        val max = (ceil((rawMax + 10) / 10.0) * 10).toInt().coerceAtMost(CHART_AXIS_MAX)
        val span = (max - min).coerceAtLeast(20)
        val tickStep = when {
            span <= 70 -> 10
            span <= 140 -> 20
            else -> 40
        }
        return TrendYAxis(min = min, max = max, tickStep = tickStep)
    }

    private fun TrendRecord.toRawPoint(): TrendPoint {
        return TrendPoint(
            id = id,
            timestamp = measuredAt,
            intervalStart = measuredAt,
            intervalEndExclusive = measuredAt.saturatedPlusOne(),
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse,
            category = category,
            containsHighRiskReading = containsHighRiskReading,
            recordCount = 1,
            aggregation = TrendAggregation.RAW
        )
    }

    private fun List<TrendRecord>.toDailyPoints(zoneId: ZoneId): List<TrendPoint> {
        return groupBy {
            Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).toLocalDate()
        }.toSortedMap().map { (date, dayRecords) ->
            val range = date.toEpochMillisRange(zoneId)
            val systolic = dayRecords.map { it.systolic }.average().roundToInt()
            val diastolic = dayRecords.map { it.diastolic }.average().roundToInt()
            val pulseValues = dayRecords.mapNotNull { it.pulse }
            TrendPoint(
                id = "day:$date",
                timestamp = range.startInclusive,
                intervalStart = range.startInclusive,
                intervalEndExclusive = range.endExclusive,
                systolic = systolic,
                diastolic = diastolic,
                pulse = pulseValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                category = CategoryCalculator.calculate(systolic, diastolic).name,
                containsHighRiskReading = dayRecords.any { it.containsHighRiskReading },
                recordCount = dayRecords.size,
                aggregation = TrendAggregation.DAILY
            )
        }
    }

    private fun Long.saturatedPlusOne(): Long {
        return if (this == Long.MAX_VALUE) Long.MAX_VALUE else this + 1L
    }
}
