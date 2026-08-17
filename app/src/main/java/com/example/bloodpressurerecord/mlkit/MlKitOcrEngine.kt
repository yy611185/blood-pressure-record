package com.example.bloodpressurerecord.mlkit

import android.graphics.Bitmap
import android.util.Log
import com.example.bloodpressurerecord.BuildConfig
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
 * 离线组合识别引擎：
 * 1. 使用经过对比度增强、自适应二值化与形态学闭运算桥接后的图像；
 * 2. 优先运行高精度 7 段拓扑状态机识别器；
 * 3. 结合 ML Kit Latin 离线模型多变体共识进行综合判决。
 */
class MlKitOcrEngine(
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
) : OcrEngine {

    companion object {
        private const val TAG = "BpOcr"

        /** 7 段拓扑通道达到该置信度免强制人工核对标志。 */
        private const val AUTO_ACCEPT_CONFIDENCE = 0.85f

        private inline fun debugLog(message: () -> String) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, message())
            }
        }
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val prepared = runCatching { ScanImagePreprocessor.prepare(bitmap) }.getOrNull()
            ?: return@withContext recognizeOne(bitmap)
        try {
            // 通道 1：确定性 7 段拓扑状态机识别
            val segmentBest = prepared.segmentMasks
                .mapNotNull(SegmentDigitRecognizer::recognize)
                .maxByOrNull { it.confidence + if (it.candidate.pulse != null) 0.20f else 0f }

            if (segmentBest != null && segmentBest.confidence >= 0.72f) {
                debugLog {
                    "channel=segment sys=${segmentBest.candidate.systolic} " +
                        "dia=${segmentBest.candidate.diastolic} pulse=${segmentBest.candidate.pulse} " +
                        "conf=${segmentBest.confidence}"
                }
                return@withContext segmentBest.toOcrResult(
                    prepared.recognitionSource.width,
                    prepared.recognitionSource.height
                ).copy(
                    lcdLocalized = prepared.screenLocated,
                    requiresReview = segmentBest.confidence < AUTO_ACCEPT_CONFIDENCE
                )
            }

            // 通道 2：ML Kit 通用 OCR 识别（已桥接段码间隙）
            val rawResults = prepared.ocrVariants.map(::recognizeOne)
            val recognized = rawResults.mapNotNull { result ->
                val candidate = BpReadingParser.parse(result) ?: return@mapNotNull null
                RecognizedVariant(result, candidate)
            }
            debugLog {
                "channel=mlkit variants=${rawResults.size} parsed=${recognized.size} " +
                    "candidates=${recognized.joinToString { it.candidate.key() }}"
            }
            chooseConsensus(recognized)?.let { consensus ->
                return@withContext consensus.copy(lcdLocalized = prepared.screenLocated)
            }

            // 通道 3：兜底输出 7 段拓扑候选（哪怕置信度略低）
            if (segmentBest != null) {
                return@withContext segmentBest.toOcrResult(
                    prepared.recognitionSource.width,
                    prepared.recognitionSource.height
                ).copy(
                    lcdLocalized = prepared.screenLocated,
                    requiresReview = true
                )
            }

            OcrResult(
                imageWidth = prepared.recognitionSource.width,
                imageHeight = prepared.recognitionSource.height,
                blocks = emptyList(),
                requiresReview = true,
                lcdLocalized = prepared.screenLocated
            ).also { debugLog { "channel=none all paths failed" } }
        } finally {
            prepared.recycle()
        }
    }

    private fun recognizeOne(bitmap: Bitmap): OcrResult = runCatching {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = Tasks.await(recognizer.process(image))
        toOcrResult(bitmap.width, bitmap.height, text)
    }.getOrElse { throwable ->
        debugLog { "ML Kit recognizeOne failed: ${throwable.message}" }
        OcrResult(bitmap.width, bitmap.height, emptyList())
    }

    private fun chooseConsensus(variants: List<RecognizedVariant>): OcrResult? {
        if (variants.isEmpty()) return null
        val grouped = variants.groupBy { it.candidate.key() }
        val winningEntry = grouped.maxByOrNull { (key, list) ->
            val countScore = list.size * 10
            val bestCandidate = list.maxByOrNull { it.candidate.qualityScore() }?.candidate
            val qualityScore = bestCandidate?.qualityScore() ?: 0
            countScore + qualityScore
        } ?: return null

        val winningList = winningEntry.value
        val hasDisagreement = grouped.size > 1
        val bestVariant = winningList.maxByOrNull { it.candidate.qualityScore() } ?: winningList.first()

        val flags = buildSet {
            addAll(bestVariant.candidate.flags)
            if (hasDisagreement) {
                add(ReadingFlag.AMBIGUOUS_DIGIT)
            }
        }
        return bestVariant.result.copy(
            requiresReview = hasDisagreement || ReadingFlag.AMBIGUOUS_DIGIT in flags
        )
    }

    private fun toOcrResult(width: Int, height: Int, text: Text): OcrResult {
        val blocks = text.textBlocks.flatMap { textBlock ->
            textBlock.lines.flatMap { line ->
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
        val rowHeight = height * 0.20f
        val startY = height * 0.18f
        val rowStep = height * 0.26f
        val blocks = values.mapIndexed { index, value ->
            val top = startY + rowStep * index
            OcrBlock(
                text = value.toString(),
                left = width * 0.20f,
                top = top,
                right = width * 0.80f,
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
