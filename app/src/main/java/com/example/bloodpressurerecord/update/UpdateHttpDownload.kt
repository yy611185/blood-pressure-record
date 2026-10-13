package com.example.bloodpressurerecord.update

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException

internal class UpdateTransferException(message: String) : IOException(message)

internal class UpdateSourcesExhaustedException(
    val attemptedUrls: List<String>,
    val verificationFailure: Boolean,
    cause: Exception
) : IOException("下载失败，已尝试：${attemptedUrls.joinToString("、") { updateDownloadSource(it) }}；" +
    (cause.message ?: "请确认网络与存储空间后重试"), cause)

/** Internal transport seam for offline JVM tests. The Android caller validates every URL and requires HTTPS. */
internal class UpdateHttpDownload(
    private val openConnection: (String) -> HttpURLConnection,
    private val connectTimeoutMillis: Int = 10_000,
    private val stallTimeoutMillis: Int = 15_000
) {
    suspend fun download(
        candidates: List<String>,
        partial: File,
        expectedSize: Long,
        checkActive: () -> Unit = {},
        onConnection: (HttpURLConnection?) -> Unit = {},
        onSource: (String) -> Unit = {},
        onProgress: (Long) -> Unit = {},
        usableSpace: () -> Long = { partial.parentFile!!.usableSpace },
        verifyFile: suspend (File) -> Unit
    ) {
        require(candidates.isNotEmpty())
        if (partial.length() > expectedSize) throw UpdateVerificationException("已下载文件超过预期大小")
        val attempted = mutableListOf<String>()
        var verificationFailure = false
        for (url in candidates) {
            checkActive()
            attempted += url
            onSource(url)
            var connection: HttpURLConnection? = null
            var watchdog: UpdateStallWatchdog? = null
            try {
                if (partial.length() == expectedSize) {
                    verifyFile(partial)
                    return
                }
                var offset = partial.length()
                if (usableSpace() < expectedSize - offset + UpdateDownloadSafety.STORAGE_RESERVE) {
                    throw UpdateTransferException("存储空间不足，请至少保留安装包大小及 64 MB 空间")
                }
                val http = openConnection(url)
                connection = http
                onConnection(http)
                http.instanceFollowRedirects = false
                http.connectTimeout = connectTimeoutMillis
                http.readTimeout = stallTimeoutMillis
                http.setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) http.setRequestProperty("Range", "bytes=$offset-")
                // Covers response headers/TLS as well as body reads: a trickling partial header
                // cannot keep this attempt blocked beyond the no-body-byte deadline.
                val stall = UpdateStallWatchdog(http, stallTimeoutMillis)
                watchdog = stall
                val responseBytes = when (val response = http.responseCode) {
                    200 -> { offset = 0; expectedSize }
                    206 -> UpdateDownloadSafety.requireRange(http.getHeaderField("Content-Range"), offset, expectedSize)
                    in 300..399 -> throw UpdateVerificationException("服务器返回了不允许的下载重定向")
                    else -> throw UpdateTransferException("下载服务暂时不可用（HTTP $response）")
                }
                stall.requireActive()
                val length = http.getHeaderFieldLong("Content-Length", -1)
                if (length >= 0 && length != responseBytes) {
                    throw UpdateVerificationException("下载响应大小与清单不一致")
                }
                val encoding = http.getHeaderField("Content-Encoding")
                if (!encoding.isNullOrBlank() && !encoding.equals("identity", true)) {
                    throw UpdateVerificationException("下载响应编码不受支持")
                }
                if (usableSpace() < expectedSize - offset + UpdateDownloadSafety.STORAGE_RESERVE) {
                    throw UpdateTransferException("存储空间不足，无法继续下载")
                }
                onProgress(offset)
                var received = 0L
                var lastProgress = 0L
                // Read the raw stream so receiving a small chunk immediately refreshes the deadline.
                http.inputStream.use { input ->
                    FileOutputStream(partial, offset > 0).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            checkActive()
                            stall.requireActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            stall.receivedBytes()
                            checkActive()
                            UpdateDownloadSafety.requireWriteFits(offset, count, expectedSize)
                            if (received > responseBytes - count) {
                                throw UpdateVerificationException("下载响应超过声明范围")
                            }
                            if (usableSpace() < UpdateDownloadSafety.STORAGE_RESERVE + count) {
                                throw UpdateTransferException("存储空间不足，下载已暂停")
                            }
                            output.write(buffer, 0, count)
                            offset += count
                            received += count
                            val now = System.nanoTime()
                            if (now - lastProgress >= TimeUnit.MILLISECONDS.toNanos(500)) {
                                onProgress(offset)
                                lastProgress = now
                            }
                        }
                        output.fd.sync()
                    }
                }
                stall.requireActive()
                if (received != responseBytes) throw UpdateTransferException("下载连接中断，可重试续传")
                onProgress(offset)
                watchdog.close()
                watchdog = null
                checkActive()
                verifyFile(partial)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                checkActive()
                if (failure is UpdateVerificationException) {
                    verificationFailure = true
                    partial.delete()
                }
                if (attempted.size == candidates.size) {
                    val reason = if (watchdog?.timedOut == true) SocketTimeoutException("下载来源长时间未收到数据") else failure
                    throw UpdateSourcesExhaustedException(attempted.toList(), verificationFailure, reason)
                }
            } finally {
                watchdog?.close()
                connection?.disconnect()
                onConnection(null)
            }
        }
    }
}

private class UpdateStallWatchdog(private val connection: HttpURLConnection, timeoutMillis: Int) {
    private val deadlineNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())
    private val lastByte = AtomicLong(System.nanoTime())
    private val expired = AtomicBoolean(false)
    private val timer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "update-stall-watchdog").apply { isDaemon = true }
    }
    val timedOut: Boolean get() = expired.get()

    init {
        timer.scheduleWithFixedDelay({
            if (System.nanoTime() - lastByte.get() >= deadlineNanos && expired.compareAndSet(false, true)) {
                connection.disconnect()
            }
        }, 100, 100, TimeUnit.MILLISECONDS)
    }

    fun receivedBytes() {
        requireActive()
        lastByte.set(System.nanoTime())
    }

    fun requireActive() {
        if (timedOut) throw SocketTimeoutException("下载来源停滞，正在切换来源")
    }

    fun close() { timer.shutdownNow() }
}
