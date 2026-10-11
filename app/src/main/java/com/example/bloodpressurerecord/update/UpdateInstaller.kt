package com.example.bloodpressurerecord.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class UpdateInstaller(private val context: Context, private val verifier: UpdateVerifier) {
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"))

    suspend fun installIntent(file: File, manifest: UpdateManifest): Intent {
        val root = File(context.filesDir, "updates/verified").canonicalFile
        if (file.canonicalFile.parentFile != root) throw UpdateVerificationException("安装包不在专用更新目录")
        verifier.verify(file, manifest)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update.files", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
