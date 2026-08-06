package com.example.bloodpressurerecord.data.scan

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 待保存的识别照片：groupNumber 从 1 开始，与确认页“第 N 组”一致。 */
data class ScanPhoto(val groupNumber: Int, val bitmap: Bitmap)

/** 照片保存抽象，便于 ViewModel 测试注入假实现。 */
fun interface PhotoSaver {
    suspend fun save(sessionId: String, photos: List<ScanPhoto>): Result<Unit>
}

/** 把 Bitmap 编码为 JPEG 后交给 [ScanPhotoStore] 落盘（App 私有目录，不进相册）。 */
class ScanPhotoSaver(private val store: ScanPhotoStore) : PhotoSaver {

    override suspend fun save(sessionId: String, photos: List<ScanPhoto>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                photos.forEach { photo ->
                    val bytes = ByteArrayOutputStream().use { out ->
                        photo.bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        out.toByteArray()
                    }
                    store.save(sessionId, photo.groupNumber, bytes)
                }
            }
        }
}
