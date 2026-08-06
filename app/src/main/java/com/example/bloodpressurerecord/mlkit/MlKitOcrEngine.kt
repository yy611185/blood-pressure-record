package com.example.bloodpressurerecord.mlkit

import android.graphics.Bitmap
import com.example.bloodpressurerecord.domain.ocr.OcrBlock
import com.example.bloodpressurerecord.domain.ocr.OcrResult
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ML Kit 离线文本识别（bundled Latin 模型，无需 INTERNET 权限）。
 * 识别失败时返回空结果，由解析器/UI 兜底到“手动输入”。
 */
class MlKitOcrEngine(
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
) : OcrEngine {

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val text = Tasks.await(recognizer.process(image))
            text.toOcrResult(bitmap.width, bitmap.height)
        }.getOrElse {
            OcrResult(bitmap.width, bitmap.height, emptyList())
        }
    }

    private fun Text.toOcrResult(width: Int, height: Int): OcrResult {
        val blocks = textBlocks.flatMap { block ->
            block.lines.flatMap { line ->
                line.elements.mapNotNull { element ->
                    val box = element.boundingBox ?: return@mapNotNull null
                    OcrBlock(
                        text = element.text,
                        left = box.left.toFloat(),
                        top = box.top.toFloat(),
                        right = box.right.toFloat(),
                        bottom = box.bottom.toFloat(),
                        confidence = element.confidence
                    )
                }
            }
        }
        return OcrResult(width, height, blocks)
    }
}
