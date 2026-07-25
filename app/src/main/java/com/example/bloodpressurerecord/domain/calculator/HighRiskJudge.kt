package com.example.bloodpressurerecord.domain.calculator

object HighRiskJudge {
    fun shouldTrigger(avgSystolic: Int, avgDiastolic: Int): Boolean {
        return BloodPressureRules.isHighRisk(avgSystolic, avgDiastolic)
    }
}
