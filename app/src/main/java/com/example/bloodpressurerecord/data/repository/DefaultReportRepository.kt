package com.example.bloodpressurerecord.data.repository

import androidx.room.withTransaction
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.report.ReportIntake
import com.example.bloodpressurerecord.domain.report.ReportMeasurement
import com.example.bloodpressurerecord.domain.report.ReportMedication
import com.example.bloodpressurerecord.domain.report.ReportMedicationTime
import com.example.bloodpressurerecord.domain.report.ReportOptions
import com.example.bloodpressurerecord.domain.report.ReportPeriod
import com.example.bloodpressurerecord.domain.report.ReportSnapshot
import com.example.bloodpressurerecord.domain.report.ReportUserProfile
import org.json.JSONArray

class DefaultReportRepository(private val database: AppDatabase) : ReportRepository {
    override suspend fun loadSnapshot(period: ReportPeriod, options: ReportOptions): ReportSnapshot {
        // 只在读取时持有事务，统计、排版和 PDF 绘制均由调用方在事务外完成。
        return database.withTransaction {
            val measurements = database.measurementSessionDao()
                .getSessionsWithReadingsInRange(period.startInclusive, period.endExclusive)
                .map { row ->
                    ReportMeasurement(
                        id = row.session.id,
                        measuredAt = row.session.measuredAt,
                        systolic = row.session.avgSystolic,
                        diastolic = row.session.avgDiastolic,
                        pulse = row.session.avgPulse,
                        averageStrategy = AverageStrategy.entries.firstOrNull {
                            it.name == row.session.averageStrategy
                        } ?: AverageStrategy.ALL,
                        note = if (options.includeNotes) fullNote(row.session) else null,
                        // scene 是旧版自由文本或时段，不能作为家测/诊室依据。
                        scene = null
                    )
                }
            val profile = if (options.includeIdentity) {
                database.userProfileDao().getProfile()?.let { ReportUserProfile(it.name, it.age) }
            } else null
            val logs = if (options.includeMedications) {
                database.medicationDao().getLogsInRange(
                    period.startDate.toEpochDay(), period.endDate.plusDays(1).toEpochDay()
                )
            } else emptyList()
            val medicationIdsWithLogs = logs.mapTo(hashSetOf()) { it.medicationId }
            val medications = if (options.includeMedications) {
                database.medicationDao().getMedicationsWithTimes()
                    .filter { row ->
                        row.medication.id in medicationIdsWithLogs ||
                            (row.medication.enabled && row.medication.createdAt < period.endExclusive)
                    }
                    .map { row ->
                        ReportMedication(
                            id = row.medication.id,
                            name = row.medication.name,
                            dosage = row.medication.dosage,
                            enabled = row.medication.enabled,
                            times = row.times.sortedWith(compareBy({ it.timeText }, { it.id })).map {
                                ReportMedicationTime(it.id, it.timeText, it.active)
                            }
                        )
                    }
            } else emptyList()
            val retainedMedicationIds = medications.mapTo(hashSetOf()) { it.id }
            ReportSnapshot(
                measurements = measurements,
                profile = profile,
                medications = medications,
                intakes = logs.filter { it.medicationId in retainedMedicationIds }.map {
                    ReportIntake(it.medicationId, it.timeId, it.epochDay, it.takenAt)
                }
            )
        }
    }

    /** 保留完整补充情况；历史列表使用的 40 字摘要不能用于医生报告。 */
    private fun fullNote(session: MeasurementSessionEntity): String? {
        val parts = linkedMapOf<String, String>()
        fun add(text: String?, label: String = "") {
            val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return
            parts.putIfAbsent(value, label + value)
        }
        add(session.symptomNote, "不舒服：")
        add(session.factorNote, "可能影响血压：")
        add(session.note)
        if (!session.symptomsJson.isNullOrBlank()) {
            val symptoms = runCatching {
                val json = JSONArray(session.symptomsJson)
                List(json.length()) { json.getString(it) }
            }.getOrDefault(emptyList())
            symptoms.forEach { add(it) }
        }
        return parts.values.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
