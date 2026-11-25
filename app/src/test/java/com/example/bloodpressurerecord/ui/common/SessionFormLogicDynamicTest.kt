package com.example.bloodpressurerecord.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFormLogicDynamicTest {
    @Test
    fun validate_dynamic_readings_success() {
        val result = SessionFormLogic.validateAndBuildReadings(
            readings = listOf(
                SessionReadingInputUi("120", "80", "70"),
                SessionReadingInputUi("126", "82", "72"),
                SessionReadingInputUi("130", "84", "74"),
                SessionReadingInputUi("132", "86", "76")
            ),
            requiredCount = 2
        )
        assertNull(result.error)
        assertEquals(4, result.readings.size)
    }

    @Test
    fun derive_dynamic_readings_average_and_category() {
        val result = SessionFormLogic.recomputeDerived(
            readings = listOf(
                SessionReadingInputUi("150", "95", "80"),
                SessionReadingInputUi("145", "90", "82"),
                SessionReadingInputUi("140", "88", "84"),
                SessionReadingInputUi("138", "86", "80")
            ),
            requiredCount = 2
        )
        assertEquals(143, result.avgSystolic)
        assertEquals(90, result.avgDiastolic)
        // 中国指南：143/90 属 1 级高血压
        assertEquals("1级高血压", result.categoryLabel)
    }

    @Test
    fun derive_with_discard_first_strategy_ignores_first_group() {
        val result = SessionFormLogic.recomputeDerived(
            readings = listOf(
                SessionReadingInputUi("160", "100", "90"),
                SessionReadingInputUi("120", "78", "70"),
                SessionReadingInputUi("122", "78", "72")
            ),
            requiredCount = 2,
            strategy = com.example.bloodpressurerecord.domain.model.AverageStrategy.DISCARD_FIRST
        )
        assertEquals(121, result.avgSystolic)
        assertEquals(78, result.avgDiastolic)
        assertEquals("正常高值", result.categoryLabel)
    }

    @Test
    fun auto_advance_only_on_complete_valid_forward_input() {
        val sysPrefix = SessionFormLogic.SYSTOLIC_AUTO_ADVANCE_PREFIX
        val diaPrefix = SessionFormLogic.DIASTOLIC_AUTO_ADVANCE_PREFIX
        // 收缩压：3位有效值自动跳格；2位仅 > 30（不可能续输3位 ≤300）才跳；退格删除或中间插入绝不跳格
        assertTrue(SessionFormLogic.shouldAutoAdvanceField("12", "125", sysPrefix))
        assertFalse(SessionFormLogic.shouldAutoAdvanceField("1", "12", sysPrefix))
        assertTrue(SessionFormLogic.shouldAutoAdvanceField("8", "85", sysPrefix))
        assertFalse(SessionFormLogic.shouldAutoAdvanceField("125", "12", sysPrefix))
        assertFalse(SessionFormLogic.shouldAutoAdvanceField("125", "125", sysPrefix))
        assertFalse(SessionFormLogic.shouldAutoAdvanceField("25", "125", sysPrefix))

        // 舒张压：> 20 的2位或3位正向追加自动跳格；11（可能续输 110）或退格删除不跳格
        assertTrue(SessionFormLogic.shouldAutoAdvanceField("8", "80", diaPrefix))
        assertFalse(SessionFormLogic.shouldAutoAdvanceField("1", "11", diaPrefix))
        assertTrue(SessionFormLogic.shouldAutoAdvanceField("11", "110", diaPrefix))
        assertFalse(SessionFormLogic.shouldAutoAdvanceField("80", "8", diaPrefix))
    }

    @Test
    fun quick_note_tag_toggle_adds_and_removes_cleanly() {
        val added = SessionFormLogic.toggleQuickNoteTag("", "头晕")
        assertEquals("头晕", added)
        assertTrue(SessionFormLogic.containsQuickNoteTag(added, "头晕"))

        val second = SessionFormLogic.toggleQuickNoteTag(added, "乏力")
        assertEquals("头晕、乏力", second)
        assertTrue(SessionFormLogic.containsQuickNoteTag(second, "乏力"))

        val removed = SessionFormLogic.toggleQuickNoteTag(second, "头晕")
        assertEquals("乏力", removed)
        assertFalse(SessionFormLogic.containsQuickNoteTag(removed, "头晕"))
    }
}

