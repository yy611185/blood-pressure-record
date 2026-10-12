package com.example.bloodpressurerecord.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.AtomicFile
import com.example.bloodpressurerecord.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.Properties
import javax.net.ssl.HttpsURLConnection

enum class UpdateDownloadPhase { IDLE, WAITING_NETWORK, DOWNLOADING, PAUSED, VERIFYING, READY, FAILED }

data class UpdateDownloadState(
    val phase: UpdateDownloadPhase = UpdateDownloadPhase.IDLE,
    val manifest: UpdateManifest? = null,
    val downloadedBytes: Long = 0,
    val message: String? = null
)

private class UpdateTransferException(message: String) : IOException(message)

/** Single application-owned foreground transfer; no health repository or backup data is referenced. */
class UpdateDownloadManager(
    context: Context,
    private val preferences: UpdatePreferences,
    private val verifier: UpdateVerifier
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val initialized = CompletableDeferred<Unit>()
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val partialDir = File(appContext.filesDir, "updates/partial")
    private val verifiedDir = File(appContext.filesDir, "updates/verified")
    private val metadata = AtomicFile(File(appContext.filesDir, "updates/task.properties"))
    private val mutableState = MutableStateFlow(UpdateDownloadState())
    val state: StateFlow<UpdateDownloadState> = mutableState.asStateFlow()
    @Volatile private var foreground = false
    @Volatile private var wifiOnly = true
    @Volatile private var connection: HttpsURLConnection? = null
    @Volatile private var networkMonitoringAvailable = true
    private var job: Job? = null
    @Volatile private var runningNetwork: Network? = null
    private var automatic = false
    private var automaticPaused = false
    private var currentSettings = UpdateSettings()

    init {
        scope.launch {
            try {
                val settings = preferences.settings.first()
                mutex.withLock {
                    currentSettings = settings
                    wifiOnly = settings.wifiOnly
                    restore()
                    // Persisted skip/snooze/disable rules are recomputed by reconcile, including expiry.
                    automaticPaused = false
                    reconcile()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(phase = UpdateDownloadPhase.FAILED,
                    message = "无法恢复更新任务，可手动检查后重试")
            } finally {
                initialized.complete(Unit)
            }
            try {
                preferences.settings.collect { settings ->
                    mutex.withLock {
                        currentSettings = settings
                        wifiOnly = settings.wifiOnly
                        // pauseAutomatic bridges the UI action until its DataStore transaction arrives.
                        automaticPaused = false
                        reconcile()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutex.withLock {
                    automaticPaused = true
                    if (automatic) {
                        stopTransfer()
                    }
                    mutableState.value = mutableState.value.copy(message = "更新偏好读取失败，自动下载已暂停")
                }
            }
        }
        try {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = networkChanged()
                override fun onLost(network: Network) = networkChanged()
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = networkChanged()
            })
        } catch (_: Exception) {
            // If monitoring cannot be established, refuse transfers rather than bypassing network rules.
            networkMonitoringAvailable = false
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            connection?.disconnect()
        }
        scope.launch {
            initialized.await()
            mutex.withLock { reconcile() }
        }
    }

    fun setWifiOnly(value: Boolean) {
        wifiOnly = value
        if (eligibleNetwork() == null) {
            connection?.disconnect()
        }
        scope.launch {
            initialized.await()
            mutex.withLock { reconcile() }
        }
    }

    fun pauseAutomatic() {
        scope.launch {
            initialized.await()
            mutex.withLock {
                if (automatic && state.value.phase != UpdateDownloadPhase.READY &&
                    state.value.phase != UpdateDownloadPhase.IDLE && state.value.phase != UpdateDownloadPhase.FAILED) {
                    automaticPaused = true
                    stopTransfer()
                    if (state.value.phase == UpdateDownloadPhase.READY || state.value.phase == UpdateDownloadPhase.FAILED) {
                        return@withLock
                    }
                    mutableState.value = mutableState.value.copy(phase = UpdateDownloadPhase.PAUSED,
                        message = "自动下载已暂停，可手动继续")
                }
            }
        }
    }

    suspend fun start(manifest: UpdateManifest, automatic: Boolean = false) = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            try {
                if (automatic) {
                    val settings = preferences.settings.first()
                    currentSettings = settings
                    if (!UpdatePolicy.shouldAutoDownload(manifest, BuildConfig.VERSION_CODE.toLong(), settings,
                            System.currentTimeMillis())) {
                        return@withLock
                    }
                }
                UpdateUrls.validateApk(manifest.url, manifest.version, manifest.apk)
                if (manifest.size !in 1..UpdateDownloadSafety.MAX_BYTES || manifest.versionCode <= BuildConfig.VERSION_CODE) {
                    throw UpdateVerificationException("更新版本或安装包大小不符合要求")
                }
                val previous = mutableState.value
                // A background check cannot replace a restored or explicitly requested existing download.
                if (automatic && previous.manifest != null && previous.manifest.identity != manifest.identity &&
                    previous.phase != UpdateDownloadPhase.READY && previous.phase != UpdateDownloadPhase.FAILED) {
                    return@withLock
                }
                if (previous.manifest?.identity != manifest.identity) {
                    stopTransfer()
                    clearFiles()
                    mutableState.value = UpdateDownloadState(UpdateDownloadPhase.PAUSED, manifest)
                    this@UpdateDownloadManager.automatic = automatic
                    automaticPaused = false
                    saveTask(manifest)
                } else {
                    if (automatic && this@UpdateDownloadManager.automatic) {
                        automaticPaused = false
                    }
                    if (!automatic) {
                        if (previous.phase == UpdateDownloadPhase.FAILED) {
                            stopTransfer()
                        }
                        this@UpdateDownloadManager.automatic = false
                        automaticPaused = false
                        saveTask(manifest, if (previous.phase == UpdateDownloadPhase.FAILED) UpdateDownloadPhase.PAUSED else state.value.phase)
                    }
                    if (previous.phase == UpdateDownloadPhase.READY) {
                        return@withLock
                    }
                    // Automatic checks do not repeatedly restart failed transfers.
                    if (automatic && previous.phase == UpdateDownloadPhase.FAILED) {
                        return@withLock
                    }
                }
                reconcile(allowRetry = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                stopTransfer()
                if (failure is UpdateVerificationException) {
                    clearFiles()
                }
                mutableState.value = UpdateDownloadState(UpdateDownloadPhase.FAILED, manifest,
                    partialFile(manifest).length(), failureMessage(failure))
                persistFailure(manifest)
            }
        }
    }

    suspend fun retry() {
        val manifest = state.value.manifest ?: return
        start(manifest, automatic = false)
    }

    suspend fun cancel() = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            stopTransfer()
            val manifest = state.value.manifest
            var message: String? = null
            try {
                if (manifest != null) {
                    preferences.cancelAutomaticFor(manifest.versionCode)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "下载已取消，但取消偏好保存失败"
            } finally {
                clearFiles()
                mutableState.value = UpdateDownloadState(message = message)
                automaticPaused = false
            }
        }
    }

    suspend fun verifiedFile(): File? = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            val value = state.value
            if (value.phase != UpdateDownloadPhase.READY) {
                return@withLock null
            }
            val manifest = value.manifest ?: return@withLock null
            val file = completedFile(manifest)
            try {
                verifier.verify(file, manifest)
                file
            } catch (failure: UpdateVerificationException) {
                file.delete()
                mutableState.value = value.copy(phase = UpdateDownloadPhase.FAILED, message = failure.message)
                persistFailure(manifest)
                throw failure
            }
        }
    }

    private fun networkChanged() {
        // Disconnect immediately when the current route is no longer eligible.
        if (eligibleNetwork() != runningNetwork) {
            connection?.disconnect()
        }
        scope.launch {
            initialized.await()
            mutex.withLock { reconcile() }
        }
    }

    private fun eligibleNetwork(): Network? {
        if (!networkMonitoringAvailable) {
            return null
        }
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return null
        }
        if (wifiOnly && (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))) {
            return null
        }
        return network
    }

    private suspend fun reconcile(allowRetry: Boolean = false) {
        val value = mutableState.value
        val manifest = value.manifest ?: return
        if (value.phase == UpdateDownloadPhase.READY || (value.phase == UpdateDownloadPhase.FAILED && !allowRetry)) {
            return
        }
        val network = eligibleNetwork()
        val policyPaused = automatic && !UpdatePolicy.shouldAutoDownload(manifest,
            BuildConfig.VERSION_CODE.toLong(), currentSettings, System.currentTimeMillis())
        if (!foreground || automaticPaused || policyPaused || network == null) {
            stopTransfer()
            if (state.value.phase == UpdateDownloadPhase.READY || state.value.phase == UpdateDownloadPhase.FAILED) {
                return
            }
            mutableState.value = mutableState.value.copy(
                phase = if (foreground && !automaticPaused && !policyPaused) UpdateDownloadPhase.WAITING_NETWORK else UpdateDownloadPhase.PAUSED,
                downloadedBytes = partialFile(manifest).length(),
                message = when {
                    automaticPaused || policyPaused -> "自动下载已暂停，可手动继续"
                    !foreground -> "返回应用后继续下载"
                    !networkMonitoringAvailable -> "无法监听网络，请重启应用后重试"
                    wifiOnly -> "等待非计费 Wi-Fi 网络"
                    else -> "等待可用网络"
                })
            return
        }
        if (job?.isActive == true && runningNetwork == network) {
            return
        }
        stopTransfer()
        runningNetwork = network
        mutableState.value = UpdateDownloadState(UpdateDownloadPhase.DOWNLOADING, manifest, partialFile(manifest).length())
        job = scope.launch { transfer(manifest, network) }
    }

    private suspend fun stopTransfer() {
        connection?.disconnect()
        job?.cancelAndJoin()
        job = null
        runningNetwork = null
        connection = null
    }

    private suspend fun transfer(manifest: UpdateManifest, network: Network) {
        val partial = partialFile(manifest)
        try {
            partialDir.mkdirs()
            verifiedDir.mkdirs()
            var offset = partial.length()
            if (offset > manifest.size) {
                throw UpdateVerificationException("已下载文件超过预期大小")
            }
            if (partialDir.usableSpace < manifest.size - offset + UpdateDownloadSafety.STORAGE_RESERVE) {
                throw UpdateTransferException("存储空间不足，请至少保留安装包大小及 64 MB 空间")
            }
            if (offset < manifest.size) {
                currentCoroutineContext().ensureActive()
                if (!foreground || eligibleNetwork() != network) {
                    throw CancellationException()
                }
                val https = network.openConnection(URL(manifest.url)) as? HttpsURLConnection
                    ?: throw UpdateVerificationException("更新下载必须使用 HTTPS")
                connection = https
                https.instanceFollowRedirects = false
                https.connectTimeout = 10_000
                https.readTimeout = 15_000
                https.setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) {
                    https.setRequestProperty("Range", "bytes=$offset-")
                }
                val response = https.responseCode
                val responseBytes = when (response) {
                    200 -> {
                        offset = 0
                        manifest.size
                    }
                    206 -> UpdateDownloadSafety.requireRange(https.getHeaderField("Content-Range"), offset, manifest.size)
                    in 300..399 -> throw UpdateVerificationException("服务器返回了不允许的下载重定向")
                    else -> throw UpdateTransferException("下载服务暂时不可用（HTTP $response）")
                }
                val length = https.getHeaderFieldLong("Content-Length", -1)
                if (length >= 0 && length != responseBytes) {
                    throw UpdateVerificationException("下载响应大小与清单不一致")
                }
                val encoding = https.getHeaderField("Content-Encoding")
                if (!encoding.isNullOrBlank() && !encoding.equals("identity", true)) {
                    throw UpdateVerificationException("下载响应编码不受支持")
                }
                if (partialDir.usableSpace < manifest.size - offset + UpdateDownloadSafety.STORAGE_RESERVE) {
                    throw UpdateTransferException("存储空间不足，无法继续下载")
                }
                mutableState.value = UpdateDownloadState(UpdateDownloadPhase.DOWNLOADING, manifest, offset)
                var received = 0L
                var lastProgress = 0L
                https.inputStream.buffered().use { input ->
                    FileOutputStream(partial, offset > 0).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (!foreground || eligibleNetwork() != network) {
                                throw CancellationException()
                            }
                            val count = input.read(buffer)
                            if (count < 0) {
                                break
                            }
                            currentCoroutineContext().ensureActive()
                            if (!foreground || eligibleNetwork() != network) {
                                throw CancellationException()
                            }
                            UpdateDownloadSafety.requireWriteFits(offset, count, manifest.size)
                            if (received > responseBytes - count) {
                                throw UpdateVerificationException("下载响应超过声明范围")
                            }
                            if (partialDir.usableSpace < UpdateDownloadSafety.STORAGE_RESERVE + count) {
                                throw UpdateTransferException("存储空间不足，下载已暂停")
                            }
                            output.write(buffer, 0, count)
                            offset += count
                            received += count
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastProgress >= 500) {
                                mutableState.value = mutableState.value.copy(downloadedBytes = offset)
                                lastProgress = now
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (received != responseBytes) {
                    throw UpdateTransferException("下载连接中断，可重试续传")
                }
            }
            currentCoroutineContext().ensureActive()
            mutableState.value = UpdateDownloadState(UpdateDownloadPhase.VERIFYING, manifest, partial.length())
            verifier.verify(partial, manifest)
            currentCoroutineContext().ensureActive()
            val completed = completedFile(manifest)
            if (!partial.renameTo(completed)) {
                throw UpdateTransferException("无法保存校验后的安装包")
            }
            mutableState.value = UpdateDownloadState(UpdateDownloadPhase.READY, manifest, manifest.size,
                "安装包预检完成，点击安装后由系统验证并升级")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // A pause/disconnect must never turn into a visible failure or corrupt the next job's state.
            if (!currentCoroutineContext().isActiveForTransfer()) {
                return
            }
            if (failure !is UpdateVerificationException && (!foreground || eligibleNetwork() != network)) {
                return
            }
            if (failure is UpdateVerificationException) {
                partial.delete()
                completedFile(manifest).delete()
            }
            mutableState.value = UpdateDownloadState(UpdateDownloadPhase.FAILED, manifest, partial.length(),
                failureMessage(failure))
            persistFailure(manifest)
        } finally {
            connection?.disconnect()
            connection = null
        }
    }

    private fun kotlin.coroutines.CoroutineContext.isActiveForTransfer(): Boolean = this[Job]?.isActive != false
    private fun failureMessage(failure: Exception): String = when (failure) {
        is UpdateVerificationException, is UpdateTransferException -> failure.message ?: "下载失败，请重试"
        else -> "下载失败，请确认网络与存储空间后重试"
    }
    private fun partialFile(manifest: UpdateManifest) = File(partialDir, "${manifest.versionCode}.part")
    private fun completedFile(manifest: UpdateManifest) = File(verifiedDir, "${manifest.versionCode}.apk")

    @Synchronized
    private fun saveTask(manifest: UpdateManifest, phase: UpdateDownloadPhase = UpdateDownloadPhase.PAUSED) {
        metadata.baseFile.parentFile?.mkdirs()
        val properties = Properties().apply {
            setProperty("manifest", manifest.encode())
            setProperty("automatic", automatic.toString())
            setProperty("phase", phase.name)
            if (phase == UpdateDownloadPhase.FAILED) {
                setProperty("message", state.value.message.orEmpty())
            }
        }
        val stream = metadata.startWrite()
        try {
            properties.store(stream, null)
            metadata.finishWrite(stream)
        } catch (failure: Exception) {
            metadata.failWrite(stream)
            throw failure
        }
    }

    private fun persistFailure(manifest: UpdateManifest) {
        // Disk failure is a download error too; never throw it into the application's UI coroutine.
        try {
            saveTask(manifest, UpdateDownloadPhase.FAILED)
        } catch (_: Exception) {
            // Keep the in-memory FAILED state if task persistence is unavailable.
        }
    }

    private suspend fun restore() {
        var restoredManifest: UpdateManifest? = null
        try {
            val properties = Properties().apply { metadata.openRead().use { load(it) } }
            val manifest = UpdateManifest.parse(properties.getProperty("manifest"))
            if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                clearFiles()
                return
            }
            restoredManifest = manifest
            automatic = properties.getProperty("automatic") == "true"
            if (properties.getProperty("phase") == UpdateDownloadPhase.FAILED.name) {
                mutableState.value = UpdateDownloadState(UpdateDownloadPhase.FAILED, manifest,
                    partialFile(manifest).length(), properties.getProperty("message", "下载失败，请手动重试"))
                return
            }
            val file = completedFile(manifest)
            if (file.isFile) {
                verifier.verify(file, manifest)
                mutableState.value = UpdateDownloadState(UpdateDownloadPhase.READY, manifest, manifest.size)
            } else {
                mutableState.value = UpdateDownloadState(UpdateDownloadPhase.PAUSED, manifest,
                    partialFile(manifest).length(), "返回应用后继续下载")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val manifest = restoredManifest
            if (manifest == null) {
                clearFiles()
                mutableState.value = UpdateDownloadState()
            } else {
                if (failure is UpdateVerificationException) {
                    partialFile(manifest).delete()
                    completedFile(manifest).delete()
                }
                mutableState.value = UpdateDownloadState(UpdateDownloadPhase.FAILED, manifest,
                    partialFile(manifest).length(), "无法恢复安装包，请手动重试")
                persistFailure(manifest)
            }
        }
    }

    private fun clearFiles() {
        partialDir.listFiles()?.forEach { it.delete() }
        verifiedDir.listFiles()?.forEach { it.delete() }
        metadata.delete()
    }
}
