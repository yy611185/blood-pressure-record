package com.example.bloodpressurerecord.update

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UpdatePlatformActions(
    val install: () -> Unit,
    val openReleases: (String) -> Unit
)

/** 只有用户点击安装才进入来源授权；安装器返回不等于升级成功。 */
@Composable
fun rememberUpdatePlatformActions(
    viewModel: UpdateViewModel,
    installer: UpdateInstaller,
    promptAllowed: Boolean,
    snackbar: SnackbarHostState
): UpdatePlatformActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionPending by rememberSaveable { mutableStateOf(false) }
    var installRequested by rememberSaveable { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        scope.launch {
            snackbar.showSnackbar("已返回应用；如果尚未完成安装，可再次点击安装。")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionPending) {
            permissionPending = false
            if (installer.canInstallPackages()) {
                installRequested = true
            } else {
                scope.launch {
                    snackbar.showSnackbar("未获得安装来源授权，更新包已保留，可稍后重试。")
                }
            }
        }
    }

    LaunchedEffect(installRequested, promptAllowed) {
        if (installRequested && promptAllowed && !preparing) {
            installRequested = false
            preparing = true
            try {
                val manifest = viewModel.uiState.value.download.manifest
                    ?: error("没有可安装的更新包，请重新下载。")
                val file = viewModel.verifiedFile()
                    ?: error("更新包尚未通过校验，请重新下载。")
                val intent = installer.installIntent(file, manifest)
                installLauncher.launch(intent)
                viewModel.dismissDialog()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbar.showSnackbar(error.message ?: "无法打开系统安装器，请稍后重试。")
            } finally {
                preparing = false
            }
        }
    }

    return UpdatePlatformActions(
        install = {
            if (!preparing && !permissionPending && !installRequested) {
                if (installer.canInstallPackages()) {
                    installRequested = true
                } else {
                    try {
                        permissionPending = true
                        permissionLauncher.launch(installer.permissionIntent())
                    } catch (_: Exception) {
                        permissionPending = false
                        scope.launch { snackbar.showSnackbar("无法打开安装来源设置，请在系统设置中授权。") }
                    }
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
