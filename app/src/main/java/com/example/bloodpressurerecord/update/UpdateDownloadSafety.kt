package com.example.bloodpressurerecord.update

import java.io.File
import java.security.MessageDigest

/** Pure bounded-transfer and identity rules, shared by the downloader and JVM tests. */
internal object UpdateDownloadSafety {
    const val MAX_BYTES = 256L * 1024 * 1024
    const val STORAGE_RESERVE = 64L * 1024 * 1024

    fun requireWriteFits(current: Long, count: Int, expected: Long) {
        if (expected !in 1..MAX_BYTES || current < 0 || count < 0 || current > expected - count) {
            throw UpdateVerificationException("安装包超过清单大小或下载上限")
        }
    }

    fun requireRange(header: String?, start: Long, total: Long): Long {
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header.orEmpty())
            ?: throw UpdateVerificationException("服务器续传响应不正确")
        val values = match.groupValues.drop(1).map { it.toLongOrNull() }
        val first = values[0]
        val end = values[1]
        val size = values[2]
        if (first != start || size != total || end == null || end < start || end != total - 1) {
            throw UpdateVerificationException("服务器续传范围与安装包不一致")
        }
        return end - start + 1
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun acceptsSigningIdentity(
        installedCurrent: Set<String>, installedHistory: Set<String>,
        candidateCurrent: Set<String>, candidateHistory: Set<String>, rotationSupported: Boolean,
        anchor: String
    ): Boolean {
        if (installedCurrent.isEmpty() || candidateCurrent.isEmpty()) return false
        if (anchor !in installedCurrent && (installedCurrent.size != 1 || anchor !in installedHistory)) return false
        if (installedCurrent == candidateCurrent) return true
        if (!rotationSupported || installedCurrent.size != 1 || candidateCurrent.size != 1) return false
        // Direction matters: a candidate lineage must contain the *current* installed signer.
        return installedCurrent.single() in candidateHistory
    }

    fun requirePackage(packageName: String?, versionCode: Long, expectedCode: Long,
                       installedCode: Long, debuggable: Boolean, testOnly: Boolean) {
        if (packageName != "com.yang.bloodpressure" || versionCode != expectedCode ||
            versionCode <= installedCode || debuggable || testOnly) {
            throw UpdateVerificationException("安装包身份、版本或构建类型不符合正式更新要求")
        }
    }
}

class UpdateVerificationException(message: String) : Exception(message)
