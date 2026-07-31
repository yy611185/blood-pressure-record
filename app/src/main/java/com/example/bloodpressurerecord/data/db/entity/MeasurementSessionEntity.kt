package com.example.bloodpressurerecord.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurement_sessions",
    indices = [Index(value = ["measuredAt"])]
)
data class MeasurementSessionEntity(
    @PrimaryKey val id: String,
    val measuredAt: Long,
    val scene: String,
    val note: String?,
    val symptomsJson: String?,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?,
    /** 保存该记录时使用的平均值计算策略，避免之后编辑或导出时按当前全局设置重算。 */
    @ColumnInfo(defaultValue = "'ALL'")
    val averageStrategy: String = "ALL",
    val category: String,
    /**
     * 是否包含高风险原始读数或高风险平均值。
     * 数据库沿用旧列名以保持无损兼容。
     */
    @ColumnInfo(name = "highRiskAlertTriggered")
    val containsHighRiskReading: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
