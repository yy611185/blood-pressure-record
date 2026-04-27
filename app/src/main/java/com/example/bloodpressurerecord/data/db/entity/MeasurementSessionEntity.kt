package com.example.bloodpressurerecord.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurement_sessions")
data class MeasurementSessionEntity(
    @PrimaryKey val id: String,
    val measuredAt: Long,
    val scene: String,
    val note: String?,
    val symptomsJson: String?,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?,
    val category: String,
    val highRiskAlertTriggered: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
