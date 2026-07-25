package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.BloodPressureCategory

object CategoryCalculator {
    fun calculate(avgSystolic: Int, avgDiastolic: Int): BloodPressureCategory {
        return BloodPressureRules.category(avgSystolic, avgDiastolic)
    }
}
