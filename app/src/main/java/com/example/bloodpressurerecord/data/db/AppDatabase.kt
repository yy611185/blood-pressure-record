package com.example.bloodpressurerecord.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bloodpressurerecord.data.db.dao.BloodPressureMeasurementDao
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.dao.MedicationDao
import com.example.bloodpressurerecord.data.db.dao.UserProfileDao
import com.example.bloodpressurerecord.data.db.entity.BloodPressureMeasurementEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationIntakeLogEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationTimeEntity
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity

@Database(
    entities = [
        BloodPressureMeasurementEntity::class,
        MeasurementSessionEntity::class,
        MeasurementReadingEntity::class,
        UserProfileEntity::class,
        MedicationEntity::class,
        MedicationTimeEntity::class,
        MedicationIntakeLogEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): BloodPressureMeasurementDao
    abstract fun measurementSessionDao(): MeasurementSessionDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun medicationDao(): MedicationDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "blood_pressure_record.db"
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7
            ).build()
        }

        /**
         * v7：把每条记录采用的平均策略持久化。
         *
         * 旧记录先以 ALL 为默认值，再仅对“弃用第一组能匹配已存三项平均值、
         * 全部平均不能匹配”的记录回填 DISCARD_FIRST。两种策略结果相同的记录
         * 保留 ALL，不会改变任何已存平均值。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `measurement_sessions` " +
                        "ADD COLUMN `averageStrategy` TEXT NOT NULL DEFAULT 'ALL'"
                )
                db.execSQL(
                    """
                    UPDATE measurement_sessions
                    SET averageStrategy = 'DISCARD_FIRST'
                    WHERE (
                        SELECT COUNT(*)
                        FROM measurement_readings
                        WHERE measurement_readings.sessionId = measurement_sessions.id
                    ) >= 2
                    AND avgSystolic = (
                        SELECT CAST(AVG(systolic) + 0.5 AS INTEGER)
                        FROM measurement_readings
                        WHERE measurement_readings.sessionId = measurement_sessions.id
                          AND orderIndex > (
                              SELECT MIN(first_reading.orderIndex)
                              FROM measurement_readings AS first_reading
                              WHERE first_reading.sessionId = measurement_sessions.id
                          )
                    )
                    AND avgDiastolic = (
                        SELECT CAST(AVG(diastolic) + 0.5 AS INTEGER)
                        FROM measurement_readings
                        WHERE measurement_readings.sessionId = measurement_sessions.id
                          AND orderIndex > (
                              SELECT MIN(first_reading.orderIndex)
                              FROM measurement_readings AS first_reading
                              WHERE first_reading.sessionId = measurement_sessions.id
                          )
                    )
                    AND (
                        (avgPulse IS NULL AND (
                            SELECT AVG(pulse)
                            FROM measurement_readings
                            WHERE measurement_readings.sessionId = measurement_sessions.id
                              AND orderIndex > (
                                  SELECT MIN(first_reading.orderIndex)
                                  FROM measurement_readings AS first_reading
                                  WHERE first_reading.sessionId = measurement_sessions.id
                              )
                        ) IS NULL)
                        OR avgPulse = (
                            SELECT CAST(AVG(pulse) + 0.5 AS INTEGER)
                            FROM measurement_readings
                            WHERE measurement_readings.sessionId = measurement_sessions.id
                              AND orderIndex > (
                                  SELECT MIN(first_reading.orderIndex)
                                  FROM measurement_readings AS first_reading
                                  WHERE first_reading.sessionId = measurement_sessions.id
                              )
                        )
                    )
                    AND NOT (
                        avgSystolic = (
                            SELECT CAST(AVG(systolic) + 0.5 AS INTEGER)
                            FROM measurement_readings
                            WHERE measurement_readings.sessionId = measurement_sessions.id
                        )
                        AND avgDiastolic = (
                            SELECT CAST(AVG(diastolic) + 0.5 AS INTEGER)
                            FROM measurement_readings
                            WHERE measurement_readings.sessionId = measurement_sessions.id
                        )
                        AND (
                            (avgPulse IS NULL AND (
                                SELECT AVG(pulse)
                                FROM measurement_readings
                                WHERE measurement_readings.sessionId = measurement_sessions.id
                            ) IS NULL)
                            OR avgPulse = (
                                SELECT CAST(AVG(pulse) + 0.5 AS INTEGER)
                                FROM measurement_readings
                                WHERE measurement_readings.sessionId = measurement_sessions.id
                            )
                        )
                    )
                    """.trimIndent()
                )
            }
        }

        /** v6：新增服药提醒相关三张表（药品、服药时间、每日勾选历史）。 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medications` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `dosage` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medication_times` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `medicationId` INTEGER NOT NULL,
                        `timeText` TEXT NOT NULL,
                        FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_medication_times_medicationId` " +
                        "ON `medication_times` (`medicationId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medication_intake_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `medicationId` INTEGER NOT NULL,
                        `timeId` INTEGER NOT NULL,
                        `epochDay` INTEGER NOT NULL,
                        `takenAt` INTEGER NOT NULL,
                        FOREIGN KEY(`timeId`) REFERENCES `medication_times`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_intake_logs_timeId_epochDay` " +
                        "ON `medication_intake_logs` (`timeId`, `epochDay`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_medication_intake_logs_epochDay` " +
                        "ON `medication_intake_logs` (`epochDay`)"
                )
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `measurement_sessions` (
                        `id` TEXT NOT NULL,
                        `measuredAt` INTEGER NOT NULL,
                        `scene` TEXT NOT NULL,
                        `note` TEXT,
                        `symptomsJson` TEXT,
                        `avgSystolic` INTEGER NOT NULL,
                        `avgDiastolic` INTEGER NOT NULL,
                        `avgPulse` INTEGER,
                        `category` TEXT NOT NULL,
                        `highRiskAlertTriggered` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `measurement_readings` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `systolic` INTEGER NOT NULL,
                        `diastolic` INTEGER NOT NULL,
                        `pulse` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `measurement_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_readings_sessionId` ON `measurement_readings` (`sessionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_readings_sessionId_orderIndex` ON `measurement_readings` (`sessionId`, `orderIndex`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_profile` (
                        `id` INTEGER NOT NULL,
                        `name` TEXT,
                        `age` INTEGER,
                        `gender` TEXT,
                        `targetSystolic` INTEGER,
                        `targetDiastolic` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_measurement_sessions_measuredAt` " +
                        "ON `measurement_sessions` (`measuredAt`)"
                )
            }
        }

        /**
         * v5：分级标准从 ACC/AHA 切换为《中国高血压防治指南》，
         * 高风险急症阈值改为含边界（≥180 / ≥120）。
         * 从已存的平均值和原始读数无损重算 category 与 highRiskAlertTriggered，
         * 与 [BloodPressureRules] 保持同一套规则。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE measurement_sessions
                    SET category =
                        CASE
                            WHEN avgSystolic >= 180 OR avgDiastolic >= 110 THEN 'STAGE3'
                            WHEN avgSystolic >= 160 OR avgDiastolic >= 100 THEN 'STAGE2'
                            WHEN avgSystolic >= 140 OR avgDiastolic >= 90 THEN 'STAGE1'
                            WHEN avgSystolic >= 120 OR avgDiastolic >= 80 THEN 'HIGH_NORMAL'
                            WHEN avgSystolic < 90 OR avgDiastolic < 60 THEN 'LOW'
                            ELSE 'NORMAL'
                        END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE measurement_sessions
                    SET highRiskAlertTriggered =
                        CASE
                            WHEN avgSystolic >= 180 OR avgDiastolic >= 120 THEN 1
                            WHEN EXISTS (
                                SELECT 1
                                FROM measurement_readings
                                WHERE measurement_readings.sessionId = measurement_sessions.id
                                  AND (
                                      measurement_readings.systolic >= 180
                                      OR measurement_readings.diastolic >= 120
                                  )
                            ) THEN 1
                            ELSE 0
                        END
                    """.trimIndent()
                )
            }
        }

        /**
         * 旧版本只按平均值写入 highRiskAlertTriggered。
         * v4 保留物理列名，但把内容无损修正为“原始任一组或平均值为高风险”。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE measurement_sessions
                    SET highRiskAlertTriggered =
                        CASE
                            WHEN avgSystolic > 180 OR avgDiastolic > 120 THEN 1
                            WHEN EXISTS (
                                SELECT 1
                                FROM measurement_readings
                                WHERE measurement_readings.sessionId = measurement_sessions.id
                                  AND (
                                      measurement_readings.systolic > 180
                                      OR measurement_readings.diastolic > 120
                                  )
                            ) THEN 1
                            ELSE 0
                        END
                    """.trimIndent()
                )
            }
        }
    }
}
