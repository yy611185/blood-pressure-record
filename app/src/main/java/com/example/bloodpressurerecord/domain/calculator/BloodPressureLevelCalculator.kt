package com.example.bloodpressurerecord.domain.calculator

object BloodPressureLevelCalculator {
    fun calculateLabel(systolic: Int?, diastolic: Int?): String {
        if (systolic == null || diastolic == null) return "待判断"
        return when {
            systolic < 90 || diastolic < 60 -> "偏低（仅供参考）"
            systolic >= 180 || diastolic >= 110 -> "3级偏高（仅供参考）"
            systolic >= 160 || diastolic >= 100 -> "2级偏高（仅供参考）"
            systolic >= 140 || diastolic >= 90 -> "1级偏高（仅供参考）"
            systolic >= 130 || diastolic >= 85 -> "正常高值（仅供参考）"
            else -> "理想范围（仅供参考）"
        }
    }
}
