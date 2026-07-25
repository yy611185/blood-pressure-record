package com.example.bloodpressurerecord.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bloodpressurerecord.data.db.dao.BloodPressureMeasurementDao
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.dao.UserProfileDao
import com.example.bloodpressurerecord.data.db.entity.BloodPressureMeasurementEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity

@Database(
    entities = [
        BloodPressureMeasurementEntity::class,
        MeasurementSessionEntity::class,
        MeasurementReadingEntity::class,
        UserProfileEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): BloodPressureMeasurementDao
    abstract fun measurementSessionDao(): MeasurementSessionDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "blood_pressure_record.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
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
