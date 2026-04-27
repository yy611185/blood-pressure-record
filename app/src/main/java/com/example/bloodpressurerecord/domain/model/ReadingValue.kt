package com.example.bloodpressurerecord.domain.model

data class ReadingValue(
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?
)
