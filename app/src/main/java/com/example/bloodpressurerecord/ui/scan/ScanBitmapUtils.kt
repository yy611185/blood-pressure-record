package com.example.bloodpressurerecord.ui.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 把 ImageProxy 的 CameraX 有效 cropRect 转成按旋转角度摆正的 Bitmap。 */
fun ImageProxy.toUprightBitmap(): Bitmap {
    val bufferBitmap = toBitmap()
    val crop = cropRect
    val bitmap = if (
        crop.left > 0 || crop.top > 0 ||
        crop.right < bufferBitmap.width || crop.bottom < bufferBitmap.height
    ) {
        Bitmap.createBitmap(
            bufferBitmap,
            crop.left.coerceIn(0, bufferBitmap.width - 1),
            crop.top.coerceIn(0, bufferBitmap.height - 1),
            crop.width().coerceAtMost(bufferBitmap.width - crop.left),
            crop.height().coerceAtMost(bufferBitmap.height - crop.top)
        ).also { if (it !== bufferBitmap) bufferBitmap.recycle() }
    } else {
        bufferBitmap
    }
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

/**
 * CameraController 的 cropRect 已与 PreviewView 对齐；这里再按界面中央 3:4 引导框裁剪，
 * 让 LCD 数字占满 OCR 输入。保留少量边距，避免用户贴框时切掉最外侧段码。
 */
fun Bitmap.cropToGuideFrame(
    previewWidth: Int,
    previewHeight: Int,
    horizontalPaddingPx: Float,
    marginFraction: Float = 0.06f
): Bitmap {
    if (previewWidth <= 0 || previewHeight <= 0) return this
    val guideWidth = (previewWidth - horizontalPaddingPx * 2f).coerceAtLeast(1f)
    val guideHeight = min(previewHeight.toFloat(), guideWidth * 4f / 3f)
    val margin = min(guideWidth, guideHeight) * marginFraction
    val leftFraction = ((horizontalPaddingPx - margin) / previewWidth).coerceIn(0f, 1f)
    val rightFraction = ((previewWidth - horizontalPaddingPx + margin) / previewWidth)
        .coerceIn(0f, 1f)
    val guideTop = (previewHeight - guideHeight) / 2f
    val topFraction = ((guideTop - margin) / previewHeight).coerceIn(0f, 1f)
    val bottomFraction = ((guideTop + guideHeight + margin) / previewHeight).coerceIn(0f, 1f)

    val left = floor(width * leftFraction).toInt().coerceIn(0, width - 1)
    val top = floor(height * topFraction).toInt().coerceIn(0, height - 1)
    val right = ceil(width * rightFraction).toInt().coerceIn(left + 1, width)
    val bottom = ceil(height * bottomFraction).toInt().coerceIn(top + 1, height)
    if (left == 0 && top == 0 && right == width && bottom == height) return this
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}

/** 限制最长边，控制 OCR 与内存开销；小于目标尺寸时原样返回。 */
fun Bitmap.normalize(maxDimension: Int = 1600): Bitmap {
    val longest = max(width, height)
    if (longest <= maxDimension) return this
    val scale = maxDimension.toFloat() / longest
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}
