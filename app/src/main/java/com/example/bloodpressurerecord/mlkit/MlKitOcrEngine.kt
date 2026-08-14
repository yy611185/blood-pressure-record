package com.example.bloodpressurerecord.mlkit

import android.graphics.Bitmap
import android.util.Log
import com.example.bloodpressurerecord.domain.ocr.BpReadingParser
import com.example.bloodpressurerecord.domain.ocr.OcrBlock
import com.example.bloodpressurerecord.domain.ocr.OcrResult
import com.example.bloodpressurerecord.domain.ocr.OcrSource
import com.example.bloodpressurerecord.domain.ocr.ReadingCandidate
import com.example.bloodpressurerecord.domain.ocr.ReadingFlag
import com.example.bloodpressurerecord.domain.ocr.SegmentDigitRecognizer
import com.example.bloodpressurerecord.domain.ocr.SegmentRecognition
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 离线组合识别引擎：裁剪后的 LCD 画面先生成自动对比度/二值化版本，分别交给
 * bundled ML Kit；若通用 OCR 仍无有效三行结果，再启用确定性的 7 段识别器。
 */
class MlKitOcrEngine(
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
) : OcrEngine {

    companion object {
        private const val TAG = "BpOcr"
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val prepared = runCatching { ScanImagePreprocessor.prepare(bitmap) }.getOrNull()
            ?: return@withContext recognizeOne(bitmap)
        try {
            // CalibratedLcdRecognizer 的槽位/模板与真实照片存在系统性偏差（对 25 张
            // 真值照片 0/25 匹配），且它一旦匹配会直接返回读数，存在伪读数风险。
            // 在完成重标定之前禁用该通道：识别完全走 ML Kit 多路变体 + 7 段兜底。
            Log.d(TAG, "channel=calibrated disabled (uncalibrated)")

            val enhanced = prepared.ocrVariants.first()
            val remaining = prepared.ocrVariants.drop(1)
            val variants = listOf(enhanced, prepared.recognitionSource) + remaining
            val rawResults = variants.map(::recognizeOne)
            val recognized = rawResults.mapNotNull { result ->
                val candidate = BpReadingParser.parse(result) ?: return@mapNotNull null
                RecognizedVariant(result, candidate)
            }
            Log.d(
                TAG,
                "channel=mlkit variants=${rawResults.size} parsed=${recognized.size} " +
                    "candidates=${recognized.joinToString { it.candidate.key() }}"
            )
            chooseConsensus(recognized)?.let { return@withContext it }

            val fallback = prepared.segmentMasks
                .mapNotNull(SegmentDigitRecognizer::recognize)
                .filter { it.candidate.pulse != null && it.confidence >= 0.82f }
                .maxByOrNull { it.confidence + if (it.candidate.pulse != null) 0.15f else 0f }
            Log.d(TAG, "channel=segment fallback=${fallback != null}")
            fallback?.toOcrResult(
                prepared.recognitionSource.width,
                prepared.recognitionSource.height
            )
                ?: OcrResult(
                    imageWidth = prepared.recognitionSource.width,
                    imageHeight = prepared.recognitionSource.height,
                    blocks = emptyList(),
                    requiresReview = true
                ).also { Log.d(TAG, "channel=none all paths failed") }
        } finally {
            prepared.recycle()
        }
    }

    private fun recognizeOne(bitmap: Bitmap): OcrResult = runCatching {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = Tasks.await(recognizer.process(image))
        Log.d(TAG, "mlkit variant ${bitmap.width}x${bitmap.height}: \"${text.text}\"")
        text.toOcrResult(bitmap.width, bitmap.height)
    }.getOrElse {
        Log.w(TAG, "mlkit variant ${bitmap.width}x${bitmap.height} failed: ${it.message ?: it.javaClass.simpleName}")
        OcrResult(bitmap.width, bitmap.height, emptyList())
    }

    /**
     * 多变体共识：≥2 个变体给出完全一致的读数视为共识；只有一个变体解析成功时
     * 同样放行，但强制 requiresReview 交给确认页核对。解析器会为无脉搏结果打
     * PARTIAL_STRUCTURE 标记。设计取舍：宁可靠用户核对，也不把有效读数整体丢弃
     * （旧逻辑要求“≥2 个变体一致且至少一个含脉搏”，实际把大量正确结果拒之门外）。
     */
    private fun chooseConsensus(variants: List<RecognizedVariant>): OcrResult? {
        if (variants.isEmpty()) return null
        val grouped = variants.groupBy { it.candidate.key() }
        val winnerGroup = grouped.values.maxWithOrNull(
            compareBy<List<RecognizedVariant>> { it.size }
                .thenBy { group -> group.maxOf { it.candidate.qualityScore() } }
        ).orEmpty()
        if (winnerGroup.isEmpty()) return null
        val winner = winnerGroup.maxByOrNull { it.candidate.qualityScore() } ?: return null
        val hasConsensus = winnerGroup.size >= 2
        return winner.result.copy(requiresReview = !hasConsensus || grouped.size > 1)
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

    private fun SegmentRecognition.toOcrResult(width: Int, height: Int): OcrResult {
        val values = listOfNotNull(
            candidate.systolic,
            candidate.diastolic,
            candidate.pulse
        )
        val rowHeight = height * 0.18f
        val startY = height * 0.20f
        val rowStep = height * 0.25f
        val blocks = values.mapIndexed { index, value ->
            val top = startY + rowStep * index
            OcrBlock(
                text = value.toString(),
                left = width * 0.25f,
                top = top,
                right = width * 0.75f,
                bottom = top + rowHeight,
                confidence = confidence
            )
        }
        return OcrResult(
            imageWidth = width,
            imageHeight = height,
            blocks = blocks,
            source = OcrSource.SEVEN_SEGMENT,
            requiresReview = ReadingFlag.AMBIGUOUS_DIGIT in candidate.flags
        )
    }

    private fun ReadingCandidate.key(): String = "$systolic/$diastolic/${pulse ?: ""}"

    private fun ReadingCandidate.qualityScore(): Int =
        (if (pulse != null) 4 else 0) - flags.size

    private data class RecognizedVariant(
        val result: OcrResult,
        val candidate: ReadingCandidate
    )
}
