package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.AverageResult
import com.example.bloodpressurerecord.domain.model.ReadingValue
import kotlin.math.roundToInt

object AverageCalculator {
    fun calculate(readings: List<ReadingValue>): AverageResult {
        require(readings.isNotEmpty()) { "readings 不能为空" }

        val avgSystolic = readings.map { it.systolic }.average().roundToInt()
        val avgDiastolic = readings.map { it.diastolic }.average().roundToInt()

        val pulseValues = readings.mapNotNull { it.pulse }
        val avgPulse = if (pulseValues.isEmpty()) {
            null
        } else {
            pulseValues.average().roundToInt()
        }

        return AverageResult(
            avgSystolic = avgSystolic,
            avgDiastolic = avgDiastolic,
            avgPulse = avgPulse
        )
    }
}
