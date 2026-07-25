package com.example.bloodpressurerecord.domain.model

enum class TrendRange(val label: String, val title: String) {
    DAYS_7("7天", "最近7天"),
    DAYS_30("30天", "最近30天"),
    ALL("全部", "全部记录")
}

enum class TrendAggregation {
    RAW,
    DAILY
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
    val rangeEnd: Long
)
