package com.example.bloodpressurerecord.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ClearAllDataResultTest {
    @Test
    fun databaseFailureDoesNotClaimThatDataWasCleared() {
        val result = ClearAllDataResult(
            databaseCleared = false,
            settingsCleared = false,
            remindersRescheduled = false,
            widgetRefreshed = false,
            warnings = listOf("database failure")
        )

        assertEquals("清空失败：本地数据未完整删除，请重试。", result.toUserMessage())
    }

    @Test
    fun partialFailureWarnsAfterDatabaseWasCleared() {
        val result = ClearAllDataResult(
            databaseCleared = true,
            settingsCleared = false,
            remindersRescheduled = true,
            widgetRefreshed = false,
            warnings = listOf("settings reset failed", "widget refresh failed")
        )

        assertEquals("健康记录已删除，但部分设置未能重置。", result.toUserMessage())
    }
}
