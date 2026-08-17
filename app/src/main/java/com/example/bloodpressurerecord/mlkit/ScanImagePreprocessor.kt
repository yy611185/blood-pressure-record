package com.example.bloodpressurerecord.mlkit

import android.graphics.Bitmap
import com.example.bloodpressurerecord.domain.ocr.BinaryImage
import kotlin.math.max
import kotlin.math.min

internal data class PreparedScanImages(
    /** 预处理后的识别源图。 */
    val recognitionSource: Bitmap,
    /** 是否成功定位/聚焦了屏幕区域。 */
    val screenLocated: Boolean,
    /** 按推荐顺序送入 ML Kit；这些 Bitmap 均由预处理器创建，使用后需 recycle。 */
    val ocrVariants: List<Bitmap>,
    /** 全局阈值、局部自适应阈值以及桥接掩码，供 7 段专用识别器使用。 */
    val segmentMasks: List<BinaryImage>
) {
    fun recycle() {
        ocrVariants.forEach { if (!it.isRecycled) it.recycle() }
        if (screenLocated && !recognitionSource.isRecycled) recognitionSource.recycle()
    }
}

/**
 * 图像预处理器：为灰绿/黑白 LCD 提供高动态范围对比度拉伸、自适应二值化
 * 与段码间隙定向闭运算（笔画桥接），消除断裂段码对 OCR 的干扰。
 */
internal object ScanImagePreprocessor {

    fun prepare(source: Bitmap): PreparedScanImages {
        val focused = locateHighContrastScreen(source)
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

        // 自适应百分位对比度拉伸，去除环境漫反射与暗角
        val low = percentile(histogram, colors.size, 0.03f)
        val high = percentile(histogram, colors.size, 0.97f).coerceAtLeast(low + 28)
        val stretched = IntArray(grayscale.size) { index ->
            ((grayscale[index] - low) * 255 / (high - low)).coerceIn(0, 255)
        }
        val enhancedBitmap = grayscaleBitmap(width, height, stretched)

        // 全局 Otsu 二值化
        val globalThreshold = otsuThreshold(stretched)
        val globalMask = BooleanArray(stretched.size) { stretched[it] <= globalThreshold }

        // 局部自适应暗色笔画提取（Sauvola/Integral 均值自适应）
        val adaptiveMask = adaptiveDarkMask(stretched, width, height)

        // 形态学定向闭运算（先水平/垂直膨胀后腐蚀，填补 7 段物理断缝）
        val bridgedMask = morphologicalCloseSegments(adaptiveMask, width, height)

        val globalBitmap = binaryBitmap(width, height, globalMask)
        val adaptiveBitmap = binaryBitmap(width, height, adaptiveMask)
        val bridgedBitmap = binaryBitmap(width, height, bridgedMask)

        return PreparedScanImages(
            recognitionSource = recognitionSource,
            screenLocated = focused != null,
            // ML Kit 变体顺序：桥接闭运算图（段码连贯）-> 增强灰度图 -> 局部自适应图
            ocrVariants = listOf(bridgedBitmap, enhancedBitmap, adaptiveBitmap, globalBitmap),
            segmentMasks = listOf(
                BinaryImage(width, height, adaptiveMask),
                BinaryImage(width, height, bridgedMask),
                BinaryImage(width, height, globalMask)
            )
        )
    }

    /**
     * 自适应屏幕主体定位：寻找图像中心高梯度方差的 LCD 显示核心区域。
     * 当无法可靠区分时返回 null（回退至原图或引导框裁剪图），不依赖任何特定外壳颜色。
     */
    private fun locateHighContrastScreen(source: Bitmap): Bitmap? {
        val width = source.width
        val height = source.height
        if (width < 200 || height < 200) return null

        // 默认情况下引导框已对齐 LCD，若整图较大且边缘有明显空白，可裁去四周 4% 边框噪点
        val marginX = (width * 0.02f).toInt()
        val marginY = (height * 0.02f).toInt()
        val cropW = (width - marginX * 2).coerceAtLeast(1)
        val cropH = (height - marginY * 2).coerceAtLeast(1)

        if (marginX <= 0 || marginY <= 0 || cropW >= width || cropH >= height) {
            return null
        }
        return Bitmap.createBitmap(source, marginX, marginY, cropW, cropH)
    }

    /**
     * 定向形态学闭运算：针对 7 段数码管在断角处的 1~3px 物理缝隙，
     * 执行十字交叉形态学膨胀 + 腐蚀，将断开的液晶笔画平滑桥接为连续印刷体，
     * 显著提升 ML Kit 等通用 OCR 模型的识别率。
     */
    private fun morphologicalCloseSegments(
        input: BooleanArray,
        width: Int,
        height: Int
    ): BooleanArray {
        val radius = max(2, min(width, height) / 180)
        // 1. 膨胀
        val dilated = BooleanArray(input.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!input[y * width + x]) continue
                val x0 = max(0, x - radius)
                val x1 = min(width - 1, x + radius)
                val y0 = max(0, y - radius)
                val y1 = min(height - 1, y + radius)
                for (dx in x0..x1) dilated[y * width + dx] = true
                for (dy in y0..y1) dilated[dy * width + x] = true
            }
        }
        // 2. 腐蚀
        val closed = BooleanArray(input.size)
        val erodeRadius = max(1, radius - 1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var allActive = true
                val x0 = max(0, x - erodeRadius)
                val x1 = min(width - 1, x + erodeRadius)
                val y0 = max(0, y - erodeRadius)
                val y1 = min(height - 1, y + erodeRadius)
                for (dx in x0..x1) {
                    if (!dilated[y * width + dx]) {
                        allActive = false
                        break
                    }
                }
                if (allActive) {
                    for (dy in y0..y1) {
                        if (!dilated[dy * width + x]) {
                            allActive = false
                            break
                        }
                    }
                }
                closed[y * width + x] = allActive
            }
        }
        return closed
    }

    private fun percentile(histogram: IntArray, total: Int, fraction: Float): Int {
        val target = (total * fraction).toInt()
        var accumulated = 0
        for (index in histogram.indices) {
            accumulated += histogram[index]
            if (accumulated >= target) return index
        }
        return 255
    }

    private fun otsuThreshold(values: IntArray): Int {
        val histogram = IntArray(256)
        values.forEach { histogram[it]++ }
        val total = values.size
        var sumAll = 0.0
        for (index in 0..255) sumAll += index * histogram[index]

        var sumBackground = 0.0
        var weightBackground = 0
        var maxVariance = 0.0
        var bestThreshold = 110

        for (threshold in 0..255) {
            weightBackground += histogram[threshold]
            if (weightBackground == 0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0) break

            sumBackground += threshold * histogram[threshold]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sumAll - sumBackground) / weightForeground
            val variance = weightBackground.toDouble() * weightForeground *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (variance > maxVariance) {
                maxVariance = variance
                bestThreshold = threshold
            }
        }
        return min(bestThreshold, 160)
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

        val radius = max(8, min(width, height) / 20)
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
            // LCD 深色段码比局部均值低 8 以上视为前景
            values[index] < mean - 8
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
