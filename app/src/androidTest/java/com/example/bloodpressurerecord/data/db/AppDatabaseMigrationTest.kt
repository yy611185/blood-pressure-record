package com.example.bloodpressurerecord.data.db

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
        AppDatabase::class.java.canonicalName,
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

    companion object {
        private const val TEST_DB = "trend-migration-test"
    }
}
