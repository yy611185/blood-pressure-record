package com.example.bloodpressurerecord.domain.model

data class AverageResult(
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?
)
