package com.example.bloodpressurerecord.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementSessionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MeasurementSessionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.measurementSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_and_query_session_with_readings() = runBlocking {
        val session = MeasurementSessionEntity(
            id = "session-1",
            measuredAt = 1000L,
            scene = "居家安静",
            note = "早餐前",
            symptomsJson = "[\"头晕\"]",
            avgSystolic = 132,
            avgDiastolic = 84,
            avgPulse = 72,
            category = "STAGE1",
            containsHighRiskReading = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val readings = listOf(
            MeasurementReadingEntity(
                id = "r1",
                sessionId = "session-1",
                orderIndex = 1,
                systolic = 130,
                diastolic = 82,
                pulse = 70
            ),
            MeasurementReadingEntity(
                id = "r2",
                sessionId = "session-1",
                orderIndex = 2,
                systolic = 134,
                diastolic = 86,
                pulse = 74
            )
        )

        dao.insertSessionWithReadings(session, readings)
        val loaded = dao.getSessionWithReadings("session-1")

        assertNotNull(loaded)
        assertEquals("session-1", loaded?.session?.id)
        assertEquals(2, loaded?.readings?.size)
        assertEquals(1, loaded?.readings?.first()?.orderIndex)
    }

    @Test
    fun trend_query_returns_lightweight_rows_in_time_order_and_range() = runBlocking {
        val first = session(id = "early", measuredAt = 1_000L, systolic = 120)
        val second = session(id = "late", measuredAt = 3_000L, systolic = 140)
        dao.insertSessionWithReadings(second, emptyList())
        dao.insertSessionWithReadings(first, emptyList())

        val rows = dao.getTrendPoints(startInclusive = 500L, endExclusive = 2_000L)

        assertEquals(listOf("early"), rows.map { it.id })
        assertEquals(120, rows.first().avgSystolic)
        assertTrue(rows.first().measuredAt < second.measuredAt)
    }

    @Test
    fun natural_day_query_uses_end_exclusive_boundary() = runBlocking {
        dao.insertSessionWithReadings(session("last-millis", 1_999L, 120), emptyList())
        dao.insertSessionWithReadings(session("next-midnight", 2_000L, 122), emptyList())

        val rows = dao.getSessionsWithReadingsInRange(
            startInclusive = 1_000L,
            endExclusive = 2_000L
        )

        assertEquals(listOf("last-millis"), rows.map { it.session.id })
    }

    @Test
    fun calendar_summary_query_only_returns_visible_month_lightweight_rows() = runBlocking {
        dao.insertSessionWithReadings(session("before", 999L, 118), emptyList())
        dao.insertSessionWithReadings(session("inside-1", 1_000L, 120), emptyList())
        dao.insertSessionWithReadings(
            session("inside-2", 1_999L, 190).copy(containsHighRiskReading = true),
            emptyList()
        )
        dao.insertSessionWithReadings(session("after", 2_000L, 122), emptyList())

        val rows = dao.observeCalendarSessionSummaries(1_000L, 2_000L).first()

        assertEquals(2, rows.size)
        assertEquals(listOf(1_000L, 1_999L), rows.map { it.measuredAt })
        assertTrue(rows.last().containsHighRiskReading)
    }

    @Test
    fun day_summary_query_returns_only_projection_with_stable_order() = runBlocking {
        dao.insertSessions(
            listOf(
                session("same-time-b", 1_500L, 130).copy(note = "b".repeat(60)),
                session("same-time-a", 1_500L, 120).copy(note = "short"),
                session("outside", 2_000L, 140)
            )
        )

        val rows = dao.observeSessionSummariesInRange(1_000L, 2_000L).first()

        assertEquals(listOf("same-time-a", "same-time-b"), rows.map { it.id })
        assertEquals("short", rows.first().noteSummary)
        assertEquals(40, rows.last().noteSummary?.length)
    }

    @Test
    fun period_statistics_are_computed_by_sql_without_integer_rounding_change() = runBlocking {
        dao.insertSessions(
            listOf(
                session("one", 1_000L, 121).copy(
                    avgDiastolic = 81,
                    containsHighRiskReading = false
                ),
                session("two", 1_500L, 122).copy(
                    avgDiastolic = 82,
                    containsHighRiskReading = true
                ),
                session("outside", 2_000L, 200)
            )
        )

        val statistics = dao.observePeriodStatistics(1_000L, 2_000L).first()

        assertEquals(2, statistics.recordCount)
        assertEquals(121, statistics.averageSystolic?.toInt())
        assertEquals(81, statistics.averageDiastolic?.toInt())
        assertEquals(122, statistics.highestSystolic)
        assertEquals(121, statistics.lowestSystolic)
        assertEquals(1, statistics.highRiskCount)
    }

    @Test
    fun range_queries_are_correct_for_zero_to_ten_thousand_rows() = runBlocking {
        listOf(0, 100, 1_000, 10_000).forEach { size ->
            dao.deleteAllSessions()
            if (size > 0) {
                dao.insertSessions(
                    (0 until size).map { index ->
                        session("stress-$size-$index", index.toLong(), 120 + (index % 20))
                    }
                )
            }

            val start = (size / 4).toLong()
            val end = (size * 3 / 4).toLong()
            val expected = (end - start).toInt()
            val rows = dao.observeCalendarSessionSummaries(start, end).first()
            val dayRows = dao.observeSessionSummariesInRange(start, end).first()

            assertEquals(expected, rows.size)
            assertEquals(expected, dayRows.size)
        }
    }

    @Test
    fun month_and_day_range_query_plan_uses_measured_at_index() {
        val queries = listOf(
            """
            SELECT measuredAt, highRiskAlertTriggered
            FROM measurement_sessions
            WHERE measuredAt >= ? AND measuredAt < ?
            """.trimIndent(),
            """
            SELECT id, measuredAt, avgSystolic, avgDiastolic
            FROM measurement_sessions
            WHERE measuredAt >= ? AND measuredAt < ?
            ORDER BY measuredAt ASC, id ASC
            """.trimIndent()
        )

        queries.forEach { sql ->
            database.openHelper.writableDatabase
                .query("EXPLAIN QUERY PLAN $sql", arrayOf(0L, Long.MAX_VALUE))
                .use { cursor ->
                    val detailIndex = cursor.getColumnIndexOrThrow("detail")
                    val details = buildList {
                        while (cursor.moveToNext()) add(cursor.getString(detailIndex))
                    }
                    assertTrue(
                        details.joinToString().contains("index_measurement_sessions_measuredAt")
                    )
                }
        }
    }

    private fun session(id: String, measuredAt: Long, systolic: Int): MeasurementSessionEntity {
        return MeasurementSessionEntity(
            id = id,
            measuredAt = measuredAt,
            scene = "晨起",
            note = null,
            symptomsJson = null,
            avgSystolic = systolic,
            avgDiastolic = 80,
            avgPulse = 70,
            category = "NORMAL",
            containsHighRiskReading = false,
            createdAt = measuredAt,
            updatedAt = measuredAt
        )
    }
}
