package com.example.bloodpressurerecord.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class MeasurementSessionWithReadings(
    @Embedded val session: MeasurementSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val readings: List<MeasurementReadingEntity>
)
