package com.example.bloodpressurerecord.ui.scan

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 取景页是否激活；MainActivity 据此决定是否把音量键转成快门。 */
object ScanCameraActive {
    @Volatile
    var isActive: Boolean = false
}

/** 音量键快门事件总线（MainActivity.dispatchKeyEvent 写入，取景页收集）。 */
object ScanVolumeKeyBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun emitShutter() {
        _events.tryEmit(Unit)
    }
}
