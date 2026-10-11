package com.example.bloodpressurerecord.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Platform package/signing identity precheck. The system installer makes the final cryptographic decision. */
class UpdateVerifier(private val context: Context) {
    suspend fun verify(file: File, manifest: UpdateManifest) = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() != manifest.size || manifest.size !in 1..UpdateDownloadSafety.MAX_BYTES) {
            throw UpdateVerificationException("安装包文件大小不一致")
        }
        if (!UpdateDownloadSafety.sha256(file).equals(manifest.sha256, ignoreCase = true)) {
            throw UpdateVerificationException("安装包 SHA-256 校验失败，请重新下载")
        }
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNATURES or
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
        @Suppress("DEPRECATION")
        val installed = manager.getPackageInfo(context.packageName, flags)
        if (installed.packageName != PACKAGE_NAME) throw UpdateVerificationException("仅正式安装的应用可使用更新安装")
        @Suppress("DEPRECATION")
        val candidate = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateVerificationException("无法读取或验证 APK 身份")
        val application = candidate.applicationInfo ?: throw UpdateVerificationException("APK 缺少应用信息")
        UpdateDownloadSafety.requirePackage(candidate.packageName, code(candidate), manifest.versionCode,
            code(installed), application.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            application.flags and ApplicationInfo.FLAG_TEST_ONLY != 0)
        val current = signers(installed)
        val next = signers(candidate)
        if (!UpdateDownloadSafety.acceptsSigningIdentity(current.first, current.second,
                next.first, next.second, Build.VERSION.SDK_INT >= 28, RELEASE_CERTIFICATE)) {
            throw UpdateVerificationException("安装包签名身份不符合当前正式应用")
        }
    }

    private fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

    private fun signers(info: PackageInfo): Pair<Set<String>, Set<String>> {
        if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: throw UpdateVerificationException("APK 没有可用的签名信息")
            val current = signing.apkContentsSigners?.map { digest(it.toByteArray()) }?.toSet().orEmpty()
            val history = if (signing.hasMultipleSigners()) emptySet() else
                signing.signingCertificateHistory?.map { digest(it.toByteArray()) }?.toSet().orEmpty()
            return current to history
        }
        @Suppress("DEPRECATION")
        val current = info.signatures?.map { digest(it.toByteArray()) }?.toSet().orEmpty()
        return current to emptySet()
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    companion object {
        const val PACKAGE_NAME = "com.yang.bloodpressure"
        const val RELEASE_CERTIFICATE = "46dfcbf4da054df8a8aca30e0df4df323161cbe4363a9eac7683a9da87d44012"
    }
}
