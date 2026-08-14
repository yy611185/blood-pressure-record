package com.example.bloodpressurerecord.domain.ocr

import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.model.ReadingValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 与 Android Bitmap 解耦的黑白图，true 表示深色前景（LCD 段码）。 */
class BinaryImage(
    val width: Int,
    val height: Int,
    private val foreground: BooleanArray
) {
    init {
        require(width > 0 && height > 0)
        require(foreground.size == width * height)
    }

    operator fun get(x: Int, y: Int): Boolean = foreground[y * width + x]

    internal fun pixels(): BooleanArray = foreground
}

data class SegmentRecognition(
    val candidate: ReadingCandidate,
    val confidence: Float
)

/**
 * 三行 LCD 的确定性 7 段识别器。
 *
 * 算法只依赖二值像素：轻量膨胀连接同一数字的段码，连通域定位数字，七个采样区
 * 做模板匹配，再按 Y 坐标组成高压/低压/脉搏三行。它只在 ML Kit 无法得到有效
 * 读数时启用，输出始终带 FROM_FALLBACK，必须经确认页核对。
 */
object SegmentDigitRecognizer {

    private val templates = mapOf(
        0 to intArrayOf(1, 1, 1, 0, 1, 1, 1),
        1 to intArrayOf(0, 0, 1, 0, 0, 1, 0),
        2 to intArrayOf(1, 0, 1, 1, 1, 0, 1),
        3 to intArrayOf(1, 0, 1, 1, 0, 1, 1),
        4 to intArrayOf(0, 1, 1, 1, 0, 1, 0),
        5 to intArrayOf(1, 1, 0, 1, 0, 1, 1),
        6 to intArrayOf(1, 1, 0, 1, 1, 1, 1),
        7 to intArrayOf(1, 0, 1, 0, 0, 1, 0),
        8 to intArrayOf(1, 1, 1, 1, 1, 1, 1),
        9 to intArrayOf(1, 1, 1, 1, 0, 1, 1)
    )

    /** 连通域填充率超过该值且宽高比正常时，视为实心噪声块而非数字笔画。 */
    private const val SOLID_FILL_REJECT = 0.75f

    fun recognize(image: BinaryImage): SegmentRecognition? {
        val baseRadius = max(1, min(image.width, image.height) / 360)
        val radii = listOf(baseRadius, baseRadius * 2, baseRadius * 3, baseRadius * 4)
            .distinct()
        return radii.mapNotNull { radius -> recognizeWithRadius(image, radius) }
            .maxByOrNull { recognitionScore(it) }
    }

    private fun recognitionScore(result: SegmentRecognition): Float {
        val pulseBonus = if (result.candidate.pulse != null) 0.18f else 0f
        return result.confidence + pulseBonus
    }

    private fun recognizeWithRadius(image: BinaryImage, radius: Int): SegmentRecognition? {
        val dilated = dilate(image, radius)
        val components = connectedComponents(dilated)
        val minDigitHeight = max(12, image.height / 22)
        val maxDigitHeight = image.height * 2 / 5

        val digits = components.mapNotNull { component ->
            val bounds = tightenToOriginal(image, component.bounds) ?: return@mapNotNull null
            if (bounds.height !in minDigitHeight..maxDigitHeight) return@mapNotNull null
            val ratio = bounds.width.toFloat() / bounds.height
            if (ratio !in 0.07f..0.95f) return@mapNotNull null
            if (bounds.pixelArea < image.width * image.height / 18000) return@mapNotNull null
            classifyDigit(image, bounds)?.let { classified ->
                DigitCandidate(bounds, classified.digit, classified.confidence)
            }
        }
        if (digits.size < 4) return null

        val rows = buildRows(digits)
        if (rows.size < 2) return null
        return chooseReading(rows)
    }

    private fun buildRows(digits: List<DigitCandidate>): List<DigitRow> {
        val clusters = mutableListOf<MutableList<DigitCandidate>>()
        digits.sortedBy { it.bounds.centerY }.forEach { digit ->
            val target = clusters
                .filter { row ->
                    val rowCenter = row.map { it.bounds.centerY }.average().toFloat()
                    val rowHeight = row.maxOf { it.bounds.height }
                    abs(digit.bounds.centerY - rowCenter) <= max(rowHeight, digit.bounds.height) * 0.38f
                }
                .minByOrNull { row ->
                    abs(digit.bounds.centerY - row.map { it.bounds.centerY }.average().toFloat())
                }
            if (target == null) clusters += mutableListOf(digit) else target += digit
        }

        return clusters.flatMap { cluster ->
            val sorted = cluster.sortedBy { it.bounds.left }
            val windows = buildList {
                if (sorted.size in 2..3) add(sorted)
                if (sorted.size > 3) {
                    for (size in 3 downTo 2) {
                        for (start in 0..sorted.size - size) {
                            add(sorted.subList(start, start + size))
                        }
                    }
                }
            }
            windows.mapNotNull(::toDigitRow)
        }
    }

    private fun toDigitRow(digits: List<DigitCandidate>): DigitRow? {
        val maxHeight = digits.maxOf { it.bounds.height }
        val minHeight = digits.minOf { it.bounds.height }
        if (minHeight < maxHeight * 0.52f) return null
        for (index in 1 until digits.size) {
            val gap = digits[index].bounds.left - digits[index - 1].bounds.right
            if (gap < -maxHeight * 0.12f || gap > maxHeight * 0.95f) return null
        }
        val value = digits.joinToString("") { it.digit.toString() }.toIntOrNull() ?: return null
        return DigitRow(
            value = value,
            centerX = digits.map { it.bounds.centerX }.average().toFloat(),
            centerY = digits.map { it.bounds.centerY }.average().toFloat(),
            height = maxHeight,
            confidence = digits.map { it.confidence }.average().toFloat()
        )
    }

    private fun chooseReading(rows: List<DigitRow>): SegmentRecognition? {
        val sorted = rows.sortedBy { it.centerY }
        var best: SegmentRecognition? = null
        var bestScore = Float.NEGATIVE_INFINITY

        for (firstIndex in 0 until sorted.lastIndex) {
            for (secondIndex in firstIndex + 1 until sorted.size) {
                val first = sorted[firstIndex]
                val second = sorted[secondIndex]
                if (!rowsAreSeparate(first, second)) continue

                val twoRow = buildRecognition(first, second, null)
                if (twoRow != null) {
                    val score = scoreRows(listOf(first, second), twoRow)
                    if (score > bestScore) {
                        best = twoRow
                        bestScore = score
                    }
                }

                for (thirdIndex in secondIndex + 1 until sorted.size) {
                    val third = sorted[thirdIndex]
                    if (!rowsAreSeparate(second, third)) continue
                    val threeRow = buildRecognition(first, second, third) ?: continue
                    val score = scoreRows(listOf(first, second, third), threeRow)
                    if (score > bestScore) {
                        best = threeRow
                        bestScore = score
                    }
                }
            }
        }
        return best
    }

    private fun rowsAreSeparate(first: DigitRow, second: DigitRow): Boolean {
        val verticalDistance = second.centerY - first.centerY
        return verticalDistance > max(first.height, second.height) * 0.45f
    }

    private fun buildRecognition(
        systolicRow: DigitRow,
        diastolicRow: DigitRow,
        pulseRow: DigitRow?
    ): SegmentRecognition? {
        val reading = ReadingValue(systolicRow.value, diastolicRow.value, pulseRow?.value)
        if (MeasurementInputRules.validateReading(reading) != null) return null

        val rows = listOfNotNull(systolicRow, diastolicRow, pulseRow)
        val maxHeight = rows.maxOf { it.height }
        if (rows.minOf { it.height } < maxHeight * 0.50f) return null
        if (rows.maxOf { it.centerX } - rows.minOf { it.centerX } > maxHeight * 1.35f) return null

        val confidence = rows.map { it.confidence }.average().toFloat()
        val flags = buildSet {
            add(ReadingFlag.FROM_FALLBACK)
            if (pulseRow == null) add(ReadingFlag.PARTIAL_STRUCTURE)
            if (confidence < 0.76f) add(ReadingFlag.AMBIGUOUS_DIGIT)
        }
        return SegmentRecognition(
            candidate = ReadingCandidate(
                systolic = reading.systolic,
                diastolic = reading.diastolic,
                pulse = reading.pulse,
                flags = flags
            ),
            confidence = confidence
        )
    }

    private fun scoreRows(rows: List<DigitRow>, result: SegmentRecognition): Float {
        val maxHeight = rows.maxOf { it.height }.toFloat()
        val sizeConsistency = rows.minOf { it.height } / maxHeight
        return rows.size * 2f + result.confidence * 2f + sizeConsistency
    }

    private fun classifyDigit(image: BinaryImage, bounds: Bounds): ClassifiedDigit? {
        val aspectRatio = bounds.width.toFloat() / bounds.height
        val fillRatio = bounds.pixelArea.toFloat() / (bounds.width * bounds.height)
        // 宽而实的连通域（图标、边框、LCD 底纹残留）不是数字笔画。
        if (aspectRatio >= 0.26f && fillRatio > SOLID_FILL_REJECT) return null

        if (aspectRatio < 0.26f) {
            // 7 段“1”由两条竖直实心窄段组成（合成测试与真实 LCD 相同，填充率≈1）；
            // 细到接近毛发级（1–2px）的竖条才是掩码噪声，直接拒绝，避免幽灵“1”行。
            val minStrokeWidth = max(3, bounds.height / 20)
            return if (bounds.width < minStrokeWidth) {
                null
            } else {
                ClassifiedDigit(1, 0.88f)
            }
        }

        val zones = arrayOf(
            Zone(0.16f, 0.00f, 0.84f, 0.24f),
            Zone(0.00f, 0.10f, 0.34f, 0.50f),
            Zone(0.66f, 0.10f, 1.00f, 0.50f),
            Zone(0.16f, 0.38f, 0.84f, 0.62f),
            Zone(0.00f, 0.50f, 0.34f, 0.90f),
            Zone(0.66f, 0.50f, 1.00f, 0.90f),
            Zone(0.16f, 0.76f, 0.84f, 1.00f)
        )
        val densities = zones.map { zoneDensity(image, bounds, it) }
        val maxDensity = densities.maxOrNull()?.coerceAtLeast(0.01f) ?: return null
        val normalized = densities.map { (it / maxDensity).coerceIn(0f, 1f) }

        val ranked = templates.map { (digit, template) ->
            val loss = template.indices.sumOf { index ->
                val expected = template[index].toFloat()
                (normalized[index] - expected).toDouble().pow(2.0)
            }.toFloat() / template.size
            digit to loss
        }.sortedBy { it.second }
        val best = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.second ?: 1f
        val confidence = (1f - best.second).coerceIn(0f, 1f)
        if (confidence < 0.56f || runnerUp - best.second < 0.015f) return null
        return ClassifiedDigit(best.first, confidence)
    }

    private fun zoneDensity(image: BinaryImage, bounds: Bounds, zone: Zone): Float {
        val left = (bounds.left + bounds.width * zone.left).toInt().coerceIn(0, image.width - 1)
        val top = (bounds.top + bounds.height * zone.top).toInt().coerceIn(0, image.height - 1)
        val right = (bounds.left + bounds.width * zone.right).toInt().coerceIn(left + 1, image.width)
        val bottom = (bounds.top + bounds.height * zone.bottom).toInt().coerceIn(top + 1, image.height)
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) if (image[x, y]) count++
        }
        return count.toFloat() / ((right - left) * (bottom - top))
    }

    private fun dilate(image: BinaryImage, radius: Int): BinaryImage {
        val width = image.width
        val height = image.height
        val horizontal = BooleanArray(width * height)
        for (y in 0 until height) {
            var active = 0
            for (candidateX in 0..min(radius, width - 1)) {
                if (image[candidateX, y]) active++
            }
            for (x in 0 until width) {
                horizontal[y * width + x] = active > 0
                val leaving = x - radius
                if (leaving >= 0 && image[leaving, y]) active--
                val entering = x + radius + 1
                if (entering < width && image[entering, y]) active++
            }
        }
        val vertical = BooleanArray(width * height)
        for (x in 0 until width) {
            var active = 0
            for (candidateY in 0..min(radius, height - 1)) {
                if (horizontal[candidateY * width + x]) active++
            }
            for (y in 0 until height) {
                vertical[y * width + x] = active > 0
                val leaving = y - radius
                if (leaving >= 0 && horizontal[leaving * width + x]) active--
                val entering = y + radius + 1
                if (entering < height && horizontal[entering * width + x]) active++
            }
        }
        return BinaryImage(width, height, vertical)
    }

    private fun connectedComponents(image: BinaryImage): List<Component> {
        val width = image.width
        val height = image.height
        val pixels = image.pixels()
        val visited = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        val result = mutableListOf<Component>()
        for (start in pixels.indices) {
            if (!pixels[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var left = width
            var top = height
            var right = 0
            var bottom = 0
            var area = 0
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                left = min(left, x)
                top = min(top, y)
                right = max(right, x + 1)
                bottom = max(bottom, y + 1)
                area++
                if (x > 0) enqueue(index - 1, pixels, visited, queue, tail).also { tail = it }
                if (x + 1 < width) enqueue(index + 1, pixels, visited, queue, tail).also { tail = it }
                if (y > 0) enqueue(index - width, pixels, visited, queue, tail).also { tail = it }
                if (y + 1 < height) enqueue(index + width, pixels, visited, queue, tail).also { tail = it }
            }
            if (area >= 4) result += Component(Bounds(left, top, right, bottom, area))
        }
        return result
    }

    private fun enqueue(
        index: Int,
        pixels: BooleanArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int
    ): Int {
        if (!pixels[index] || visited[index]) return tail
        visited[index] = true
        queue[tail] = index
        return tail + 1
    }

    private fun tightenToOriginal(image: BinaryImage, bounds: Bounds): Bounds? {
        var left = image.width
        var top = image.height
        var right = 0
        var bottom = 0
        var area = 0
        for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(image.height)) {
            for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(image.width)) {
                if (!image[x, y]) continue
                left = min(left, x)
                top = min(top, y)
                right = max(right, x + 1)
                bottom = max(bottom, y + 1)
                area++
            }
        }
        return if (area == 0) null else Bounds(left, top, right, bottom, area)
    }

    private data class Zone(val left: Float, val top: Float, val right: Float, val bottom: Float)
    private data class ClassifiedDigit(val digit: Int, val confidence: Float)
    private data class Component(val bounds: Bounds)
    private data class DigitCandidate(val bounds: Bounds, val digit: Int, val confidence: Float)
    private data class DigitRow(
        val value: Int,
        val centerX: Float,
        val centerY: Float,
        val height: Int,
        val confidence: Float
    )

    private data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val pixelArea: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }
}
