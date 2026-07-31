package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.AverageResult
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.model.ReadingValue

data class MeasurementDerivedValues(
    val average: AverageResult,
    val category: BloodPressureCategory,
    val containsHighRiskReading: Boolean
)

/**
 * 从原始读数生成全部派生事实的领域层唯一入口。
 *
 * 平均值和分级随 [strategy] 变化；高风险判断始终检查全部原始读数。
 */
object MeasurementDerivation {
    fun derive(
        readings: List<ReadingValue>,
        strategy: AverageStrategy = AverageStrategy.ALL
    ): MeasurementDerivedValues {
        val average = AverageCalculator.calculate(readings, strategy)
        return MeasurementDerivedValues(
            average = average,
            category = CategoryCalculator.calculate(
                average.avgSystolic,
                average.avgDiastolic
            ),
            containsHighRiskReading = BloodPressureRules.containsHighRiskReading(
                readings,
                average
            )
        )
    }
}
