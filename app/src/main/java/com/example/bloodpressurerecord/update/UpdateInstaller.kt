package com.example.bloodpressurerecord.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class UpdateInstaller(private val context: Context, private val verifier: UpdateVerifier) {
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:" + context.packageName))

    suspend fun installIntent(file: File, manifest: UpdateManifest): Intent {
        val uri = verifiedUri(file)
        // Verify again immediately before passing the APK to Android.
        verifier.verify(file, manifest)
        return buildIntent(Intent.ACTION_VIEW, uri)
    }

    /** Used only if the normal APK MIME viewer does not exist on this ROM. */
    fun alternativeInstallIntent(file: File): Intent =
        buildIntent(Intent.ACTION_INSTALL_PACKAGE, verifiedUri(file))

    private fun verifiedUri(file: File): Uri {
        val root = File(context.filesDir, "updates/verified").canonicalFile
        if (file.canonicalFile.parentFile != root) {
            throw UpdateVerificationException("安装包不在专用更新目录")
        }
        return FileProvider.getUriForFile(context, context.packageName + ".update.files", file)
    }

    private fun buildIntent(action: String, uri: Uri): Intent = Intent(action).apply {
        if (action == Intent.ACTION_VIEW) {
            setDataAndType(uri, "application/vnd.android.package-archive")
        } else {
            data = uri
        }
        // Some Android/OEM package installers require a ClipData grant as
        // well as FLAG_GRANT_READ_URI_PERMISSION for a content:// APK.
        clipData = ClipData.newUri(context.contentResolver, "Verified update APK", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
