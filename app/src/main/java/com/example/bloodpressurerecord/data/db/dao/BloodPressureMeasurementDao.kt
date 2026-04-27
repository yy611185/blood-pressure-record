package com.example.bloodpressurerecord.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import com.example.bloodpressurerecord.data.db.entity.BloodPressureMeasurementEntity
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

data class LegacyBloodPressureRecordRow(
    val id: Long,
    val memberName: String?,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val measuredAtMillis: Long,
    val level: String?,
    val remark: String?
)

@Dao
interface BloodPressureMeasurementDao {
    @Query("SELECT * FROM bp_measurements ORDER BY measuredAtMillis DESC")
    fun observeAll(): Flow<List<BloodPressureMeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BloodPressureMeasurementEntity)

    @Query("SELECT COUNT(*) FROM bp_measurements")
    suspend fun countAll(): Int

    @Query("SELECT * FROM bp_measurements ORDER BY measuredAtMillis ASC")
    suspend fun getAll(): List<BloodPressureMeasurementEntity>

    @Query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = :tableName")
    suspend fun tableExists(tableName: String): Int

    @RawQuery
    suspend fun getLegacyBloodPressureRecords(query: SupportSQLiteQuery): List<LegacyBloodPressureRecordRow>

    @Query("DELETE FROM bp_measurements")
    suspend fun deleteAll()
}
