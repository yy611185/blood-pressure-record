package com.example.bloodpressurerecord.data.model

object BloodPressureLevelCalculator {
    fun calculate(systolic: Int?, diastolic: Int?): BloodPressureLevel {
        if (systolic == null || diastolic == null) return BloodPressureLevel.UNKNOWN

        return when {
            systolic < 90 || diastolic < 60 -> BloodPressureLevel.LOW
            systolic >= 180 || diastolic >= 110 -> BloodPressureLevel.GRADE_3
            systolic >= 160 || diastolic >= 100 -> BloodPressureLevel.GRADE_2
            systolic >= 140 || diastolic >= 90 -> BloodPressureLevel.GRADE_1
            systolic >= 130 || diastolic >= 85 -> BloodPressureLevel.NORMAL_HIGH
            else -> BloodPressureLevel.IDEAL
        }
    }
}
