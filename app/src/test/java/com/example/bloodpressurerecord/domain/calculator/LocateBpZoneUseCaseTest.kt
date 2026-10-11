package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.BpAxisConfig
import com.example.bloodpressurerecord.domain.model.BpZone
import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import com.example.bloodpressurerecord.domain.model.BpZoneCuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocateBpZoneUseCaseTest {
    private val locate = LocateBpZoneUseCase()
    private val default = BpZoneConfig.Default

    @Test
    fun `119 over 75 is normal within the second equal segment`() {
        val position = requireNotNull(locate(119, 75, default))
        assertEquals(BpZone.NORMAL, position.zone)
        assertTrue(position.percent > 25f && position.percent < 50f)
        assertEquals(29f / 30f, position.offset, 0.0001f)
    }

    @Test
    fun `higher diastolic zone takes priority over normal systolic`() {
        val position = requireNotNull(locate(119, 85, default))
        assertEquals(BpZone.ELEVATED, position.zone)
        assertEquals(62.5f, position.percent, 0.0001f)
    }

    @Test
    fun `equal zones use the farther right reading on either axis`() {
        val systolicFurther = requireNotNull(locate(115, 65, default))
        assertEquals(BpZone.NORMAL, systolicFurther.zone)
        assertEquals(25f / 30f, systolicFurther.offset, 0.0001f)

        val diastolicFurther = requireNotNull(locate(95, 75, default))
        assertEquals(BpZone.NORMAL, diastolicFurther.zone)
        assertEquals(0.75f, diastolicFurther.offset, 0.0001f)
        assertEquals(43.75f, diastolicFurther.percent, 0.0001f)
    }

    @Test
    fun `exactly every systolic cut enters the higher zone at its left edge`() {
        // 等于分界点时必须归入右侧（更高一级），不是左侧区间末端。
        listOf(90 to BpZone.NORMAL, 120 to BpZone.ELEVATED, 140 to BpZone.HIGH)
            .forEach { (cut, zone) ->
                val position = requireNotNull(locate(cut, 1, default))
                assertEquals(zone, position.zone)
                assertEquals(0f, position.offset, 0f)
                assertEquals(zone.ordinal * 25f, position.percent, 0f)
            }
    }

    @Test
    fun `exactly every diastolic cut enters the higher zone at its left edge`() {
        // 同一边界规则也独立适用于舒张压。
        listOf(60 to BpZone.NORMAL, 80 to BpZone.ELEVATED, 90 to BpZone.HIGH)
            .forEach { (cut, zone) ->
                val position = requireNotNull(locate(1, cut, default))
                assertEquals(zone, position.zone)
                assertEquals(0f, position.offset, 0f)
                assertEquals(zone.ordinal * 25f, position.percent, 0f)
            }
    }

    @Test
    fun `values outside display bounds clamp marker to two and ninety eight percent`() {
        val below = requireNotNull(locate(1, 1, default))
        assertEquals(BpZone.LOW, below.zone)
        assertEquals(0f, below.offset, 0f)
        assertEquals(2f, below.percent, 0f)

        listOf(180 to 110, 181 to 111, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (sys, dia) ->
            val above = requireNotNull(locate(sys, dia, default))
            assertEquals(BpZone.HIGH, above.zone)
            assertEquals(1f, above.offset, 0f)
            assertEquals(98f, above.percent, 0f)
        }
    }

    @Test
    fun `non positive readings return null`() {
        listOf(0 to 75, 119 to 0, -1 to 75, 119 to -1, Int.MIN_VALUE to Int.MIN_VALUE)
            .forEach { (sys, dia) -> assertNull(locate(sys, dia, default)) }
    }

    @Test
    fun `changing configuration changes classification and marker`() {
        val custom = default.copy(sys = default.sys.copy(cuts = BpZoneCuts(80, 100, 150)))
        assertEquals(BpZone.NORMAL, locate(119, 75, default)?.zone)
        val changed = requireNotNull(locate(119, 75, custom))
        assertEquals(BpZone.ELEVATED, changed.zone)
        assertEquals(59.5f, changed.percent, 0.0001f)
    }

    @Test
    fun `config validation rejects unordered out of range and invalid bounds`() {
        val invalidAxes = listOf(
            BpAxisConfig(BpZoneCuts(90, 90, 140), 70, 180),
            BpAxisConfig(BpZoneCuts(120, 90, 140), 70, 180),
            BpAxisConfig(BpZoneCuts(69, 120, 140), 70, 180),
            BpAxisConfig(BpZoneCuts(90, 120, 181), 70, 180),
            BpAxisConfig(BpZoneCuts(90, 120, 140), 0, 180),
            BpAxisConfig(BpZoneCuts(90, 120, 140), 180, 180),
            BpAxisConfig(BpZoneCuts(90, 120, 140), 180, 70)
        )
        invalidAxes.forEach { axis ->
            assertFalse(axis.isValid())
            assertNull(locate(119, 75, default.copy(sys = axis)))
            assertNull(locate(119, 75, default.copy(dia = axis)))
        }
    }

    @Test
    fun `cuts at min and max are valid and zero width segments never divide by zero`() {
        val axis = BpAxisConfig(BpZoneCuts(70, 120, 180), 70, 180)
        val config = BpZoneConfig(axis, axis)
        assertTrue(config.isValid())
        val below = requireNotNull(locate(69, 69, config))
        assertEquals(BpZone.LOW, below.zone)
        assertEquals(2f, below.percent, 0f)
        val atMin = requireNotNull(locate(70, 70, config))
        assertEquals(BpZone.NORMAL, atMin.zone)
        assertEquals(0f, atMin.offset, 0f)
        val belowMax = requireNotNull(locate(179, 179, config))
        assertEquals(BpZone.ELEVATED, belowMax.zone)
        assertTrue(belowMax.offset.isFinite())
        val atMax = requireNotNull(locate(180, 180, config))
        assertEquals(BpZone.HIGH, atMax.zone)
        assertEquals(1f, atMax.offset, 0f)
        assertEquals(98f, atMax.percent, 0f)
    }

    @Test
    fun `large integer config calculates finite offsets without overflow`() {
        val axis = BpAxisConfig(BpZoneCuts(2, 3, 4), min = 1, max = Int.MAX_VALUE)
        val position = requireNotNull(locate(Int.MAX_VALUE - 1, 4, BpZoneConfig(axis, axis)))
        assertEquals(BpZone.HIGH, position.zone)
        assertTrue(position.offset.isFinite())
        assertTrue(position.offset in 0f..1f)
        assertEquals(98f, position.percent, 0f)
    }
}
