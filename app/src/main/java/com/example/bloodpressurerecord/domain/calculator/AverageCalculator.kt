package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.AverageResult
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.model.ReadingValue
import kotlin.math.roundToInt

object AverageCalculator {
    fun calculate(
        readings: List<ReadingValue>,
        strategy: AverageStrategy = AverageStrategy.ALL
    ): AverageResult {
        require(readings.isNotEmpty()) { "readings 不能为空" }

        val effective = if (strategy == AverageStrategy.DISCARD_FIRST && readings.size >= 2) {
            readings.drop(1)
        } else {
            readings
        }

        val avgSystolic = effective.map { it.systolic }.average().roundToInt()
        val avgDiastolic = effective.map { it.diastolic }.average().roundToInt()

        val pulseValues = effective.mapNotNull { it.pulse }
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
