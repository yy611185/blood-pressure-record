package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.ReadingValue

/**
 * 正常表单、Repository 与备份导入共用的原始读数完整性规则。
 */
object MeasurementInputRules {
    const val MIN_READING_COUNT = 2
    const val MAX_READING_COUNT = 20

    val SYSTOLIC_RANGE = 40..300
    val DIASTOLIC_RANGE = 20..200
    val PULSE_RANGE = 20..250

    fun validateReading(reading: ReadingValue): ReadingValidationError? {
        return when {
            reading.systolic !in SYSTOLIC_RANGE ->
                ReadingValidationError.SYSTOLIC_OUT_OF_RANGE
            reading.diastolic !in DIASTOLIC_RANGE ->
                ReadingValidationError.DIASTOLIC_OUT_OF_RANGE
            reading.diastolic >= reading.systolic ->
                ReadingValidationError.DIASTOLIC_NOT_LOWER_THAN_SYSTOLIC
            reading.pulse != null && reading.pulse !in PULSE_RANGE ->
                ReadingValidationError.PULSE_OUT_OF_RANGE
            else -> null
        }
    }

    fun validateReadings(readings: List<ReadingValue>): ReadingSetValidationError? {
        return when {
            readings.size < MIN_READING_COUNT -> ReadingSetValidationError.TOO_FEW_READINGS
            readings.size > MAX_READING_COUNT -> ReadingSetValidationError.TOO_MANY_READINGS
            else -> null
        }
    }
}

enum class ReadingValidationError {
    SYSTOLIC_OUT_OF_RANGE,
    DIASTOLIC_OUT_OF_RANGE,
    DIASTOLIC_NOT_LOWER_THAN_SYSTOLIC,
    PULSE_OUT_OF_RANGE
}

enum class ReadingSetValidationError {
    TOO_FEW_READINGS,
    TOO_MANY_READINGS
}
