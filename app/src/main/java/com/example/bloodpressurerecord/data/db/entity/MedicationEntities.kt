package com.example.bloodpressurerecord.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一种药品：名称 + 每次服用数量（如“1片”）。 */
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String,
    val enabled: Boolean = true,
    val createdAt: Long
)

/** 药品的一个每日服药时间点（一种药可配置早/中/晚多个时间）。 */
@Entity(
    tableName = "medication_times",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId")]
)
data class MedicationTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    /** "HH:mm"，与晨晚提醒时间的存储格式一致。 */
    val timeText: String
)

/**
 * 每日服药勾选历史：某个服药时间点在某一天被勾选“已服”。
 * 取消勾选即删除对应行；时间点被删除时历史级联清理。
 */
@Entity(
    tableName = "medication_intake_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicationTimeEntity::class,
            parentColumns = ["id"],
            childColumns = ["timeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["timeId", "epochDay"], unique = true),
        Index("epochDay")
    ]
)
data class MedicationIntakeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val timeId: Long,
    /** LocalDate.toEpochDay()，本地时区的自然日。 */
    val epochDay: Long,
    /** 勾选时刻（epoch 毫秒）。 */
    val takenAt: Long
)
