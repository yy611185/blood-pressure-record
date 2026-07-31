package com.example.bloodpressurerecord.data.db

import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList<AutoMigrationSpec>(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_preservesSessionsAndAddsMeasuredAtIndex() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO measurement_sessions (
                    id, measuredAt, scene, note, symptomsJson,
                    avgSystolic, avgDiastolic, avgPulse, category,
                    highRiskAlertTriggered, createdAt, updatedAt
                ) VALUES (
                    'session-before-migration', 1000, '晨起', NULL, NULL,
                    128, 82, 70, 'STAGE1', 0, 1000, 1000
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        ).use { db ->
            db.query(
                "SELECT avgSystolic FROM measurement_sessions " +
                    "WHERE id = 'session-before-migration'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(128, cursor.getInt(0))
            }

            db.query("PRAGMA index_list('measurement_sessions')").use { cursor ->
                val nameColumn = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == "index_measurement_sessions_measuredAt") {
                        found = true
                    }
                }
                assertTrue(found)
            }
        }
    }

    @Test
    fun migrate3To4_recomputesRiskFromRawReadingsWithoutDataLoss() {
        helper.createDatabase(RISK_TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO measurement_sessions (
                    id, measuredAt, scene, note, symptomsJson,
                    avgSystolic, avgDiastolic, avgPulse, category,
                    highRiskAlertTriggered, createdAt, updatedAt
                ) VALUES (
                    'raw-high-risk', 2000, '晨起', 'keep-note', NULL,
                    150, 80, 71, 'STAGE2', 0, 2000, 2000
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO measurement_readings (
                    id, sessionId, orderIndex, systolic, diastolic, pulse
                ) VALUES
                    ('risk-reading-1', 'raw-high-risk', 1, 190, 90, 70),
                    ('risk-reading-2', 'raw-high-risk', 2, 110, 70, 72)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            RISK_TEST_DB,
            4,
            true,
            AppDatabase.MIGRATION_3_4
        ).use { db ->
            db.query(
                "SELECT highRiskAlertTriggered, note FROM measurement_sessions " +
                    "WHERE id = 'raw-high-risk'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals("keep-note", cursor.getString(1))
            }
            db.query(
                "SELECT COUNT(*) FROM measurement_readings WHERE sessionId = 'raw-high-risk'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate2To4_runsAllSupportedMigrations() {
        helper.createDatabase(CHAIN_TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO measurement_sessions (
                    id, measuredAt, scene, note, symptomsJson,
                    avgSystolic, avgDiastolic, avgPulse, category,
                    highRiskAlertTriggered, createdAt, updatedAt
                ) VALUES (
                    'chain-session', 3000, '睡前', NULL, NULL,
                    181, 90, 70, 'SEVERE', 0, 3000, 3000
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            CHAIN_TEST_DB,
            4,
            true,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        ).use { db ->
            db.query(
                "SELECT highRiskAlertTriggered FROM measurement_sessions WHERE id = 'chain-session'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate4To5_recomputesCategoryToChineseGuidelineAndInclusiveHighRisk() {
        helper.createDatabase(GUIDELINE_TEST_DB, 4).apply {
            // 旧 ACC/AHA 下 132/83 是 STAGE1；中国指南应改判为正常高值。
            execSQL(
                """
                INSERT INTO measurement_sessions (
                    id, measuredAt, scene, note, symptomsJson,
                    avgSystolic, avgDiastolic, avgPulse, category,
                    highRiskAlertTriggered, createdAt, updatedAt
                ) VALUES (
                    'acc-stage1', 4000, '晨起', 'keep', NULL,
                    132, 83, 70, 'STAGE1', 0, 4000, 4000
                )
                """.trimIndent()
            )
            // 恰好 180/120：旧规则（严格大于）不算高风险，新规则含边界应标记。
            execSQL(
                """
                INSERT INTO measurement_sessions (
                    id, measuredAt, scene, note, symptomsJson,
                    avgSystolic, avgDiastolic, avgPulse, category,
                    highRiskAlertTriggered, createdAt, updatedAt
                ) VALUES (
                    'boundary-180', 5000, '睡前', NULL, NULL,
                    180, 95, 70, 'SEVERE', 0, 5000, 5000
                )
                """.trimIndent()
            )
            // 偏低提示：85/55。
            execSQL(
                """
                INSERT INTO measurement_sessions (
                    id, measuredAt, scene, note, symptomsJson,
                    avgSystolic, avgDiastolic, avgPulse, category,
                    highRiskAlertTriggered, createdAt, updatedAt
                ) VALUES (
                    'low-bp', 6000, '晨起', NULL, NULL,
                    85, 55, 66, 'NORMAL', 0, 6000, 6000
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            GUIDELINE_TEST_DB,
            5,
            true,
            AppDatabase.MIGRATION_4_5
        ).use { db ->
            db.query(
                "SELECT category, highRiskAlertTriggered, note FROM measurement_sessions " +
                    "WHERE id = 'acc-stage1'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("HIGH_NORMAL", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals("keep", cursor.getString(2))
            }
            db.query(
                "SELECT category, highRiskAlertTriggered FROM measurement_sessions " +
                    "WHERE id = 'boundary-180'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("STAGE3", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            db.query(
                "SELECT category FROM measurement_sessions WHERE id = 'low-bp'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("LOW", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrate6To7_infersUnambiguousAverageStrategyWithoutChangingAverages() {
        helper.createDatabase(AVERAGE_STRATEGY_TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO measurement_sessions (
                    id, measuredAt, scene, note, symptomsJson,
                    avgSystolic, avgDiastolic, avgPulse, category,
                    highRiskAlertTriggered, createdAt, updatedAt
                ) VALUES
                    ('discard-first', 7000, '晨起', NULL, NULL,
                     120, 80, 60, 'HIGH_NORMAL', 0, 7000, 7000),
                    ('all-readings', 8000, '晨起', NULL, NULL,
                     130, 85, 70, 'HIGH_NORMAL', 0, 8000, 8000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO measurement_readings (
                    id, sessionId, orderIndex, systolic, diastolic, pulse
                ) VALUES
                    ('discard-1', 'discard-first', 1, 140, 90, 80),
                    ('discard-2', 'discard-first', 2, 120, 80, 60),
                    ('all-1', 'all-readings', 1, 140, 90, 80),
                    ('all-2', 'all-readings', 2, 120, 80, 60)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            AVERAGE_STRATEGY_TEST_DB,
            7,
            true,
            AppDatabase.MIGRATION_6_7
        ).use { db ->
            db.query(
                "SELECT id, averageStrategy, avgSystolic, avgDiastolic, avgPulse " +
                    "FROM measurement_sessions ORDER BY id"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("all-readings", cursor.getString(0))
                assertEquals("ALL", cursor.getString(1))
                assertEquals(130, cursor.getInt(2))
                assertEquals(85, cursor.getInt(3))
                assertEquals(70, cursor.getInt(4))

                assertTrue(cursor.moveToNext())
                assertEquals("discard-first", cursor.getString(0))
                assertEquals("DISCARD_FIRST", cursor.getString(1))
                assertEquals(120, cursor.getInt(2))
                assertEquals(80, cursor.getInt(3))
                assertEquals(60, cursor.getInt(4))
            }
        }
    }

    companion object {
        private const val TEST_DB = "trend-migration-test"
        private const val RISK_TEST_DB = "risk-migration-test"
        private const val CHAIN_TEST_DB = "chain-migration-test"
        private const val GUIDELINE_TEST_DB = "guideline-migration-test"
        private const val AVERAGE_STRATEGY_TEST_DB = "average-strategy-migration-test"
    }
}
