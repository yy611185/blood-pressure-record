package com.example.bloodpressurerecord.domain.ocr

/**
 * OCR 文本块（纯 Kotlin，不依赖 Android / ML Kit，便于 JVM 单测）。
 * 坐标均为图片像素坐标。
 */
data class OcrBlock(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float? = null
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** 识别结果来源，用于确认页明确标记专用段码兜底结果。 */
enum class OcrSource {
    ML_KIT,
    SEVEN_SEGMENT
}

/** 一次 OCR 的完整结果。 */
data class OcrResult(
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<OcrBlock>,
    val source: OcrSource = OcrSource.ML_KIT,
    /** 多个预处理版本给出冲突结果时，强制要求人工核对。 */
    val requiresReview: Boolean = false,
    /** 预处理是否成功定位了血压计屏幕（黄色边框）。为 true 时坐标是 LCD 内坐标。 */
    val lcdLocalized: Boolean = false
)
