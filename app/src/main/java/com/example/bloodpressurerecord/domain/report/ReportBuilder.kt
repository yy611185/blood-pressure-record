package com.example.bloodpressurerecord.domain.report

import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.domain.calculator.SessionStatisticsCalculator
import java.time.Instant

object ReportBuilder {
    fun build(
        snapshot: ReportSnapshot, period: ReportPeriod, options: ReportOptions, generatedAtMillis: Long
    ): BpReport? {
        val records = snapshot.measurements.asSequence()
            .filter { it.measuredAt >= period.startInclusive && it.measuredAt < period.endExclusive }
            .sortedWith(compareBy<ReportMeasurement> { it.measuredAt }.thenBy { it.id })
            .map { if (options.includeNotes) it else it.copy(note = null) }.toList()
        if (records.isEmpty()) return null
        val grouped = records.groupBy { Instant.ofEpochMilli(it.measuredAt).atZone(period.zoneId).toLocalDate() }
        val days = (0 until period.dayCount).map { offset ->
            val date = period.startDate.plusDays(offset.toLong())
            val values = grouped[date].orEmpty()
            val morning = values.filter { SessionStatisticsCalculator.isMorning(it.measuredAt, period.zoneId) }
            val evening = values.filter { SessionStatisticsCalculator.isEvening(it.measuredAt, period.zoneId) }
            val other = values.filterNot {
                SessionStatisticsCalculator.isMorning(it.measuredAt, period.zoneId) ||
                    SessionStatisticsCalculator.isEvening(it.measuredAt, period.zoneId)
            }
            val average = average(values)
            val range = morning + evening
            ReportDay(
                date, average, morning, evening, other,
                average.systolic?.let { CategoryCalculator.calculate(it, requireNotNull(average.diastolic)) },
                range.minOfOrNull { it.systolic }, range.maxOfOrNull { it.systolic },
                range.minOfOrNull { it.diastolic }, range.maxOfOrNull { it.diastolic }
            )
        }
        val notes = records.mapNotNull { record ->
            record.note?.trim()?.takeIf { it.isNotEmpty() }?.let { record to it }
        }.mapIndexed { index, (record, text) ->
            ReportNote(index + 1, Instant.ofEpochMilli(record.measuredAt).atZone(period.zoneId).toLocalDate(), text, record.id)
        }
        val intakes = snapshot.intakes.filter {
            it.epochDay in period.startDate.toEpochDay()..period.endDate.toEpochDay()
        }
        val medications = if (options.includeMedications) snapshot.medications.map { medication ->
            ReportMedicationSummary(
                medication.id, medication.name, medication.dosage, medication.enabled,
                medication.times.filter { it.active }.map { it.timeText }.sorted(),
                intakes.count { it.medicationId == medication.id }
            )
        } else emptyList()
        return BpReport(
            period, generatedAtMillis, snapshot.profile.takeIf { options.includeIdentity }, average(records),
            average(days.flatMap { it.morning }), average(days.flatMap { it.evening }),
            grouped.size, records.size, days,
            days.mapNotNull { it.category }.groupingBy { it }.eachCount(), medications, notes
        )
    }

    private fun average(records: List<ReportMeasurement>): ReportAverage {
        val pressure = SessionStatisticsCalculator.pressureAverage(records, { it.systolic }, { it.diastolic })
        return ReportAverage(pressure?.first, pressure?.second, SessionStatisticsCalculator.pulseAverage(records) { it.pulse })
    }
}
