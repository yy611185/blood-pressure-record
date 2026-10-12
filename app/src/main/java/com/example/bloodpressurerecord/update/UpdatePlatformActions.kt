package com.example.bloodpressurerecord.update

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UpdatePlatformActions(
    val install: () -> Unit,
    val openReleases: (String) -> Unit
)

private const val INSTALL_GUIDANCE = "更新包已保留。请在系统设置中允许本应用安装外部来源应用。" +
    "\n\n小米：安全 → 纯净模式，检查安装拦截开关。" +
    "\n华为/荣耀：更多安全设置 → 外部来源应用检查，检查拦截开关。" +
    "\nOPPO/vivo：安装外部来源应用，允许本应用安装。" +
    "\n\n完成设置后返回应用；如仍未开始安装，可再次点击“安装更新”。"

/** 只有用户点击安装才进入来源授权；安装器返回不等于升级成功。 */
@Composable
fun rememberUpdatePlatformActions(
    viewModel: UpdateViewModel,
    installer: UpdateInstaller,
    promptAllowed: Boolean,
    snackbar: SnackbarHostState
): UpdatePlatformActions {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var permissionPending by rememberSaveable { mutableStateOf(false) }
    var installRequested by rememberSaveable { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    // These record an explicit user continuation, never disable another install attempt.
    var awaitingPermissionReturn by rememberSaveable { mutableStateOf(false) }
    var continueAfterPermission by rememberSaveable { mutableStateOf(false) }
    var guidanceMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val latestPromptAllowed by rememberUpdatedState(promptAllowed)
    val uiState by viewModel.uiState.collectAsState()
    var previousPhase by remember { mutableStateOf(uiState.download.phase) }

    fun attemptState() = InstallAttemptState(preparing, permissionPending, installRequested)

    fun applyAttemptState(state: InstallAttemptState) {
        preparing = state.preparing
        permissionPending = state.permissionPending
        installRequested = state.installRequested
    }

    fun resetRequests() = applyAttemptState(resetInstallRequests(attemptState()))

    val handlePermissionReturn by rememberUpdatedState<() -> Unit> {
        if (awaitingPermissionReturn) {
            awaitingPermissionReturn = false
            resetRequests()
            if (installer.canInstallPackages()) {
                // ON_START may still be recomposing the foreground prompt gate.
                continueAfterPermission = true
            } else {
                continueAfterPermission = false
                guidanceMessage = "未获得安装来源授权。\n\n$INSTALL_GUIDANCE"
            }
        }
    }
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        resetRequests()
        scope.launch {
            snackbar.showSnackbar("已返回应用；如果尚未完成安装，可再次点击安装。")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Results can arrive before ON_RESUME. Consume once, after its uniform reset.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            handlePermissionReturn()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resetRequests()
                handlePermissionReturn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.download.phase) {
        val phase = uiState.download.phase
        if (phase == UpdateDownloadPhase.READY && previousPhase != UpdateDownloadPhase.READY) {
            resetRequests()
        }
        previousPhase = phase
    }

    LaunchedEffect(continueAfterPermission, promptAllowed, preparing) {
        if (continueAfterPermission && promptAllowed && !preparing) {
            continueAfterPermission = false
            installRequested = true
        }
    }

    LaunchedEffect(installRequested, promptAllowed) {
        if (installRequested) {
            preparing = true
            try {
                if (!latestPromptAllowed) {
                    guidanceMessage = "当前页面暂不能打开安装器，请返回更新页面后再次点击“安装更新”。"
                    return@LaunchedEffect
                }
                if (!installer.canInstallPackages()) {
                    val intent = installer.permissionIntent()
                    permissionPending = true
                    awaitingPermissionReturn = true
                    try {
                        permissionLauncher.launch(intent)
                    } catch (error: Exception) {
                        awaitingPermissionReturn = false
                        throw error
                    }
                    return@LaunchedEffect
                }
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
                guidanceMessage = "${error.message ?: "无法打开系统安装器，请稍后重试。"}\n\n$INSTALL_GUIDANCE"
            } finally {
                applyAttemptState(finishInstallAttempt(attemptState()))
            }
        }
    }

    guidanceMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { guidanceMessage = null },
            title = { Text("安装更新指引") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { guidanceMessage = null }) { Text("知道了") }
            }
        )
    }

    return UpdatePlatformActions(
        install = {
            if (canAttemptInstall(attemptState(), latestPromptAllowed)) {
                continueAfterPermission = false
                guidanceMessage = null
                installRequested = true
            } else if (!latestPromptAllowed && !preparing) {
                resetRequests()
                guidanceMessage = "当前页面暂不能打开安装器，请返回更新页面后再次点击“安装更新”。"
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
