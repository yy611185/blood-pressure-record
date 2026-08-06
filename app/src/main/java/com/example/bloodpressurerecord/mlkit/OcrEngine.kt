package com.example.bloodpressurerecord.mlkit

import android.graphics.Bitmap
import com.example.bloodpressurerecord.domain.ocr.OcrResult

/** OCR 引擎抽象：P0 使用 ML Kit，P1 可换成 7 段专用识别或组合通道。 */
interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): OcrResult
}
