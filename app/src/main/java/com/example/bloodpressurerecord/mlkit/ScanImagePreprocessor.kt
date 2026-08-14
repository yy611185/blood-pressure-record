package com.example.bloodpressurerecord.mlkit

import android.graphics.Bitmap
import com.example.bloodpressurerecord.domain.ocr.BinaryImage
import kotlin.math.max
import kotlin.math.min

internal data class PreparedScanImages(
    /** 屏幕定位后的原始彩色图；没有可靠定位时就是调用方传入的 source。 */
    val recognitionSource: Bitmap,
    private val ownsRecognitionSource: Boolean,
    /** 按推荐顺序送入 ML Kit；这些 Bitmap 均由预处理器创建，使用后需 recycle。 */
    val ocrVariants: List<Bitmap>,
    /** 全局阈值和局部自适应阈值，供 7 段专用识别器兜底。 */
    val segmentMasks: List<BinaryImage>
) {
    fun recycle() {
        ocrVariants.forEach { if (!it.isRecycled) it.recycle() }
        if (ownsRecognitionSource && !recognitionSource.isRecycled) recognitionSource.recycle()
    }
}

/** 为灰绿底 LCD 提供自动拉伸、Otsu 和局部阈值三种稳定输入。 */
internal object ScanImagePreprocessor {

    fun prepare(source: Bitmap): PreparedScanImages {
        val focused = locateLcd(source)
        val recognitionSource = focused ?: source
        val width = recognitionSource.width
        val height = recognitionSource.height
        val colors = IntArray(width * height)
        recognitionSource.getPixels(colors, 0, width, 0, 0, width, height)
        val grayscale = IntArray(colors.size)
        val histogram = IntArray(256)
        colors.forEachIndexed { index, color ->
            val red = color shr 16 and 0xFF
            val green = color shr 8 and 0xFF
            val blue = color and 0xFF
            val luminance = (red * 299 + green * 587 + blue * 114 + 500) / 1000
            grayscale[index] = luminance
            histogram[luminance]++
        }

        val low = percentile(histogram, colors.size, 0.02f)
        val high = percentile(histogram, colors.size, 0.98f).coerceAtLeast(low + 24)
        val stretched = IntArray(grayscale.size) { index ->
            ((grayscale[index] - low) * 255 / (high - low)).coerceIn(0, 255)
        }
        val enhanced = grayscaleBitmap(width, height, stretched)

        val globalThreshold = otsuThreshold(stretched)
        val globalMask = BooleanArray(stretched.size) { stretched[it] <= globalThreshold }
        val adaptiveMask = adaptiveDarkMask(stretched, width, height)
        val globalBitmap = binaryBitmap(width, height, globalMask)
        val adaptiveBitmap = binaryBitmap(width, height, adaptiveMask)

        return PreparedScanImages(
            recognitionSource = recognitionSource,
            ownsRecognitionSource = focused != null,
            ocrVariants = listOf(enhanced, globalBitmap, adaptiveBitmap),
            segmentMasks = listOf(
                BinaryImage(width, height, globalMask),
                BinaryImage(width, height, adaptiveMask)
            )
        )
    }

    /**
     * 先找血压计屏幕的黄色闭合边框，再裁到边框内侧。整机入镜时，外壳文字和时间
     * 会让通用 OCR/连通域算法产生危险的伪读数；屏幕定位必须发生在数字识别之前。
     */
    private fun locateLcd(source: Bitmap): Bitmap? {
        val longest = max(source.width, source.height)
        val scale = min(1f, 420f / longest)
        val sampleWidth = max(1, (source.width * scale).toInt())
        val sampleHeight = max(1, (source.height * scale).toInt())
        val sample = if (sampleWidth == source.width && sampleHeight == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, sampleWidth, sampleHeight, true)
        }
        try {
            val colors = IntArray(sampleWidth * sampleHeight)
            sample.getPixels(colors, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
            val yellow = BooleanArray(colors.size) { index ->
                val color = colors[index]
                val red = color shr 16 and 0xFF
                val green = color shr 8 and 0xFF
                val blue = color and 0xFF
                red > 105 && green > 65 &&
                    blue * 100 < green * 86 &&
                    red * 100 > green * 108 &&
                    red - blue > 45
            }
            val components = connectedYellowComponents(yellow, sampleWidth, sampleHeight)
            val imageArea = sampleWidth * sampleHeight.toFloat()
            val screen = components
                .filter { component ->
                    val areaFraction = component.width * component.height / imageArea
                    val aspect = component.width.toFloat() / component.height
                    areaFraction in 0.08f..0.55f &&
                        aspect in 0.50f..1.35f &&
                        component.width >= sampleWidth * 0.28f &&
                        component.height >= sampleHeight * 0.25f
                }
                .maxByOrNull { it.width * it.height }
                ?: return null

            val inset = max(2f, min(screen.width, screen.height) * 0.035f)
            val leftFraction = ((screen.left + inset) / sampleWidth).coerceIn(0f, 1f)
            val topFraction = ((screen.top + inset) / sampleHeight).coerceIn(0f, 1f)
            val rightFraction = ((screen.right - inset) / sampleWidth).coerceIn(0f, 1f)
            val bottomFraction = ((screen.bottom - inset) / sampleHeight).coerceIn(0f, 1f)
            val left = (source.width * leftFraction).toInt().coerceIn(0, source.width - 1)
            val top = (source.height * topFraction).toInt().coerceIn(0, source.height - 1)
            val right = (source.width * rightFraction).toInt().coerceIn(left + 1, source.width)
            val bottom = (source.height * bottomFraction).toInt().coerceIn(top + 1, source.height)
            return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        } finally {
            if (sample !== source) sample.recycle()
        }
    }

    private fun connectedYellowComponents(
        pixels: BooleanArray,
        width: Int,
        height: Int
    ): List<ColorComponent> {
        val visited = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        val result = mutableListOf<ColorComponent>()
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
            var count = 0
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                left = min(left, x)
                top = min(top, y)
                right = max(right, x + 1)
                bottom = max(bottom, y + 1)
                count++
                if (x > 0) tail = enqueueYellow(index - 1, pixels, visited, queue, tail)
                if (x + 1 < width) tail = enqueueYellow(index + 1, pixels, visited, queue, tail)
                if (y > 0) tail = enqueueYellow(index - width, pixels, visited, queue, tail)
                if (y + 1 < height) tail = enqueueYellow(index + width, pixels, visited, queue, tail)
            }
            if (count >= 12) result += ColorComponent(left, top, right, bottom)
        }
        return result
    }

    private fun enqueueYellow(
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

    private data class ColorComponent(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private fun percentile(histogram: IntArray, count: Int, percentile: Float): Int {
        val target = (count * percentile).toInt().coerceIn(0, count - 1)
        var cumulative = 0
        histogram.forEachIndexed { value, frequency ->
            cumulative += frequency
            if (cumulative > target) return value
        }
        return 255
    }

    private fun otsuThreshold(values: IntArray): Int {
        val histogram = IntArray(256)
        values.forEach { histogram[it]++ }
        val total = values.size
        var totalWeighted = 0L
        histogram.forEachIndexed { value, count -> totalWeighted += value.toLong() * count }

        var backgroundWeight = 0
        var backgroundWeighted = 0L
        var bestVariance = -1.0
        var bestThreshold = 127
        for (threshold in 0..255) {
            val count = histogram[threshold]
            backgroundWeight += count
            if (backgroundWeight == 0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0) break
            backgroundWeighted += threshold.toLong() * count
            val backgroundMean = backgroundWeighted.toDouble() / backgroundWeight
            val foregroundMean = (totalWeighted - backgroundWeighted).toDouble() / foregroundWeight
            val variance = backgroundWeight.toDouble() * foregroundWeight *
                (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = threshold
            }
        }
        // 避免把大面积中灰 LCD 背景整体判成前景。
        return min(bestThreshold, 150)
    }

    private fun adaptiveDarkMask(values: IntArray, width: Int, height: Int): BooleanArray {
        val stride = width + 1
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += values[y * width + x]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + rowSum
            }
        }

        val radius = max(8, min(width, height) / 18)
        return BooleanArray(values.size) { index ->
            val x = index % width
            val y = index / width
            val left = max(0, x - radius)
            val top = max(0, y - radius)
            val right = min(width, x + radius + 1)
            val bottom = min(height, y + radius + 1)
            val sum = integral[bottom * stride + right] - integral[top * stride + right] -
                integral[bottom * stride + left] + integral[top * stride + left]
            val mean = sum / ((right - left) * (bottom - top))
            values[index] < mean - 7
        }
    }

    private fun grayscaleBitmap(width: Int, height: Int, values: IntArray): Bitmap {
        val colors = IntArray(values.size) { index ->
            val value = values[index]
            0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(colors, 0, width, 0, 0, width, height)
        }
    }

    private fun binaryBitmap(width: Int, height: Int, mask: BooleanArray): Bitmap {
        val colors = IntArray(mask.size) { if (mask[it]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(colors, 0, width, 0, 0, width, height)
        }
    }
}
