package com.example.bloodpressurerecord.ui.common

import com.example.bloodpressurerecord.data.repository.SessionReadingInput
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.calculator.MeasurementDerivation
import com.example.bloodpressurerecord.domain.calculator.BloodPressureRules
import com.example.bloodpressurerecord.domain.calculator.ReadingValidationError
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.model.ReadingValue

data class SessionReadingInputUi(
    val systolic: String = "",
    val diastolic: String = "",
    val pulse: String = "",
    // Transient identity for extra form rows. Drafts and Room only store the readings.
    val editorId: Long = 0
)

data class RemovedExtraReading(val index: Int, val reading: SessionReadingInputUi)

enum class RestoreExtraReadingResult { RESTORED, AT_LIMIT, BUSY }

data class SessionDerivedResult(
    val avgSystolic: Int?,
    val avgDiastolic: Int?,
    val avgPulse: Int?,
    val categoryLabel: String,
    val containsHighRiskReading: Boolean = false,
    val averagedGroupCount: Int = 0
)

data class SessionValidationResult(
    val readings: List<SessionReadingInput> = emptyList(),
    val containsHighRiskReading: Boolean = false,
    val error: String? = null
)

object SessionFormLogic {
    /** UI 与存储使用同一个上限，旧记录的全部组数始终可编辑。 */
    const val UI_MAX_READING_COUNT = MeasurementInputRules.MAX_READING_COUNT

    /** 生理上限对应的两位数跳格前缀：高压 ≤300 -> 30；低压 ≤200 -> 20；脉搏 ≤250 -> 25。 */
    const val SYSTOLIC_AUTO_ADVANCE_PREFIX = 30
    const val DIASTOLIC_AUTO_ADVANCE_PREFIX = 20
    const val PULSE_AUTO_ADVANCE_PREFIX = 25

    /**
     * 仅在光标折叠于末尾、无输入法组合态（composition）、且单次正向尾部追加 1 位数字后
     * 不可能再合法续输下一位时自动推进焦点；退格删除、选中替换、多字符粘贴与中间插入绝不误跳。
     */
    fun shouldAutoAdvanceField(
        previousText: String,
        previousSelectionStart: Int,
        previousSelectionEnd: Int,
        nextText: String,
        nextSelectionStart: Int,
        nextSelectionEnd: Int,
        maxTwoDigitPrefix: Int,
        hasComposition: Boolean = false
    ): Boolean {
        if (hasComposition) return false
        if (previousSelectionStart != previousSelectionEnd || previousSelectionEnd != previousText.length) {
            return false
        }
        if (nextSelectionStart != nextSelectionEnd || nextSelectionEnd != nextText.length) {
            return false
        }
        if (nextText.length != previousText.length + 1 || !nextText.startsWith(previousText)) {
            return false
        }
        if (!nextText.last().isDigit()) return false
        val value = nextText.toIntOrNull() ?: return false
        return nextText.length == 3 || (nextText.length == 2 && value > maxTwoDigitPrefix)
    }

    fun shouldAutoAdvanceField(
        previous: String,
        next: String,
        maxTwoDigitPrefix: Int
    ): Boolean = shouldAutoAdvanceField(
        previousText = previous,
        previousSelectionStart = previous.length,
        previousSelectionEnd = previous.length,
        nextText = next,
        nextSelectionStart = next.length,
        nextSelectionEnd = next.length,
        maxTwoDigitPrefix = maxTwoDigitPrefix
    )

    /**
     * 切换常用词胶囊：
     * - 仅匹配边界独立的完整标签词，不误伤包含该子串的自由文本；
     * - 新增或移除标签时原样保留用户已输入的空格、逗号与换行，绝不改写无关分隔符。
     */
    fun toggleQuickNoteTag(current: String, tag: String): String {
        val normalizedTag = tag.trim()
        if (normalizedTag.isEmpty()) return current
        val existingMatch = findStandaloneQuickTag(current, normalizedTag)
        if (existingMatch != null) {
            var result = current
            var match = existingMatch
            while (match != null) {
                result = removeStandaloneQuickTagAt(result, match.first, match.second)
                match = findStandaloneQuickTag(result, normalizedTag)
            }
            return result
        }
        val suffix = if (current.isEmpty() ||
            current.last().let { it.isQuickTagSeparator() && it != '、' }
        ) normalizedTag else "、$normalizedTag"
        return current + suffix
    }

    fun containsQuickNoteTag(current: String, tag: String): Boolean {
        val normalizedTag = tag.trim()
        if (normalizedTag.isEmpty()) return false
        return findStandaloneQuickTag(current, normalizedTag) != null
    }

    private fun Char.isQuickTagDelimiterChar(): Boolean =
        this == '、' || this == '，' || this == ',' || this == ';' || this == '；'

    private fun Char.isQuickTagSeparator(): Boolean =
        isQuickTagDelimiterChar() || isWhitespace()

    private fun findStandaloneQuickTag(text: String, tag: String): Pair<Int, Int>? {
        var searchFrom = 0
        while (searchFrom <= text.length - tag.length) {
            val index = text.indexOf(tag, startIndex = searchFrom)
            if (index < 0) return null
            val end = index + tag.length
            val leftBoundary = index == 0 || text[index - 1].isQuickTagSeparator()
            val rightBoundary = end == text.length || text[end].isQuickTagSeparator()
            if (leftBoundary && rightBoundary) {
                return index to end
            }
            searchFrom = index + 1
        }
        return null
    }

    private fun removeStandaloneQuickTagAt(text: String, start: Int, end: Int): String {
        // Remove the adjacent list join inserted by this control. User whitespace,
        // line breaks and punctuation outside the tag are kept verbatim.
        return when {
            start > 0 && text[start - 1] == '、' -> text.removeRange(start - 1, end)
            end < text.length && text[end] == '、' -> text.removeRange(start, end + 1)
            else -> text.removeRange(start, end)
        }
    }

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
            return SessionDerivedResult(
                null, null, null, "待计算",
                containsHighRiskReading = validReadings.any {
                    BloodPressureRules.isHighRisk(it.systolic, it.diastolic)
                }
            )
        }
        val derived = MeasurementDerivation.derive(validReadings, strategy)
        return SessionDerivedResult(
            avgSystolic = derived.average.avgSystolic,
            avgDiastolic = derived.average.avgDiastolic,
            avgPulse = derived.average.avgPulse,
            categoryLabel = CategoryPresentation.label(derived.category),
            containsHighRiskReading = derived.containsHighRiskReading,
            averagedGroupCount = validReadings.size
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
            if (required) return ParsedReading(error = "请把${groupName}的高压和低压填完整")
            return ParsedReading(reading = null, error = null)
        }

        val sys = sysRaw.toIntOrNull()
        val dia = diaRaw.toIntOrNull()
        if (sys == null || dia == null) {
            return ParsedReading(error = "请把${groupName}的高压和低压填完整")
        }

        val pulse = if (pulseRaw.isBlank()) {
            null
        } else {
            val parsed = pulseRaw.toIntOrNull()
            if (parsed == null) {
                return ParsedReading(error = "${groupName}脉搏请填写整数")
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
            "高压须在 ${MeasurementInputRules.SYSTOLIC_RANGE.first}–${MeasurementInputRules.SYSTOLIC_RANGE.last} 之间。"
        ReadingValidationError.DIASTOLIC_OUT_OF_RANGE ->
            "低压须在 ${MeasurementInputRules.DIASTOLIC_RANGE.first}–${MeasurementInputRules.DIASTOLIC_RANGE.last} 之间。"
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
