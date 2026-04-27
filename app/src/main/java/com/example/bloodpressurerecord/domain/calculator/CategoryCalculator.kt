package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.BloodPressureCategory

object CategoryCalculator {
    fun calculate(avgSystolic: Int, avgDiastolic: Int): BloodPressureCategory {
        return when {
            avgSystolic > 180 || avgDiastolic > 120 -> BloodPressureCategory.SEVERE
            avgSystolic >= 140 || avgDiastolic >= 90 -> BloodPressureCategory.STAGE2
            avgSystolic >= 130 || avgDiastolic >= 80 -> BloodPressureCategory.STAGE1
            avgSystolic in 120..129 && avgDiastolic < 80 -> BloodPressureCategory.ELEVATED
            else -> BloodPressureCategory.NORMAL
        }
    }
}
