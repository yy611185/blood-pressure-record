package com.example.bloodpressurerecord.ui.common

import com.example.bloodpressurerecord.data.repository.SessionReadingInput
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.calculator.MeasurementDerivation
import com.example.bloodpressurerecord.domain.calculator.ReadingValidationError
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.model.ReadingValue

data class SessionReadingInputUi(
    val systolic: String = "",
    val diastolic: String = "",
    val pulse: String = ""
)

data class SessionDerivedResult(
    val avgSystolic: Int?,
    val avgDiastolic: Int?,
    val avgPulse: Int?,
    val categoryLabel: String,
    val containsHighRiskReading: Boolean = false
)

data class SessionValidationResult(
    val readings: List<SessionReadingInput> = emptyList(),
    val containsHighRiskReading: Boolean = false,
    val error: String? = null
)

object SessionFormLogic {
    /**
     * 表单允许的最大读数组数（交互体验上限）。
     * 必须 ≤ [MeasurementInputRules.MAX_READING_COUNT]（存储与导入的硬上限），
     * 旧备份中超过本值、不超过硬上限的记录仍可正常导入与展示。
     */
    const val UI_MAX_READING_COUNT = 10

    fun saveDisabledReason(
        readings: List<SessionReadingInputUi>,
        requiredCount: Int = 2,
        maximumCount: Int = UI_MAX_READING_COUNT
    ): String? {
        return validateAndBuildReadings(readings, requiredCount, maximumCount = maximumCount).error
    }

    fun recomputeDerived(
        readings: List<SessionReadingInputUi>,
        requiredCount: Int = 2,
        strategy: AverageStrategy = AverageStrategy.ALL
    ): SessionDerivedResult {
        val validReadings = readings.mapNotNull { ui ->
            val sys = ui.systolic.toIntOrNull()
            val dia = ui.diastolic.toIntOrNull()
            val value = if (sys != null && dia != null) {
                ReadingValue(sys, dia, ui.pulse.toIntOrNull())
            } else {
                null
            }
            value?.takeIf { MeasurementInputRules.validateReading(it) == null }
        }
        if (validReadings.size < requiredCount) {
            return SessionDerivedResult(null, null, null, "待计算")
        }
        val derived = MeasurementDerivation.derive(validReadings, strategy)
        return SessionDerivedResult(
            avgSystolic = derived.average.avgSystolic,
            avgDiastolic = derived.average.avgDiastolic,
            avgPulse = derived.average.avgPulse,
            categoryLabel = CategoryPresentation.label(derived.category),
            containsHighRiskReading = derived.containsHighRiskReading
        )
    }

    fun validateAndBuildReadings(
        readings: List<SessionReadingInputUi>,
        requiredCount: Int = 2,
        strategy: AverageStrategy = AverageStrategy.ALL,
        maximumCount: Int = UI_MAX_READING_COUNT
    ): SessionValidationResult {
        val list = mutableListOf<SessionReadingInput>()
        readings.forEachIndexed { index, reading ->
            val groupLabel = "第${index + 1}组"
            val required = index < requiredCount
            val parsed = parseReading(reading, groupLabel, required)
            if (parsed.error != null) return SessionValidationResult(error = parsed.error)
            parsed.reading?.let { list += it }
        }
        if (list.size < requiredCount) {
            return SessionValidationResult(
                error = "至少填写 $requiredCount 组高压和低压，就可以保存啦"
            )
        }
        if (list.size > maximumCount) {
            return SessionValidationResult(
                error = "每次测量最多保留 $maximumCount 组读数。"
            )
        }
        val values = list.map { ReadingValue(it.systolic, it.diastolic, it.pulse) }
        return SessionValidationResult(
            readings = list,
            containsHighRiskReading = MeasurementDerivation.derive(values, strategy).containsHighRiskReading
        )
    }

    fun buildAbnormalMessage(readings: List<SessionReadingInput>): String? {
        val abnormalList = readings.mapIndexedNotNull { index, reading ->
            val label = "第${index + 1}组"
            when {
                reading.systolic !in 70..260 -> "${label}收缩压 ${reading.systolic} 偏离常见范围"
                reading.diastolic !in 40..150 -> "${label}舒张压 ${reading.diastolic} 偏离常见范围"
                reading.pulse != null && reading.pulse !in 40..220 -> "${label}脉搏 ${reading.pulse} 偏离常见范围"
                else -> null
            }
        }
        if (abnormalList.isEmpty()) return null
        return abnormalList.joinToString(separator = "；", postfix = "。请确认是否继续保存？")
    }

    private fun parseReading(
        reading: SessionReadingInputUi,
        groupName: String,
        required: Boolean
    ): ParsedReading {
        val sysRaw = reading.systolic.trim()
        val diaRaw = reading.diastolic.trim()
        val pulseRaw = reading.pulse.trim()

        val allBlank = sysRaw.isBlank() && diaRaw.isBlank() && pulseRaw.isBlank()
        if (allBlank) {
            if (required) return ParsedReading(error = "$groupName：请填写收缩压和舒张压。")
            return ParsedReading(reading = null, error = null)
        }

        val sys = sysRaw.toIntOrNull()
        val dia = diaRaw.toIntOrNull()
        if (sys == null || dia == null) {
            return ParsedReading(error = "$groupName：收缩压和舒张压必须为整数。")
        }

        val pulse = if (pulseRaw.isBlank()) {
            null
        } else {
            val parsed = pulseRaw.toIntOrNull()
            if (parsed == null) {
                return ParsedReading(error = "$groupName：脉搏填写时必须为整数。")
            }
            parsed
        }

        val value = ReadingValue(sys, dia, pulse)
        val validationError = MeasurementInputRules.validateReading(value)
        if (validationError != null) {
            return ParsedReading(error = "$groupName：${validationError.toUserMessage()}")
        }
        return ParsedReading(reading = SessionReadingInput(sys, dia, pulse))
    }

    private fun ReadingValidationError.toUserMessage(): String = when (this) {
        ReadingValidationError.SYSTOLIC_OUT_OF_RANGE ->
            "收缩压须在 ${MeasurementInputRules.SYSTOLIC_RANGE.first}–${MeasurementInputRules.SYSTOLIC_RANGE.last} 之间。"
        ReadingValidationError.DIASTOLIC_OUT_OF_RANGE ->
            "舒张压须在 ${MeasurementInputRules.DIASTOLIC_RANGE.first}–${MeasurementInputRules.DIASTOLIC_RANGE.last} 之间。"
        ReadingValidationError.DIASTOLIC_NOT_LOWER_THAN_SYSTOLIC ->
            "低压要小于高压，检查一下再保存。"
        ReadingValidationError.PULSE_OUT_OF_RANGE ->
            "脉搏须在 ${MeasurementInputRules.PULSE_RANGE.first}–${MeasurementInputRules.PULSE_RANGE.last} 之间。"
    }

    private data class ParsedReading(
        val reading: SessionReadingInput? = null,
        val error: String? = null
    )
}
