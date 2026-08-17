package com.example.bloodpressurerecord.domain.ocr

/**
 * 屏幕专用数码管校准识别器入口。
 * 现已统一委托给具备自适应斜角补偿和 7 笔画拓扑状态机的高精度 [SegmentDigitRecognizer]。
 */
object CalibratedLcdRecognizer {

    fun recognize(image: BinaryImage): SegmentRecognition? {
        return SegmentDigitRecognizer.recognize(image)
    }
}
