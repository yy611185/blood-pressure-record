package com.example.bloodpressurerecord.data.scan

import java.io.File

/**
 * 识别照片存储：`filesDir/scan_photos/<sessionId>/<groupNumber>.jpg`。
 * 纯 File 逻辑（不依赖 Android），便于 JVM 单测；由 [ScanPhotoSaver] 负责 Bitmap 编码。
 */
class ScanPhotoStore(private val rootDir: File) {

    fun save(sessionId: String, groupNumber: Int, jpegBytes: ByteArray): File {
        val dir = File(rootDir, sessionId).apply { mkdirs() }
        val file = File(dir, "$groupNumber.jpg")
        file.writeBytes(jpegBytes)
        return file
    }

    /** 删除某条记录关联的照片目录。 */
    fun deleteForSession(sessionId: String) {
        File(rootDir, sessionId).deleteRecursively()
    }

    /** 删除全部照片，返回删除的记录目录数。 */
    fun deleteAll(): Int {
        val dirs = sessionDirs()
        dirs.forEach { it.deleteRecursively() }
        return dirs.size
    }

    /** 删除修改时间早于阈值的照片目录，返回删除数。 */
    fun deleteOlderThan(thresholdMillis: Long): Int {
        val dirs = sessionDirs().filter { it.lastModified() < thresholdMillis }
        dirs.forEach { it.deleteRecursively() }
        return dirs.size
    }

    fun listSessionIds(): List<String> = sessionDirs().map { it.name }.sorted()

    fun sizeBytes(): Long = rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun sessionDirs(): List<File> =
        rootDir.listFiles()?.filter { it.isDirectory }?.toList() ?: emptyList()
}
