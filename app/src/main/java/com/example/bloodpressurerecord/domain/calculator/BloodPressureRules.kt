package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.AverageResult
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.model.ReadingValue

/**
 * 血压分级与风险阈值的唯一来源。
 *
 * 分级采用《中国高血压防治指南》成人诊室血压标准；
 * “高风险提醒”是独立于分级的急症提示阈值（≥180 和/或 ≥120，
 * 对应高血压急症/亚急症的常用界值）。
 *
 * 这些结果只用于本应用内的记录提示，不替代医疗诊断。
 */
object BloodPressureRules {
    /** 急症提示阈值（含边界）：收缩压 ≥180 或舒张压 ≥120。 */
    const val HIGH_RISK_SYSTOLIC_INCLUSIVE = 180
    const val HIGH_RISK_DIASTOLIC_INCLUSIVE = 120

    /**
     * 分级规则：先按“和/或、就高不就低”判定各级高血压，
     * 无升高分级时再判断偏低提示（收缩压 <90 或舒张压 <60）。
     * 边界组合（如 125/55）按升高一侧归类为正常高值。
     */
    fun category(systolic: Int, diastolic: Int): BloodPressureCategory {
        return when {
            systolic >= 180 || diastolic >= 110 -> BloodPressureCategory.STAGE3
            systolic >= 160 || diastolic >= 100 -> BloodPressureCategory.STAGE2
            systolic >= 140 || diastolic >= 90 -> BloodPressureCategory.STAGE1
            systolic >= 120 || diastolic >= 80 -> BloodPressureCategory.HIGH_NORMAL
            systolic < 90 || diastolic < 60 -> BloodPressureCategory.LOW
            else -> BloodPressureCategory.NORMAL
        }
    }

    fun isHighRisk(systolic: Int, diastolic: Int): Boolean {
        return systolic >= HIGH_RISK_SYSTOLIC_INCLUSIVE ||
            diastolic >= HIGH_RISK_DIASTOLIC_INCLUSIVE
    }

    /**
     * 一次测量是否包含高风险读数的唯一判断入口。
     *
     * 原始任一组或最终平均值达到急症提示阈值都返回 true。
     * 无论平均值采用哪种策略，原始读数始终全部参与判断（安全优先）。
     */
    fun containsHighRiskReading(
        readings: List<ReadingValue>,
        average: AverageResult
    ): Boolean {
        return readings.any { isHighRisk(it.systolic, it.diastolic) } ||
            isHighRisk(average.avgSystolic, average.avgDiastolic)
    }
}
