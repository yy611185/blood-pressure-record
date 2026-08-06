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

/** 一次 OCR 的完整结果。 */
data class OcrResult(
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<OcrBlock>
)
