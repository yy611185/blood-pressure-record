package com.example.bloodpressurerecord.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UpdatePlatformActions(
    val install: () -> Unit,
    val openReleases: (String) -> Unit
)

/**
 * A user-initiated installation must not depend on whether an update prompt is
 * currently allowed: opening OEM permission settings briefly backgrounds the
 * app and disables that prompt. Errors are shown inside the update UI instead
 * of a Snackbar behind its separate Dialog window.
 */
@Composable
fun rememberUpdatePlatformActions(
    viewModel: UpdateViewModel,
    installer: UpdateInstaller,
    snackbar: SnackbarHostState
): UpdatePlatformActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Neither flag survives process recreation: an old pending request must
    // never permanently disable the next tap.
    var permissionPending by remember { mutableStateOf(false) }
    var installRequested by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }

    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        scope.launch {
            snackbar.showSnackbar("已返回应用；如未完成升级，可再次点击安装更新。")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionPending) {
            permissionPending = false
            try {
                if (installer.canInstallPackages()) {
                    // Do not gate this on foreground or dialog visibility.
                    installRequested = true
                } else {
                    viewModel.setInstallFeedback(
                        inProgress = false,
                        message = "尚未获得“安装未知应用”授权。请允许血压记录安装应用后重试。"
                    )
                }
            } catch (error: Exception) {
                viewModel.setInstallFeedback(
                    inProgress = false,
                    message = error.message ?: "无法检查安装来源授权，请在系统设置中允许后重试。"
                )
            }
        }
    }

    LaunchedEffect(installRequested) {
        if (!installRequested || preparing) return@LaunchedEffect
        installRequested = false
        preparing = true
        try {
            val manifest = viewModel.uiState.value.download.manifest
                ?: error("没有可安装的更新包，请重新下载。")
            val file = viewModel.verifiedFile()
                ?: error("更新包尚未通过校验，请重新下载。")
            val intent = installer.installIntent(file, manifest)
            try {
                installLauncher.launch(intent)
            } catch (missing: ActivityNotFoundException) {
                // Some OEM ROMs only register ACTION_INSTALL_PACKAGE, not
                // the usual ACTION_VIEW handler for application APK files.
                installLauncher.launch(installer.alternativeInstallIntent(file))
            }
            viewModel.setInstallFeedback(inProgress = false)
            viewModel.dismissDialog()
        } catch (cancelled: CancellationException) {
            viewModel.setInstallFeedback(inProgress = false)
            throw cancelled
        } catch (error: Exception) {
            viewModel.setInstallFeedback(
                inProgress = false,
                message = error.message ?: "无法打开系统安装器，请稍后重试或使用文件管理器安装。"
            )
        } finally {
            preparing = false
        }
    }

    return UpdatePlatformActions(
        install = {
            if (!preparing && !permissionPending && !installRequested) {
                viewModel.setInstallFeedback(inProgress = true)
                try {
                    if (installer.canInstallPackages()) {
                        installRequested = true
                    } else {
                        permissionPending = true
                        permissionLauncher.launch(installer.permissionIntent())
                    }
                } catch (error: Exception) {
                    permissionPending = false
                    viewModel.setInstallFeedback(
                        inProgress = false,
                        message = error.message ?: "无法打开安装来源设置，请在系统设置中授权后重试。"
                    )
                }
            }
        },
        openReleases = { url ->
            try {
                UpdateUrls.validateRelease(url)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                scope.launch { snackbar.showSnackbar("无法打开版本发布页面。") }
            }
        }
    )
}
