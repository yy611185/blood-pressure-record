package com.example.bloodpressurerecord.domain.ocr

import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.model.ReadingValue
import kotlin.math.max

/**
 * 行结构解析器：把 OCR 文本块转换为血压读数候选。
 *
 * 依据实测布局（维乐高 7 段 LCD，三行堆叠）：
 * 行 1 = 收缩压（最大字号）、行 2 = 舒张压（同字号）、行 3 = 脉搏（略小，心形图标旁）。
 * 外壳印刷标签不在屏内，OCR 读不到，因此不能依赖标签，只能按 Y 坐标行结构解析。
 * 纯 Kotlin，不依赖 Android / ML Kit，便于 JVM 单测（NFR7）。
 */
object BpReadingParser {

    private val TIME_PATTERN = Regex("\\d{1,2}:\\d{2}")

    /** 主数字行高度相对最大行高的最低比例（脉搏行“略小”仍应保留）。 */
    private const val MAIN_ROW_HEIGHT_RATIO = 0.6f

    /** 单个数字块高度相对最大块高度的最低比例（用于丢弃用户号等小字）。 */
    private const val SMALL_SINGLE_DIGIT_RATIO = 0.5f

    /** ML Kit 置信度阈值，低于该值标记 AMBIGUOUS_DIGIT。 */
    private const val CONFIDENCE_THRESHOLD = 0.55f

    /** 屏幕已定位时，高压行中心允许的最高位置（占 LCD 高度的比例）。 */
    private const val SYSTOLIC_ROW_MAX_CENTER_FRACTION = 0.45f

    /** 解析失败（1 行或 0 行、校验不过等）时返回 null，由调用方提示手动输入。 */
    fun parse(result: OcrResult): ReadingCandidate? {
        val candidates = result.blocks
            .mapNotNull(::cleanBlock)
            .sortedBy { it.centerY }
        if (candidates.isEmpty()) return null

        val maxHeight = candidates.maxOf { it.height }
        val main = candidates.filter { block ->
            !(block.digits.length == 1 && block.height < maxHeight * SMALL_SINGLE_DIGIT_RATIO)
        }
        if (main.isEmpty()) return null

        val rows = clusterIntoRows(main)
        if (rows.size < 2) return null

        val rowMaxHeight = rows.maxOf { it.maxHeight }
        val readingRows = rows
            .filter { it.maxHeight >= rowMaxHeight * MAIN_ROW_HEIGHT_RATIO }
            .sortedByDescending { it.maxHeight }
            .take(3)
            .sortedBy { it.centerY }
        if (readingRows.size < 2) return null

        // 两行结果的歧义保护（实测故障：只识别到“低压+脉搏”时会被错配成
        // “高压+低压”，例如 83/62 显示为高压 83、低压 62）：
        // - 屏幕已定位（lcdLocalized）时用绝对位置：高压行必须位于 LCD 上部
        //   （实测布局高压在顶部约 1/3，低压居中、脉搏在下）。
        // - 未定位时用位数：两行都 ≤2 位且上行不多于下行，多半是低压+脉搏，
        //   直接拒绝。三位数舒张压（≥100）的合法两行读数不受影响。
        if (readingRows.size == 2) {
            val first = readingRows[0]
            val second = readingRows[1]
            val mislabeled = if (result.lcdLocalized) {
                first.centerY >= result.imageHeight * SYSTOLIC_ROW_MAX_CENTER_FRACTION
            } else {
                first.digitsText.length <= 2 && first.digitsText.length <= second.digitsText.length
            }
            if (mislabeled) return null
        }

        val systolic = readingRows[0].digitsText.toIntOrNull() ?: return null
        val diastolic = readingRows[1].digitsText.toIntOrNull() ?: return null
        val pulse = readingRows.getOrNull(2)?.digitsText?.toIntOrNull()

        val reading = ReadingValue(systolic, diastolic, pulse)
        if (MeasurementInputRules.validateReading(reading) != null) return null

        val selectedBlocks = readingRows.flatMap { it.blocks }
        val flags = buildSet {
            if (pulse == null) {
                add(ReadingFlag.PARTIAL_STRUCTURE)
            }
            if (nearBoundary(systolic, MeasurementInputRules.SYSTOLIC_RANGE) ||
                nearBoundary(diastolic, MeasurementInputRules.DIASTOLIC_RANGE) ||
                (pulse != null && nearBoundary(pulse, MeasurementInputRules.PULSE_RANGE))
            ) {
                add(ReadingFlag.BOUNDARY)
            }
            val lowConfidence = selectedBlocks.any {
                it.block.confidence != null && it.block.confidence < CONFIDENCE_THRESHOLD
            }
            if (lowConfidence) {
                add(ReadingFlag.LOW_CONFIDENCE)
            }
            if (lowConfidence || result.requiresReview || selectedBlocks.any { it.hadConfusableGlyph } ||
                readingRows.any { it.blocks.size > 1 }
            ) {
                add(ReadingFlag.AMBIGUOUS_DIGIT)
            }
            if (result.source == OcrSource.SEVEN_SEGMENT) {
                add(ReadingFlag.FROM_FALLBACK)
            }
        }
        return ReadingCandidate(
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse,
            flags = flags
        )
    }

    /** 待机屏常见特征：时间 + 用户号/单个 0，没有两行有效血压读数。 */
    fun looksLikeStandby(result: OcrResult): Boolean {
        val texts = result.blocks.map { it.text.trim() }.filter { it.isNotEmpty() }
        val hasTime = texts.any { TIME_PATTERN.containsMatchIn(it) }
        val numericValues = texts.filterNot { TIME_PATTERN.containsMatchIn(it) }.mapNotNull { text ->
            text.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
        val hasValidReadings = numericValues.count { it in 50..220 } >= 2
        if (hasValidReadings) return false
        return numericValues.size <= 2 && 0 in numericValues && (hasTime || numericValues.size == 1)
    }

    /** 基本过滤 + 剥离非数字字符（如 “❤69” → “69”）。 */
    private fun cleanBlock(block: OcrBlock): ParsedBlock? {
        val text = block.text.trim()
        if (text.isEmpty()) return null
        if (TIME_PATTERN.containsMatchIn(text)) return null
        val compact = text.filterNot { it.isWhitespace() }
        val confusableCharacters = "OoQqIiLl|SsBb"
        val mayNormalizeGlyphs = text.any { it.isDigit() } ||
            (compact.length <= 3 && compact.all { it in confusableCharacters })
        var hadConfusableGlyph = false
        val digits = buildString {
            text.forEach { character ->
                when {
                    character.isDigit() -> append(character)
                    mayNormalizeGlyphs && character in "OoQq" -> {
                        append('0')
                        hadConfusableGlyph = true
                    }
                    mayNormalizeGlyphs && character in "IiLl|" -> {
                        append('1')
                        hadConfusableGlyph = true
                    }
                    mayNormalizeGlyphs && character in "Ss" -> {
                        append('5')
                        hadConfusableGlyph = true
                    }
                    mayNormalizeGlyphs && character in "Bb" -> {
                        append('8')
                        hadConfusableGlyph = true
                    }
                }
            }
        }
        if (digits.isEmpty()) return null
        return ParsedBlock(block = block.copy(text = digits), hadConfusableGlyph = hadConfusableGlyph)
    }

    /** 按 Y 坐标聚类成行：同一行内块的中心 Y 差不超过容差。 */
    private fun clusterIntoRows(blocks: List<ParsedBlock>): List<TextRow> {
        val rows = mutableListOf<MutableRow>()
        for (block in blocks) {
            val last = rows.lastOrNull()
            if (last != null && block.centerY - last.blocks.last().centerY <= last.tolerance()) {
                last.blocks += block
            } else {
                rows += MutableRow(mutableListOf(block))
            }
        }
        return rows.map { it.toRow() }
    }

    private class MutableRow(val blocks: MutableList<ParsedBlock>) {
        fun tolerance(): Float {
            val maxHeight = blocks.maxOf { it.height }
            return max(4f, maxHeight * 0.45f)
        }

        fun toRow(): TextRow = TextRow(blocks)
    }

    private data class TextRow(val blocks: List<ParsedBlock>) {
        val centerY: Float get() = blocks.map { it.centerY }.average().toFloat()
        val maxHeight: Float get() = blocks.maxOf { it.height }
        val digitsText: String
            get() = blocks.sortedBy { it.left }.joinToString("") { it.digits }
    }

    private data class ParsedBlock(
        val block: OcrBlock,
        val hadConfusableGlyph: Boolean
    ) {
        val digits: String get() = block.text
        val left: Float get() = block.left
        val height: Float get() = block.height
        val centerY: Float get() = block.centerY
    }

    private fun nearBoundary(value: Int, range: IntRange): Boolean =
        value <= range.first + 10 || value >= range.last - 10
}
