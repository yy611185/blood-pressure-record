package com.example.bloodpressurerecord.ui.report

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.domain.report.ReportTemplate
import com.example.bloodpressurerecord.export.report.PdfReportRenderer
import com.example.bloodpressurerecord.export.report.ReportCache
import com.example.bloodpressurerecord.export.report.ReportShare
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppMotion
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReportPreviewScreen(
    state: ReportUiState,
    viewModel: ReportViewModel,
    cache: ReportCache,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit
) {
    val artifact = state.artifact ?: return
    val reducedMotion = LocalReducedMotion.current
    val context = LocalContext.current
    val share = remember(context, cache) { ReportShare(context, cache) }
    val renderer = remember(context) { PdfReportRenderer(context.getString(R.string.app_name)) }
    val renderedPages = remember(artifact.file) { mutableStateMapOf<Int, Boolean>() }
    var viewedSummaryPath by rememberSaveable { mutableStateOf<String?>(null) }
    var failedPreviewPath by rememberSaveable { mutableStateOf<String?>(null) }
    val canExport = state.canExport && viewedSummaryPath == artifact.file.absolutePath &&
        failedPreviewPath != artifact.file.absolutePath && renderedPages.values.all { it }
    val outputBusy = state.isSaving || state.selectingOutput != null

    // UX-07 & P2-07: 系统返回与顶栏返回在保存中保持一致门控，禁止穿透到 NavHost 取消复制或删除预览文件
    val returnToOptions = {
        if (viewModel.uiState.value.isSaving || viewModel.uiState.value.selectingOutput != null) {
            viewModel.showMessage("正在保存报告，请稍候…")
        } else {
            viewModel.returnToOptions()
        }
    }
    BackHandler(enabled = true, onBack = returnToOptions)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportShare.PDF_MIME_TYPE)
    ) { uri ->
        viewModel.finishOutputSelection(ReportOutputKind.PDF, uri?.let { destination ->
            { saved -> share.save(saved.file, destination) }
        })
    }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportShare.PNG_MIME_TYPE)
    ) { uri ->
        viewModel.finishOutputSelection(ReportOutputKind.IMAGE, uri?.let { destination ->
            { saved -> share.saveSummaryImage(saved.plan, renderer, destination) }
        })
    }
    fun chooseDestination(kind: ReportOutputKind, launch: () -> Unit) {
        if (!viewModel.beginOutputSelection(kind, artifact)) return
        try {
            launch()
        } catch (_: Exception) {
            viewModel.finishOutputSelection(kind, null)
            viewModel.showMessage("无法打开文件选择器，请重试。")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Box(Modifier.padding(top = statusBarTopPadding())) {
                AppTopBar(
                    title = if (state.options.template == ReportTemplate.GENERAL) {
                        "医生报告预览"
                    } else {
                        "${state.options.template.title}预览"
                    },
                    onBack = returnToOptions
                )
            }
        },
        bottomBar = {
            // UX-17 & 模块五：高频导出操作常驻底部安全区，无需滑过十几页 PDF 到底端
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(
                        visible = state.isSaving,
                        enter = AppMotion.expandVisibilityEnter(reducedMotion),
                        exit = AppMotion.expandVisibilityExit(reducedMotion)
                    ) {
                        if (!reducedMotion) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    AnimatedVisibility(
                        visible = state.message.isNotEmpty(),
                        enter = AppMotion.expandVisibilityEnter(reducedMotion),
                        exit = AppMotion.expandVisibilityExit(reducedMotion)
                    ) {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!canExport && !state.isSaving) {
                        Text(
                            "正在准备报告画面…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppPrimaryButton(
                        text = "分享 PDF（发给医生/家人）",
                        enabled = canExport,
                        onClick = {
                            if (!viewModel.uiState.value.canExport || viewModel.uiState.value.artifact !== artifact) return@AppPrimaryButton
                            try {
                                share.share(artifact.file, artifact.plan.report.template.title)
                            } catch (_: Exception) {
                                viewModel.showMessage("无法打开分享面板，请重试。")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val stackSecondary = LocalDensity.current.fontScale >= 1.45f || maxWidth < 340.dp
                        val saveFileButton: @Composable (Modifier) -> Unit = { mod ->
                            AppSecondaryButton(
                                text = if (state.isSaving) "保存中…" else "存文件",
                                enabled = canExport,
                                onClick = {
                                    chooseDestination(ReportOutputKind.PDF) {
                                        saveLauncher.launch(artifact.file.name)
                                    }
                                },
                                modifier = mod
                            )
                        }
                        val printButton: @Composable (Modifier) -> Unit = { mod ->
                            AppSecondaryButton(
                                text = "打印",
                                enabled = canExport,
                                onClick = {
                                    if (!viewModel.uiState.value.canExport || viewModel.uiState.value.artifact !== artifact) return@AppSecondaryButton
                                    val activity = context.reportActivity()
                                    try {
                                        if (activity == null) viewModel.showMessage("无法打开打印，请重新进入报告。")
                                        else share.print(activity, artifact.plan, renderer)
                                    } catch (_: Exception) {
                                        viewModel.showMessage("无法打开打印，请重试。")
                                    }
                                },
                                modifier = mod
                            )
                        }
                        val saveImageButton: @Composable (Modifier) -> Unit = { mod ->
                            AppSecondaryButton(
                                text = "存摘要图",
                                enabled = canExport,
                                onClick = {
                                    chooseDestination(ReportOutputKind.IMAGE) {
                                        imageLauncher.launch(share.summaryImageName(artifact.plan))
                                    }
                                },
                                modifier = mod
                            )
                        }
                        if (stackSecondary) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    saveFileButton(Modifier.weight(1f))
                                    printButton(Modifier.weight(1f))
                                }
                                saveImageButton(Modifier.fillMaxWidth())
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                saveFileButton(Modifier.weight(1f))
                                printButton(Modifier.weight(1f))
                                saveImageButton(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("${state.period.startDate} 至 ${state.period.endDate} · 共 ${artifact.plan.pageCount} 页")
                Text(
                    "请检查各页内容后导出。隐私选择仅用于本次报告；「存摘要图」仅保存第 1 页摘要，完整逐日明细请分享或保存 PDF。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrivacyOption(
                        label = "包含姓名与年龄",
                        checked = state.options.includeIdentity && state.hasProfileIdentity,
                        enabled = !outputBusy && state.hasProfileIdentity,
                        onChange = viewModel::setIncludeIdentity
                    )
                    PrivacyOption("包含备注", state.options.includeNotes, !outputBusy, viewModel::setIncludeNotes)
                    PrivacyOption("包含药品", state.options.includeMedications, !outputBusy, viewModel::setIncludeMedications)
                }
            }
            if (state.isGenerating || !state.hasCurrentPreview) {
                item {
                    if (state.isGenerating && !reducedMotion) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (state.isGenerating) "正在更新预览…" else "预览尚未更新，请重试。")
                    if (!state.isGenerating) OutlinedButton(onClick = viewModel::regeneratePreview) { Text("重试生成预览") }
                }
            }
            if (state.hasCurrentPreview && !state.isGenerating) {
                items((0 until artifact.plan.pageCount).toList(), key = { "${artifact.file.absolutePath}:$it" }) { index ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("第 ${index + 1} 页", style = MaterialTheme.typography.labelLarge)
                        PdfPagePreview(artifact.file, index) { ready ->
                            renderedPages[index] = ready
                            if (!ready) failedPreviewPath = artifact.file.absolutePath
                            else if (index == 0) viewedSummaryPath = artifact.file.absolutePath
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    enabled = !outputBusy,
                    onClick = returnToOptions,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("重新选择周期") }
            }
        }
    }
}

@Composable
private fun PrivacyOption(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(
            checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

private fun Context.reportActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) break
        current = base
    }
    return current as? Activity
}

private data class PagePreview(val bitmap: Bitmap? = null, val failed: Boolean = false)

@Composable
private fun PdfPagePreview(file: File, index: Int, onRendered: (Boolean) -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(PREVIEW_PAGE_RATIO)
            .background(androidx.compose.ui.graphics.Color.White)
    ) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceIn(1, PREVIEW_MAX_WIDTH)
        val preview by produceState(PagePreview(), file, index, width) {
            value = try {
                val bitmap = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            renderer.openPage(index).use { page ->
                                val height = (width.toDouble() * page.height / page.width).toInt().coerceAtLeast(1)
                                createBitmap(width, height).also {
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
        val imageAlphaState = animateFloatAsState(
            targetValue = if (bitmap != null) 1f else 0f,
            animationSpec = if (reducedMotion) AppMotion.springPress(true) else AppMotion.floatNormal,
            label = "pdf_page_alpha"
        )
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                "报告第 ${index + 1} 页",
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (reducedMotion) 1f else imageAlphaState.value }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (preview.failed) Text("此页预览失败，请重新生成。", color = MaterialTheme.colorScheme.error)
                else if (reducedMotion) Text("正在加载预览…") else CircularProgressIndicator()
            }
        }
    }
}

private const val PREVIEW_MAX_WIDTH = 1200
private const val PREVIEW_PAGE_RATIO = 595f / 842f
