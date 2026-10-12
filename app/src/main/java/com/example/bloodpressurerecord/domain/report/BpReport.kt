package com.example.bloodpressurerecord.domain.report

import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import java.time.LocalDate

data class ReportUserProfile(val name: String?, val age: Int?)
data class ReportMeasurement(
    val id: String, val measuredAt: Long, val systolic: Int, val diastolic: Int, val pulse: Int?,
    val averageStrategy: AverageStrategy = AverageStrategy.ALL,
    val note: String? = null, val scene: String? = null
)
data class ReportMedicationTime(val id: Long, val timeText: String, val active: Boolean)
data class ReportMedication(
    val id: Long, val name: String, val dosage: String, val enabled: Boolean,
    val times: List<ReportMedicationTime>
)
data class ReportIntake(val medicationId: Long, val timeId: Long, val epochDay: Long, val takenAt: Long)
data class ReportSnapshot(
    val measurements: List<ReportMeasurement> = emptyList(),
    val profile: ReportUserProfile? = null,
    val medications: List<ReportMedication> = emptyList(),
    val intakes: List<ReportIntake> = emptyList()
)
data class ReportAverage(val systolic: Int?, val diastolic: Int?, val pulse: Int?)
data class ReportDay(
    val date: LocalDate, val average: ReportAverage,
    val morning: List<ReportMeasurement>, val evening: List<ReportMeasurement>,
    val other: List<ReportMeasurement>, val category: BloodPressureCategory?,
    val systolicMin: Int?, val systolicMax: Int?, val diastolicMin: Int?, val diastolicMax: Int?
)
data class ReportNote(val reference: Int, val date: LocalDate, val text: String, val measurementId: String)
data class ReportMedicationSummary(
    val id: Long, val name: String, val dosage: String, val enabled: Boolean,
    val activeTimes: List<String>, val intakeCount: Int
)
data class BpReport(
    val period: ReportPeriod, val generatedAtMillis: Long, val profile: ReportUserProfile?,
    val average: ReportAverage, val morningAverage: ReportAverage, val eveningAverage: ReportAverage,
    val validDays: Int, val measurementCount: Int, val days: List<ReportDay>,
    val categoryDays: Map<BloodPressureCategory, Int>,
    val medications: List<ReportMedicationSummary>, val notes: List<ReportNote>,
    val template: ReportTemplate = ReportTemplate.GENERAL
) {
    val insufficientData: Boolean get() = validDays < ReportPolicy.MIN_VALID_DAYS_FOR_STATS
}
