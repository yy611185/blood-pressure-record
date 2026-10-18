package com.example.bloodpressurerecord.update

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class UpdatePlatformActions(
    val download: () -> Unit,
    val openReleases: () -> Unit,
    val openDirectDownload: (() -> Unit)?
)

/** Opens a browser only. Returning from it never changes the installed-version check. */
@Composable
fun rememberUpdatePlatformActions(
    viewModel: UpdateViewModel,
    snackbar: SnackbarHostState
): UpdatePlatformActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val directUrl = state.manifest?.let(UpdateUrls::directDownload)

    fun reportFailure(message: String) {
        viewModel.onBrowserOpenFailed(message)
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun openBrowser(url: String, validate: () -> Unit, onOpened: () -> Unit = {}) {
        try {
            validate()
            // Resolve only browser apps, including when GitHub app links are enabled.
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
            }
            context.startActivity(intent)
            onOpened()
        } catch (_: IllegalArgumentException) {
            reportFailure("下载地址未通过安全检查，请使用 GitHub Releases 备用下载。")
        } catch (_: ActivityNotFoundException) {
            reportFailure("未找到可用的外部浏览器，请安装或启用浏览器后重试。")
        } catch (_: SecurityException) {
            reportFailure("无法打开外部浏览器，请检查系统设置后重试。")
        }
    }

    return UpdatePlatformActions(
        download = {
            viewModel.uiState.value.manifest?.let { manifest ->
                openBrowser(manifest.url,
                    validate = { UpdateUrls.validateApk(manifest.url, manifest.version, manifest.apk) },
                    onOpened = { viewModel.onBrowserOpened(manifest.versionCode) })
            }
        },
        openReleases = {
            // Fixed fallback remains reachable even when the manifest cannot be fetched or parsed.
            openBrowser(UpdateUrls.RELEASES_URL,
                validate = { UpdateUrls.validateRelease(UpdateUrls.RELEASES_URL) },
                onOpened = {
                    viewModel.uiState.value.manifest?.let { viewModel.onBrowserOpened(it.versionCode) }
                })
        },
        openDirectDownload = directUrl?.let { url ->
            {
                viewModel.uiState.value.manifest?.let { manifest ->
                    openBrowser(url,
                        validate = { UpdateUrls.validateDirectApk(url, manifest.version, manifest.apk) },
                        onOpened = { viewModel.onBrowserOpened(manifest.versionCode) })
                }
            }
        }
    )
}
