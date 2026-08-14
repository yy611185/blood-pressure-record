package com.example.bloodpressurerecord.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.LifecycleCameraController
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton

/** 拍照识别取景页：快门/音量键拍照，逐张识别，最多 3 组。 */
@Composable
fun ScanCameraScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onEnterReview: () -> Unit,
    onManualEntry: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showExitDialog by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val guidePaddingPx = with(density) { 40.dp.toPx() }

    val cameraController = remember { LifecycleCameraController(context) }

    DisposableEffect(Unit) {
        onDispose { cameraController.unbind() }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        } else {
            cameraController.unbind()
        }
    }

    DisposableEffect(Unit) {
        ScanCameraActive.isActive = true
        onDispose { ScanCameraActive.isActive = false }
    }

    val capture = {
        if (state.phase == ScanPhase.Camera) {
            cameraController.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val upright = image.toUprightBitmap()
                            val framed = upright.cropToGuideFrame(
                                previewWidth = previewView?.width ?: 0,
                                previewHeight = previewView?.height ?: 0,
                                horizontalPaddingPx = guidePaddingPx
                            )
                            // 识别输入用完整摆正图：预处理内部有屏幕定位（locateLcd），
                            // 且不依赖“预览视图坐标→照片坐标”的映射，真机上该映射
                            // 存在 FILL_CENTER/cropRect 偏差，曾导致裁掉高压行或裁偏。
                            // 引导框裁剪结果只用于确认页缩略图。
                            viewModel.onPhotoCaptured(upright, framed)
                        } catch (error: Throwable) {
                            viewModel.onCaptureError(error.message ?: "无法读取相机画面")
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        viewModel.onCaptureError(exception.message ?: "拍照失败")
                    }
                }
            )
        }
    }
    val captureState by rememberUpdatedState(capture)
    LaunchedEffect(Unit) {
        ScanVolumeKeyBus.events.collect { captureState() }
    }

    val exit = {
        if (state.groups.isNotEmpty()) showExitDialog = true else onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("需要相机权限才能拍照识别。", color = Color.White)
                AppPrimaryButton(
                    text = "去授权",
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
                AppSecondaryButton(text = "返回手动录入", onClick = onManualEntry)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = cameraController
                        previewView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 取景引导框（3:4，贴合血压计屏幕比例）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .aspectRatio(3f / 4f)
                    .align(Alignment.Center)
                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
            )

            // 顶部状态
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { exit() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Text(
                    text = if (state.retakeGroupNumber != null) {
                        "重拍第 ${state.retakeGroupNumber} 组"
                    } else {
                        "第 ${state.currentGroupNumber} 组"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "已拍 ${state.groups.size}/3",
                    color = Color.White,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            // 识别中遮罩
            if (state.phase == ScanPhase.Processing) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("正在识别...", color = Color.White)
                }
            }

            // 底部控制
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.phase == ScanPhase.Camera && state.message.isNotBlank()) {
                    Surface(
                        color = if (state.messageIsError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            state.message,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (state.phase == ScanPhase.Camera && state.messageIsError && state.groups.isEmpty()) {
                    AppSecondaryButton(
                        text = "改为手动输入",
                        onClick = onManualEntry
                    )
                }
                val currentSlot = state.retakeGroupNumber ?: state.currentGroupNumber
                val recognized = state.groups.firstOrNull { it.groupNumber == currentSlot }
                    ?.isRecognized == true
                if (recognized && state.phase == ScanPhase.Camera) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppSecondaryButton(
                            text = "重拍",
                            onClick = { viewModel.retakeGroup(currentSlot) }
                        )
                        if (state.groups.size < 3) {
                            AppSecondaryButton(
                                text = "继续拍下一组",
                                onClick = { viewModel.continueCapturing() }
                            )
                        }
                        AppPrimaryButton(
                            text = "完成",
                            onClick = onEnterReview
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .border(3.dp, Color.White, CircleShape)
                            .clickable(enabled = state.phase == ScanPhase.Camera) { capture() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                    if (state.groups.isNotEmpty() && state.phase == ScanPhase.Camera) {
                        TextButton(onClick = onEnterReview) {
                            Text("完成并确认", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("放弃本次识别？") },
            text = { Text("已拍的 ${state.groups.size} 组识别结果不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onBack()
                }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("继续拍摄") }
            }
        )
    }
}
