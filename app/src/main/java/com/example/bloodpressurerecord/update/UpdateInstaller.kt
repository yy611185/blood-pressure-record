package com.example.bloodpressurerecord.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class UpdateInstaller(private val context: Context, private val verifier: UpdateVerifier) {
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        return listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        ).firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?: throw IllegalStateException("系统无法打开安装来源设置，请手动授权。")
    }

    suspend fun installIntent(file: File, manifest: UpdateManifest): Intent {
        val root = File(context.filesDir, "updates/verified").canonicalFile
        if (file.canonicalFile.parentFile != root) throw UpdateVerificationException("安装包不在专用更新目录")
        verifier.verify(file, manifest)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (intent.resolveActivity(context.packageManager) == null) {
            throw IllegalStateException("系统未找到可用的 APK 安装器，请检查系统安装限制。")
        }
        return intent
    }
}
