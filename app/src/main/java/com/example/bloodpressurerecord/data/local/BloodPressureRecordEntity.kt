package com.example.bloodpressurerecord.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blood_pressure_records")
data class BloodPressureRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberName: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val measuredAtMillis: Long,
    val level: String,
    val remark: String
)
