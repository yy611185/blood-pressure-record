package com.example.bloodpressurerecord.domain.report

import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReportBuilderTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val date = LocalDate.of(2026, 10, 11)
    private val period = ReportPeriod(date.minusDays(29), date, zone)
    private val now = date.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

    @Test fun `没有测量或只有周期外记录时不生成报告`() {
        assertNull(build())
        assertNull(build(records = listOf(measurement("outside", date.plusDays(1)))))
    }

    @Test fun `保存代表值等权平均不按天加权也不重算历史策略`() {
        val records = listOf(
            measurement("a", date.minusDays(1), systolic = 100, diastolic = 60, pulse = null),
            measurement("b", systolic = 120, diastolic = 80, pulse = 60).copy(averageStrategy = AverageStrategy.DISCARD_FIRST),
            measurement("c", hour = 18, systolic = 180, diastolic = 100, pulse = 81)
        )
        val report = requireNotNull(build(records))
        assertEquals(ReportAverage(133, 80, 71), report.average)
        assertEquals(ReportAverage(110, 70, 60), report.morningAverage)
        assertEquals(ReportAverage(180, 100, 81), report.eveningAverage)
        assertEquals(2, report.validDays)
        assertEquals(3, report.measurementCount)
        assertTrue(report.insufficientData)
        assertEquals(30, report.days.size)
        assertEquals(CategoryCalculator.calculate(150, 90), report.days.last().category)
        assertEquals(1, report.categoryDays[BloodPressureCategory.STAGE1])
        assertEquals(2, report.categoryDays.values.sum())
        assertEquals(AverageStrategy.DISCARD_FIRST, report.days.last().morning.single().averageStrategy)
    }

    @Test fun `本地早晚边界和其他时段全部参与平均`() {
        val records = listOf(
            measurement("midnight", hour = 0, systolic = 200),
            measurement("before", hour = 4, minute = 59),
            measurement("first", hour = 5),
            measurement("last", hour = 11, minute = 59),
            measurement("noon", hour = 12),
            measurement("before-evening", hour = 17, minute = 59),
            measurement("evening", hour = 18),
            measurement("late", hour = 23, minute = 59)
        )
        val report = requireNotNull(build(records))
        assertEquals(listOf("first", "last"), report.days.last().morning.map { it.id })
        assertEquals(listOf("evening", "late"), report.days.last().evening.map { it.id })
        assertEquals(4, report.days.last().other.size)
        assertEquals(8, report.measurementCount)
        assertEquals(130, report.average.systolic)
        assertEquals(120, report.days.last().systolicMax)
        assertEquals(120, report.days.last().systolicMin)
    }

    @Test fun `只早只晚和三天数据不足边界`() {
        val onlyMorning = requireNotNull(build(listOf(measurement("am"))))
        assertEquals(ReportAverage(null, null, null), onlyMorning.eveningAverage)
        assertTrue(onlyMorning.insufficientData)
        val onlyEvening = requireNotNull(build(listOf(measurement("pm", hour = 18))))
        assertEquals(ReportAverage(null, null, null), onlyEvening.morningAverage)
        val three = requireNotNull(build((0..2).map { measurement("$it", date.minusDays(it.toLong())) }))
        assertFalse(three.insufficientData)
        assertEquals("测量天数较少，统计仅供参考", ReportPolicy.INSUFFICIENT_DATA)
    }

    @Test fun `周期首时刻包含而末日次日零点排除`() {
        val start = measurement("start").copy(measuredAt = period.startInclusive)
        val end = measurement("end").copy(measuredAt = period.endExclusive - 1)
        val outside = measurement("outside").copy(measuredAt = period.endExclusive)
        val before = measurement("before").copy(measuredAt = period.startInclusive - 1)
        val report = requireNotNull(build(listOf(start, end, outside, before)))
        assertEquals(2, report.measurementCount)
        assertEquals("start", report.days.first().other.single().id)
        assertEquals("end", report.days.last().evening.single().id)
    }

    @Test fun `场景不猜测且隐私在报告模型中被移除`() {
        val snapshot = ReportSnapshot(
            listOf(measurement("home").copy(scene = "家测", note = "私人备注"),
                measurement("clinic", hour = 18).copy(scene = "诊室", note = "另一备注")),
            ReportUserProfile("私人姓名", 70),
            listOf(ReportMedication(1, "私人药名", "10 mg", true, emptyList()))
        )
        val report = requireNotNull(ReportBuilder.build(snapshot, period, ReportOptions(false, false, false), now))
        assertNull(report.profile)
        assertTrue(report.notes.isEmpty())
        assertTrue(report.medications.isEmpty())
        assertTrue(report.days.flatMap { it.morning + it.evening + it.other }.all { it.note == null })
        assertEquals(2, report.measurementCount)
        assertEquals("未区分测量场景，分级与参考线采用诊室标准", ReportPolicy.SCENE_NOTICE)
        val identified = requireNotNull(ReportBuilder.build(snapshot, period, ReportOptions(true), now))
        assertEquals("私人姓名", identified.profile?.name)
        assertEquals(2, identified.notes.size)
        assertFalse(ReportOptions().includeIdentity)
    }

    @Test fun `用药按保存日期统计停用时间打卡且不推断已删除药品或漏服`() {
        val medications = listOf(
            ReportMedication(1, "药品一", "5 mg", true, listOf(
                ReportMedicationTime(10, "08:00", true), ReportMedicationTime(11, "20:00", false))),
            ReportMedication(2, "药品二", "10 mg", false, emptyList())
        )
        val logs = listOf(
            ReportIntake(1, 10, date.toEpochDay(), 0),
            ReportIntake(1, 11, date.toEpochDay(), Long.MAX_VALUE),
            ReportIntake(1, 10, period.startDate.minusDays(1).toEpochDay(), now),
            ReportIntake(2, 20, date.toEpochDay(), now),
            ReportIntake(999, 999, date.toEpochDay(), now)
        )
        val report = requireNotNull(ReportBuilder.build(
            ReportSnapshot(listOf(measurement("m")), medications = medications, intakes = logs),
            period, ReportOptions(), now
        ))
        assertEquals(2, report.medications.size)
        assertEquals(2, report.medications.first().intakeCount)
        assertEquals(listOf("08:00"), report.medications.first().activeTimes)
        assertFalse(report.medications.last().enabled)
        assertEquals(1, report.medications.last().intakeCount)
    }

    @Test fun `同一epoch在不同时区归日不同`() {
        val utc = ZoneId.of("UTC")
        // Taipei 02:00 is 18:00 on the previous UTC date, within the existing evening window.
        val instant = date.atTime(2, 0).atZone(zone).toInstant().toEpochMilli()
        val record = measurement("cross").copy(measuredAt = instant)
        val utcReport = requireNotNull(ReportBuilder.build(
            ReportSnapshot(listOf(record)), ReportPeriod(date.minusDays(1), date, utc), ReportOptions(), now))
        assertEquals("cross", utcReport.days.first().evening.single().id)
        val localReport = requireNotNull(build(listOf(record)))
        assertEquals("cross", localReport.days.last().other.single().id)
    }

    private fun build(records: List<ReportMeasurement> = emptyList()) =
        ReportBuilder.build(ReportSnapshot(records), period, ReportOptions(), now)

    private fun measurement(
        id: String, day: LocalDate = date, hour: Int = 8, minute: Int = 0,
        systolic: Int = 120, diastolic: Int = 80, pulse: Int? = 70
    ) = ReportMeasurement(id, day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli(), systolic, diastolic, pulse)
}
