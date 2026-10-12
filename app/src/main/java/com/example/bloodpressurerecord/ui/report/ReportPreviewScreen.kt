package com.example.bloodpressurerecord.ui.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.export.report.ReportCache
import com.example.bloodpressurerecord.export.report.ReportShare
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReportPreviewScreen(
    state: ReportUiState,
    viewModel: ReportViewModel,
    cache: ReportCache,
    onBack: () -> Unit
) {
    val artifact = state.artifact ?: return
    val context = LocalContext.current
    val share = remember(context, cache) { ReportShare(context, cache) }
    val renderedPages = remember(artifact.file) { mutableStateMapOf<Int, Boolean>() }
    val canExport = !state.isSaving && renderedPages[0] == true && renderedPages.values.all { it }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportShare.PDF_MIME_TYPE)
    ) { uri ->
        if (uri != null) viewModel.save { saved -> share.save(saved.file, uri) }
        else viewModel.showMessage("保存已取消。")
    }
    Column(Modifier.fillMaxSize().padding(top = statusBarTopPadding())) {
        AppTopBar(title = "医生报告预览", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("${state.period.startDate} 至 ${state.period.endDate} · 共 ${artifact.plan.pageCount} 页")
                Text(
                    "姓名与年龄：${if (state.options.includeIdentity) "包含" else "隐藏"}。请检查各页内容后导出。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            items((0 until artifact.plan.pageCount).toList(), key = { "${artifact.file.absolutePath}:$it" }) { index ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("第 ${index + 1} 页", style = MaterialTheme.typography.labelLarge)
                    PdfPagePreview(artifact.file, index) { renderedPages[index] = it }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.message.isNotEmpty()) Text(state.message)
                    Button(
                        enabled = canExport,
                        onClick = {
                            try {
                                share.share(artifact.file)
                            } catch (_: Exception) {
                                viewModel.showMessage("无法打开分享面板，请重试。")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("分享 PDF") }
                    Button(
                        enabled = canExport,
                        onClick = { saveLauncher.launch(artifact.file.name) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (state.isSaving) "正在保存…" else "保存到文件") }
                    OutlinedButton(
                        enabled = !state.isSaving,
                        onClick = viewModel::returnToOptions,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("重新生成") }
                }
            }
        }
    }
}

private data class PagePreview(val bitmap: Bitmap? = null, val failed: Boolean = false)

@Composable
private fun PdfPagePreview(file: File, index: Int, onRendered: (Boolean) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(PREVIEW_PAGE_RATIO).background(androidx.compose.ui.graphics.Color.White)
    ) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceIn(1, PREVIEW_MAX_WIDTH)
        val preview by produceState(PagePreview(), file, index, width) {
            value = try {
                val bitmap = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            renderer.openPage(index).use { page ->
                                val height = (width.toDouble() * page.height / page.width).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                }
                PagePreview(bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PagePreview(failed = true)
            }
        }
        val bitmap = preview.bitmap
        LaunchedEffect(bitmap, preview.failed) {
            if (bitmap != null || preview.failed) onRendered(bitmap != null)
        }
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), "报告第 ${index + 1} 页", Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (preview.failed) Text("此页预览失败，请重新生成。", color = MaterialTheme.colorScheme.error)
                else CircularProgressIndicator()
            }
        }
    }
}

private const val PREVIEW_MAX_WIDTH = 1200
private const val PREVIEW_PAGE_RATIO = 595f / 842f
