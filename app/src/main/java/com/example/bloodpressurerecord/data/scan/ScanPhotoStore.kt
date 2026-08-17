package com.example.bloodpressurerecord.data.scan

import java.io.File

/**
 * 识别照片存储：`filesDir/scan_photos/<sessionId>/<groupNumber>.jpg`。
 * 纯 File 逻辑（不依赖 Android），便于 JVM 单测；由 [ScanPhotoSaver] 负责 Bitmap 编码。
 */
class ScanPhotoStore(private val rootDir: File) {

    fun save(sessionId: String, groupNumber: Int, jpegBytes: ByteArray): File {
        require(groupNumber > 0) { "组序号必须为正整数" }
        val dir = sessionDir(sessionId).apply { mkdirs() }
        val file = File(dir, "$groupNumber.jpg")
        checkInsideRoot(file)
        file.writeBytes(jpegBytes)
        return file
    }

    /** 删除某条记录关联的照片目录。 */
    fun deleteForSession(sessionId: String) {
        val dir = sessionDir(sessionId)
        if (dir.exists()) dir.deleteRecursively()
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

    fun listSessionIds(): List<String> = sessionDirs().map { decodeDirName(it.name) }.sorted()

    fun sizeBytes(): Long = rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun sessionDir(sessionId: String): File {
        val dir = File(rootDir, encodeDirName(sessionId))
        checkInsideRoot(dir)
        return dir
    }

    /**
     * Windows 不允许目录名含 `:`。旧备份 id（`bp_measurements:12`）在落盘时改成 `~`，
     * `~` 不在 [ScanSessionIds] 允许字符里，因此不会和真实 id 冲突。
     */
    private fun encodeDirName(sessionId: String): String =
        ScanSessionIds.requireSafe(sessionId).replace(':', '~')

    private fun decodeDirName(dirName: String): String = dirName.replace('~', ':')

    private fun checkInsideRoot(target: File) {
        val root = rootDir.canonicalFile
        val resolved = target.canonicalFile
        val inside = resolved == root || resolved.toPath().startsWith(root.toPath())
        check(inside) { "照片路径超出存储目录" }
    }

    private fun sessionDirs(): List<File> =
        rootDir.listFiles()?.filter { it.isDirectory }?.toList() ?: emptyList()
}
