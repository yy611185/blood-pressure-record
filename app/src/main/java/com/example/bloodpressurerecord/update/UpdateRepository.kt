package com.example.bloodpressurerecord.update

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateRequestException(message: String, cause: Throwable? = null) : IOException(message, cause)

class UpdateRepository(private val preferences: UpdatePreferences) {
    suspend fun check(clock: () -> Long = System::currentTimeMillis): UpdateManifest = withContext(Dispatchers.IO) {
        val connection = URL(UpdateUrls.MANIFEST_URL).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Cache-Control", "no-cache")
            val code = connection.responseCode
            if (code != 200) {
                throw UpdateRequestException(
                    if (code in 300..399) "更新服务返回了不可信重定向" else "暂时无法获取更新清单（HTTP $code）"
                )
            }
            if (connection.contentLengthLong > UpdateManifest.MAX_JSON_BYTES) {
                throw UpdateRequestException("更新清单过大")
            }
            val bytes = connection.inputStream.use { readBounded(it, UpdateManifest.MAX_JSON_BYTES) }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val manifest = try { UpdateManifest.parse(text) } catch (error: IllegalArgumentException) {
                throw UpdateRequestException("更新清单格式无效，请稍后重试", error)
            }
            preferences.recordSuccessfulCheck(manifest, clock())
            manifest
        } finally {
            connection.disconnect()
        }
    }

    suspend fun cachedManifest(): UpdateManifest? {
        val text = preferences.readCachedManifest() ?: return null
        return try { UpdateManifest.parse(text) } catch (_: IllegalArgumentException) { null }
    }
}

/** Counts actual bytes even when a server omits or lies about Content-Length. */
internal fun readBounded(input: InputStream, maximum: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maximum, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) {
            break
        }
        if (output.size() > maximum - read) {
            throw UpdateRequestException("更新清单过大")
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
