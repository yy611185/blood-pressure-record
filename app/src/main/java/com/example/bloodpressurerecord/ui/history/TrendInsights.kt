package com.example.bloodpressurerecord.ui.history

import com.example.bloodpressurerecord.domain.calculator.BloodPressureRules
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.model.TrendRecord
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** 按原始测量记录计算展示统计，避免把“每日平均点”当成单次测量。 */
data class TrendInsights(
    val categoryCounts: Map<BloodPressureCategory, Int> = emptyMap(),
    val targetRate: Int? = null,
    val previousTargetRate: Int? = null,
    val highestReading: TrendRecord? = null,
    val morningAverage: Pair<Int, Int>? = null,
    val afternoonAverage: Pair<Int, Int>? = null,
    val morningCount: Int = 0,
    val afternoonCount: Int = 0
)

internal object TrendInsightCalculator {
    fun calculate(
        records: List<TrendRecord>,
        previousRecords: List<TrendRecord>,
        targetSystolic: Int?,
        targetDiastolic: Int?,
        zoneId: ZoneId
    ): TrendInsights {
        val morning = records.filter { Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).hour < 12 }
        val afternoon = records.filter { Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).hour >= 12 }
        fun average(values: List<TrendRecord>): Pair<Int, Int>? = values.takeIf { it.isNotEmpty() }
            ?.let { list ->
                list.map { it.systolic }.average().roundToInt() to
                    list.map { it.diastolic }.average().roundToInt()
            }
        fun targetRate(values: List<TrendRecord>): Int? {
            if (targetSystolic == null || targetDiastolic == null || values.isEmpty()) return null
            return (values.count {
                it.systolic < targetSystolic && it.diastolic < targetDiastolic &&
                    it.systolic >= 90 && it.diastolic >= 60
            } * 100.0 / values.size).roundToInt()
        }
        return TrendInsights(
            categoryCounts = records.groupingBy {
                BloodPressureRules.category(it.systolic, it.diastolic)
            }.eachCount(),
            targetRate = targetRate(records),
            previousTargetRate = targetRate(previousRecords),
            highestReading = records.maxWithOrNull(compareBy<TrendRecord> { it.systolic }.thenBy { it.diastolic }),
            morningAverage = average(morning),
            afternoonAverage = average(afternoon),
            morningCount = morning.size,
            afternoonCount = afternoon.size
        )
    }
}
