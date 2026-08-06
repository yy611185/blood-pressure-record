package com.example.bloodpressurerecord.domain.ocr

/** 字段级“可疑”标记。 */
enum class ReadingFlag {
    /** OCR 置信度低，或同一行出现多个数字组（存在 8/3、5/6 等易混淆风险）。 */
    AMBIGUOUS_DIGIT,
    /** 数值接近校验边界 ±10。 */
    BOUNDARY,
    /** 只解析出 2 行（无脉搏）或行结构异常。 */
    PARTIAL_STRUCTURE,
    /** 结果来自 7 段数码管兜底通道（P1 预留）。 */
    FROM_FALLBACK
}

/** 结构化读数候选：行 1=收缩压、行 2=舒张压、行 3（可选）=脉搏。 */
data class ReadingCandidate(
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val flags: Set<ReadingFlag> = emptySet()
)
