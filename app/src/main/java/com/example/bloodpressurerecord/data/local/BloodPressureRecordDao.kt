package com.example.bloodpressurerecord.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BloodPressureRecordDao {
    @Query("SELECT * FROM blood_pressure_records ORDER BY measuredAtMillis DESC")
    fun observeAll(): Flow<List<BloodPressureRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BloodPressureRecordEntity)

    @Query("SELECT * FROM blood_pressure_records ORDER BY measuredAtMillis DESC")
    suspend fun getAll(): List<BloodPressureRecordEntity>
}
