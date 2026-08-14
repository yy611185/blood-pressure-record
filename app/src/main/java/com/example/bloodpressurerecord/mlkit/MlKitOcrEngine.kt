package com.example.bloodpressurerecord.mlkit

import android.graphics.Bitmap
import com.example.bloodpressurerecord.domain.ocr.CalibratedLcdRecognizer
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

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val prepared = runCatching { ScanImagePreprocessor.prepare(bitmap) }.getOrNull()
            ?: return@withContext recognizeOne(bitmap)
        try {
            val calibrated = prepared.segmentMasks.asReversed()
                .mapNotNull(CalibratedLcdRecognizer::recognize)
                .maxByOrNull { it.confidence }
            if (calibrated != null) {
                return@withContext calibrated.toOcrResult(
                    prepared.recognitionSource.width,
                    prepared.recognitionSource.height
                )
            }

            val enhanced = prepared.ocrVariants.first()
            val remaining = prepared.ocrVariants.drop(1)
            val variants = listOf(enhanced, prepared.recognitionSource) + remaining
            val rawResults = variants.map(::recognizeOne)
            val recognized = rawResults.mapNotNull { result ->
                val candidate = BpReadingParser.parse(result) ?: return@mapNotNull null
                RecognizedVariant(result, candidate)
            }
            chooseConsensus(recognized)?.let { return@withContext it }

            val fallback = prepared.segmentMasks
                .mapNotNull(SegmentDigitRecognizer::recognize)
                .filter { it.candidate.pulse != null && it.confidence >= 0.82f }
                .maxByOrNull { it.confidence + if (it.candidate.pulse != null) 0.15f else 0f }
            fallback?.toOcrResult(
                prepared.recognitionSource.width,
                prepared.recognitionSource.height
            )
                ?: OcrResult(
                    imageWidth = prepared.recognitionSource.width,
                    imageHeight = prepared.recognitionSource.height,
                    blocks = emptyList(),
                    requiresReview = true
                )
        } finally {
            prepared.recycle()
        }
    }

    private fun recognizeOne(bitmap: Bitmap): OcrResult = runCatching {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = Tasks.await(recognizer.process(image))
        text.toOcrResult(bitmap.width, bitmap.height)
    }.getOrElse {
        OcrResult(bitmap.width, bitmap.height, emptyList())
    }

    private fun chooseConsensus(variants: List<RecognizedVariant>): OcrResult? {
        if (variants.isEmpty()) return null
        val grouped = variants.groupBy { it.candidate.key() }
        val winnerGroup = grouped.values.maxWithOrNull(
            compareBy<List<RecognizedVariant>> { it.size }
                .thenBy { group -> group.maxOf { it.candidate.qualityScore() } }
        ).orEmpty()
        if (winnerGroup.size < 2 || winnerGroup.none { it.candidate.pulse != null }) return null
        val winner = winnerGroup.maxByOrNull { it.candidate.qualityScore() } ?: return null
        return winner.result.copy(requiresReview = grouped.size > 1)
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
