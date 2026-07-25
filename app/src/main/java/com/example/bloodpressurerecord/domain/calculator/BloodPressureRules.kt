package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.AverageResult
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.model.ReadingValue

/**
 * 血压分级与风险阈值的唯一来源。
 *
 * 这些结果只用于本应用内的记录提示，不替代医疗诊断。
 */
object BloodPressureRules {
    const val HIGH_RISK_SYSTOLIC_EXCLUSIVE = 180
    const val HIGH_RISK_DIASTOLIC_EXCLUSIVE = 120

    fun category(systolic: Int, diastolic: Int): BloodPressureCategory {
        return when {
            isHighRisk(systolic, diastolic) -> BloodPressureCategory.SEVERE
            systolic >= 140 || diastolic >= 90 -> BloodPressureCategory.STAGE2
            systolic >= 130 || diastolic >= 80 -> BloodPressureCategory.STAGE1
            systolic in 120..129 && diastolic < 80 -> BloodPressureCategory.ELEVATED
            else -> BloodPressureCategory.NORMAL
        }
    }

    fun isHighRisk(systolic: Int, diastolic: Int): Boolean {
        return systolic > HIGH_RISK_SYSTOLIC_EXCLUSIVE ||
            diastolic > HIGH_RISK_DIASTOLIC_EXCLUSIVE
    }

    /**
     * 一次测量是否包含高风险读数的唯一判断入口。
     *
     * 原始任一组或最终平均值达到高风险标准都返回 true。
     */
    fun containsHighRiskReading(
        readings: List<ReadingValue>,
        average: AverageResult
    ): Boolean {
        return readings.any { isHighRisk(it.systolic, it.diastolic) } ||
            isHighRisk(average.avgSystolic, average.avgDiastolic)
    }
}
