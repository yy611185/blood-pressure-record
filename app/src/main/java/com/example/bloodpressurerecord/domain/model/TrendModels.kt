package com.example.bloodpressurerecord.domain.model

enum class TrendRange(val label: String, val title: String) {
    DAYS_7("7天", "最近7天"),
    DAYS_30("30天", "最近30天"),
    ALL("全部", "全部记录")
}

enum class TrendAggregation {
    /** 每条测量记录一个节点（仅 7 天范围使用）。 */
    RAW,

    /** 每个自然日一个节点，数值为当天全部记录的平均值。 */
    DAILY
}

/** 页面展示用的粒度文案，和实际聚合方式同源，避免标题与数据不一致。 */
fun TrendAggregation.displayLabel(): String = when (this) {
    TrendAggregation.RAW -> "每次测量"
    TrendAggregation.DAILY -> "每日平均"
}

data class TrendRecord(
    val id: String,
    val measuredAt: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val category: String,
    val containsHighRiskReading: Boolean = false
)

data class TrendPoint(
    val id: String,
    val timestamp: Long,
    val intervalStart: Long,
    val intervalEndExclusive: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val category: String,
    val containsHighRiskReading: Boolean,
    val recordCount: Int,
    val aggregation: TrendAggregation
)

data class TrendYAxis(
    val min: Int,
    val max: Int,
    val tickStep: Int
)

data class TrendSeries(
    val range: TrendRange,
    val points: List<TrendPoint>,
    val rawRecordCount: Int,
    val averageSystolic: Int?,
    val averageDiastolic: Int?,
    val yAxis: TrendYAxis,
    val rangeStart: Long,
    val rangeEnd: Long,
    /** 本范围的聚合方式：7 天与 30 天为原始记录，全部为每日平均。 */
    val aggregation: TrendAggregation = TrendAggregation.RAW,
    /** 范围起点（自然周期边界），与真实样本区间区分开。 */
    val windowStart: Long = rangeStart,
    /** 范围内第一次测量的时间；没有记录时为 null。 */
    val firstMeasuredAt: Long? = null,
    /** 范围内最后一次测量的时间；没有记录时为 null。 */
    val lastMeasuredAt: Long? = null
)
