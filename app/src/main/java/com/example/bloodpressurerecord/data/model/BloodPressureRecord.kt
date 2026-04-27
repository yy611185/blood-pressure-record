package com.example.bloodpressurerecord.data.model

data class BloodPressureRecord(
    val id: Long = 0,
    val memberName: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val measuredAtMillis: Long,
    val level: String,
    val remark: String
)
