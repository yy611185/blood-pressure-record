package com.example.bloodpressurerecord.domain.model

data class SessionStatPoint(
    val measuredAt: Long,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val highRisk: Boolean
)

data class PeriodAverage(
    val avgSystolic: Int?,
    val avgDiastolic: Int?
)

data class TrendDayPoint(
    val dateKey: String,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val count: Int
)

data class StatisticsResult(
    val average7d: PeriodAverage,
    val average30d: PeriodAverage,
    val weekRecordCount: Int,
    val highRiskCount30d: Int,
    val trend7d: List<TrendDayPoint>,
    val trend30d: List<TrendDayPoint>
)
