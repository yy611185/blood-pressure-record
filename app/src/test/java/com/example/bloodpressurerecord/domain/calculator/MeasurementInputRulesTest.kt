package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.ReadingValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementInputRulesTest {
    @Test
    fun `合法表单读数通过统一规则`() {
        assertNull(MeasurementInputRules.validateReading(ReadingValue(120, 80, 70)))
    }

    @Test
    fun `舒张压必须严格低于收缩压`() {
        assertEquals(
            ReadingValidationError.DIASTOLIC_NOT_LOWER_THAN_SYSTOLIC,
            MeasurementInputRules.validateReading(ReadingValue(120, 120, 70))
        )
    }

    @Test
    fun `组数使用统一上下限`() {
        assertEquals(
            ReadingSetValidationError.TOO_FEW_READINGS,
            MeasurementInputRules.validateReadings(listOf(ReadingValue(120, 80, 70)))
        )
        assertEquals(
            ReadingSetValidationError.TOO_MANY_READINGS,
            MeasurementInputRules.validateReadings(
                List(MeasurementInputRules.MAX_READING_COUNT + 1) {
                    ReadingValue(120, 80, 70)
                }
            )
        )
    }
}
