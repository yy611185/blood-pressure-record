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

    companion object {
        private const val TEST_DB = "trend-migration-test"
        private const val RISK_TEST_DB = "risk-migration-test"
        private const val CHAIN_TEST_DB = "chain-migration-test"
    }
}
