package com.example.bloodpressurerecord.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.example.bloodpressurerecord"
private const val UI_TIMEOUT_MILLIS = 5_000L

internal fun MacrobenchmarkScope.startDashboard() {
    pressHome()
    startActivityAndWait()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.runCoreJourneys() {
    tapTextIfPresent("新增测量")
    device.pressBack()

    tapTextIfPresent("历史")
    repeat(12) {
        tapDescriptionIfPresent("上个月")
    }
    tapDescriptionIfPresent("下个月")

    // 有预置记录的性能测试设备会继续覆盖日期、详情与返回路径。
    val recordedDay = device.findObjects(By.clickable(true))
        .firstOrNull { node ->
            node.contentDescription?.contains("有") == true &&
                node.contentDescription?.contains("条记录") == true
        }
    recordedDay?.click()
    device.waitForIdle()
    device.findObjects(By.clickable(true))
        .firstOrNull { it.text?.contains("mmHg") == true }
        ?.click()
    device.pressBack()

    tapTextIfPresent("趋势")
    tapTextIfPresent("7天")
    tapTextIfPresent("30天")
    tapTextIfPresent("全部")
}

private fun MacrobenchmarkScope.tapTextIfPresent(text: String) {
    val selector = By.text(text)
    if (device.wait(Until.hasObject(selector), UI_TIMEOUT_MILLIS)) {
        device.findObject(selector).click()
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.tapDescriptionIfPresent(description: String) {
    val selector = By.desc(description)
    if (device.wait(Until.hasObject(selector), UI_TIMEOUT_MILLIS)) {
        device.findObject(selector).click()
        SystemClock.sleep(100)
    }
}
