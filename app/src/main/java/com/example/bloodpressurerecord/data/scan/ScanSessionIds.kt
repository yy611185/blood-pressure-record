package com.example.bloodpressurerecord.data.scan

/**
 * 识别照片目录名和备份 record_id 共用的安全标识。
 * 允许 UUID、手动测试 id，以及旧备份前缀 `bp_measurements:` / `blood_pressure_records:`。
 */
object ScanSessionIds {
    const val MAX_LENGTH = 200
    private val SAFE = Regex("^[A-Za-z0-9_.:-]+$")

    fun isSafe(sessionId: String): Boolean =
        sessionId.isNotEmpty() &&
            sessionId.length <= MAX_LENGTH &&
            !sessionId.contains("..") &&
            SAFE.matches(sessionId)

    fun requireSafe(sessionId: String): String {
        val id = sessionId.trim()
        require(isSafe(id)) { "不安全的记录标识" }
        return id
    }
}
