package com.example.bloodpressurerecord.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurement_readings",
    foreignKeys = [
        ForeignKey(
            entity = MeasurementSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "orderIndex"], unique = true)
    ]
)
data class MeasurementReadingEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val orderIndex: Int,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?
)
