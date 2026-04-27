package com.example.bloodpressurerecord.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bp_measurements")
data class BloodPressureMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberName: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val measuredAtMillis: Long,
    val level: String
)
