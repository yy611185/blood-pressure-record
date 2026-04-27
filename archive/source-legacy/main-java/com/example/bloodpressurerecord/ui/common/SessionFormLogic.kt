package com.example.bloodpressurerecord.ui.common

import com.example.bloodpressurerecord.data.repository.SessionReadingInput
import com.example.bloodpressurerecord.domain.calculator.AverageCalculator
import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.domain.calculator.HighRiskJudge
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
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
    val categoryLabel: String
)

data class SessionValidationResult(
    val readings: List<SessionReadingInput> = emptyList(),
    val error: String? = null
)

object SessionFormLogic {
    fun recomputeDerived(
        reading1: SessionReadingInputUi,
        reading2: SessionReadingInputUi,
        reading3: SessionReadingInputUi,
        showThird: Boolean
    ): SessionDerivedResult {
        val validReadings = listOf(reading1, reading2, reading3)
            .take(if (showThird) 3 else 2)
            .mapNotNull { ui ->
                val sys = ui.systolic.toIntOrNull()
                val dia = ui.diastolic.toIntOrNull()
                if (sys != null && dia != null && sys > 0 && dia > 0 && dia <= sys) {
                    ReadingValue(sys, dia, ui.pulse.toIntOrNull()?.takeIf { it > 0 })
                } else {
                    null
                }
            }
        if (validReadings.size < 2) {
            return SessionDerivedResult(
                avgSystolic = null,
                avgDiastolic = null,
                avgPulse = null,
                categoryLabel = "待计算"
            )
        }
        val avg = AverageCalculator.calculate(validReadings)
        val category = CategoryCalculator.calculate(avg.avgSystolic, avg.avgDiastolic)
        return SessionDerivedResult(
            avgSystolic = avg.avgSystolic,
            avgDiastolic = avg.avgDiastolic,
            avgPulse = avg.avgPulse,
            categoryLabel = category.toChineseLabel()
        )
    }

    fun validateAndBuildReadings(
        reading1: SessionReadingInputUi,
        reading2: SessionReadingInputUi,
        reading3: SessionReadingInputUi,
        showThird: Boolean
    ): SessionValidationResult {
        val first = parseReading(reading1, "第1组", required = true)
        if (first.error != null) return SessionValidationResult(error = first.error)
        val second = parseReading(reading2, "第2组", required = true)
        if (second.error != null) return SessionValidationResult(error = second.error)
        val list = mutableListOf(first.reading!!, second.reading!!)
        if (showThird) {
            val third = parseReading(reading3, "第3组", required = false)
            if (third.error != null) return SessionValidationResult(error = third.error)
            third.reading?.let { list += it }
        }
        if (list.size < 2) {
            return SessionValidationResult(error = "至少填写两组有效读数后才能保存。")
        }
        return SessionValidationResult(readings = list)
    }

    fun containsHighRisk(readings: List<SessionReadingInput>): Boolean {
        val byReading = readings.any { it.systolic > 180 || it.diastolic > 120 }
        if (byReading) return true
        val avg = AverageCalculator.calculate(readings.map { ReadingValue(it.systolic, it.diastolic, it.pulse) })
        return HighRiskJudge.shouldTrigger(avg.avgSystolic, avg.avgDiastolic)
    }

    fun buildAbnormalMessage(readings: List<SessionReadingInput>): String? {
        val abnormalList = readings.mapIndexedNotNull { index, reading ->
            val label = "第${index + 1}组"
            when {
                reading.systolic !in 70..260 -> "$label 收缩压 ${reading.systolic} 偏离常见范围"
                reading.diastolic !in 40..150 -> "$label 舒张压 ${reading.diastolic} 偏离常见范围"
                reading.pulse != null && reading.pulse !in 40..220 -> "$label 脉搏 ${reading.pulse} 偏离常见范围"
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
        if (!required && sysRaw.isBlank() && diaRaw.isBlank() && pulseRaw.isBlank()) {
            return ParsedReading(reading = null, error = null)
        }
        val sys = sysRaw.toIntOrNull()
        val dia = diaRaw.toIntOrNull()
        if (sys == null || dia == null || sys <= 0 || dia <= 0) {
            return ParsedReading(error = "$groupName：收缩压和舒张压必须是正整数。")
        }
        if (dia > sys) {
            return ParsedReading(error = "$groupName：舒张压不能大于收缩压。")
        }
        val pulse = if (pulseRaw.isBlank()) {
            null
        } else {
            val parsed = pulseRaw.toIntOrNull()
            if (parsed == null || parsed <= 0) {
                return ParsedReading(error = "$groupName：脉搏填写时必须是正整数。")
            }
            parsed
        }
        return ParsedReading(reading = SessionReadingInput(sys, dia, pulse))
    }

    private fun BloodPressureCategory.toChineseLabel(): String = when (this) {
        BloodPressureCategory.NORMAL -> "正常"
        BloodPressureCategory.ELEVATED -> "血压偏高（ELEVATED）"
        BloodPressureCategory.STAGE1 -> "1期偏高（STAGE1）"
        BloodPressureCategory.STAGE2 -> "2期偏高（STAGE2）"
        BloodPressureCategory.SEVERE -> "重度偏高（SEVERE）"
    }

    private data class ParsedReading(
        val reading: SessionReadingInput? = null,
        val error: String? = null
    )
}
