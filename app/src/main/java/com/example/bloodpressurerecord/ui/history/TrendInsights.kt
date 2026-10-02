package com.example.bloodpressurerecord.ui.history

import com.example.bloodpressurerecord.domain.calculator.BloodPressureRules
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.model.DayNightAverage
import com.example.bloodpressurerecord.domain.model.TrendRecord
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** 每个 Session 的已保存代表值占一份权重，记录天数按本地自然日去重。 */
data class TrendInsights(
    val categoryCounts: Map<BloodPressureCategory, Int> = emptyMap(),
    val targetRate: Int? = null,
    val previousTargetRate: Int? = null,
    val highestReading: TrendRecord? = null,
    val lowestReading: TrendRecord? = null,
    val periodAverage: Pair<Int, Int>? = null,
    val morningAverage: Pair<Int, Int>? = null,
    val eveningAverage: Pair<Int, Int>? = null,
    val daytimeAverage: DayNightAverage = DayNightAverage(),
    val nighttimeAverage: DayNightAverage = DayNightAverage(),
    val recordCount: Int = 0,
    val morningCount: Int = 0,
    val eveningCount: Int = 0,
    val recordDays: Int = 0,
    val morningDays: Int = 0,
    val eveningDays: Int = 0,
    val highRiskCount: Int = 0
)

internal object TrendInsightCalculator {
    fun calculate(
        records: List<TrendRecord>,
        previousRecords: List<TrendRecord>,
        targetSystolic: Int?,
        targetDiastolic: Int?,
        zoneId: ZoneId
    ): TrendInsights {
        val morning = records.filter { Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).hour in 5..11 }
        val evening = records.filter { Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).hour in 18..23 }
        // 夜间包含 22:00 至次日 05:59，范围仍按页面所选自然日窗口筛选。
        val (daytime, nighttime) = records.partition {
            Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).hour in 6..21
        }
        fun recordDays(values: List<TrendRecord>): Int = values.map {
            Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).toLocalDate()
        }.toSet().size
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
        fun dayNightAverage(values: List<TrendRecord>): DayNightAverage {
            val pressure = average(values)
            return DayNightAverage(
                systolic = pressure?.first,
                diastolic = pressure?.second,
                sessionCount = values.size,
                recordDays = recordDays(values)
            )
        }
        return TrendInsights(
            categoryCounts = records.groupingBy {
                BloodPressureRules.category(it.systolic, it.diastolic)
            }.eachCount(),
            targetRate = targetRate(records),
            previousTargetRate = targetRate(previousRecords),
            highestReading = records.maxWithOrNull(compareBy<TrendRecord> { it.systolic }.thenBy { it.diastolic }),
            lowestReading = records.minWithOrNull(compareBy<TrendRecord> { it.systolic }.thenBy { it.diastolic }),
            periodAverage = average(records),
            morningAverage = average(morning),
            eveningAverage = average(evening),
            daytimeAverage = dayNightAverage(daytime),
            nighttimeAverage = dayNightAverage(nighttime),
            recordCount = records.size,
            morningCount = morning.size,
            eveningCount = evening.size,
            recordDays = recordDays(records),
            morningDays = recordDays(morning),
            eveningDays = recordDays(evening),
            highRiskCount = records.count { it.containsHighRiskReading }
        )
    }
}
