package com.example.bloodpressurerecord.domain.model

data class BloodPressureMeasurement(
    val id: Long = 0,
    val memberName: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val measuredAtMillis: Long,
    val level: String
)
