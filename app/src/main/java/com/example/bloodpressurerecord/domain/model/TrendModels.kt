package com.example.bloodpressurerecord.domain.model

enum class TrendRange(val label: String, val title: String) {
    DAYS_7("7天", "最近7天"),
    DAYS_30("30天", "最近30天"),
    ALL("全部", "全部记录")
}

enum class TrendAggregation {
    /** 每个 Measurement Session 的已保存代表值一个节点，适用于所有周期。 */
    RAW,

    /** 兼容旧图表的每日平均语义。 */
    DAILY,

    /** 自然日内 Session 代表值的最小值、最大值与平均值。 */
    DAILY_RANGE
}

/** 页面展示用的粒度文案，和实际聚合方式同源，避免标题与数据不一致。 */
fun TrendAggregation.displayLabel(): String = when (this) {
    TrendAggregation.RAW -> "每次测量"
    TrendAggregation.DAILY -> "每日平均"
    TrendAggregation.DAILY_RANGE -> "每日范围"
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
    val aggregation: TrendAggregation,
    val systolicMin: Int = systolic,
    val systolicMax: Int = systolic,
    val diastolicMin: Int = diastolic,
    val diastolicMax: Int = diastolic,
    val pulseMin: Int? = pulse,
    val pulseMax: Int? = pulse,
    /** 只统计实际保存了脉搏的 Session，缺测不参与平均。 */
    val pulseRecordCount: Int = if (pulse == null) 0 else recordCount,
    /** 按测量时间排序的来源 Session，用于打开对应记录明细。 */
    val sourceRecordIds: List<String> = if (aggregation == TrendAggregation.RAW) listOf(id) else emptyList()
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
    val averagePulse: Int? = null,
    val yAxis: TrendYAxis,
    val rangeStart: Long,
    val rangeEnd: Long,
    /** 节点粒度；所有统计均使用每次 Session 的已保存代表值。 */
    val aggregation: TrendAggregation = TrendAggregation.RAW,
    /** 范围起点（自然周期边界），与真实样本区间区分开。 */
    val windowStart: Long = rangeStart,
    /** 范围内第一次测量的时间；没有记录时为 null。 */
    val firstMeasuredAt: Long? = null,
    /** 范围内最后一次测量的时间；没有记录时为 null。 */
    val lastMeasuredAt: Long? = null
)
