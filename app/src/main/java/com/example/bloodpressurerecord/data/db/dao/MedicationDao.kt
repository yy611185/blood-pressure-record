package com.example.bloodpressurerecord.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.example.bloodpressurerecord.data.db.entity.MedicationEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationIntakeLogEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationTimeEntity
import kotlinx.coroutines.flow.Flow

data class MedicationWithTimes(
    @Embedded val medication: MedicationEntity,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val times: List<MedicationTimeEntity>
)

@Dao
interface MedicationDao {

    @Transaction
    @Query("SELECT * FROM medications ORDER BY createdAt ASC, id ASC")
    fun observeMedicationsWithTimes(): Flow<List<MedicationWithTimes>>

    @Transaction
    @Query("SELECT * FROM medications ORDER BY createdAt ASC, id ASC")
    suspend fun getMedicationsWithTimes(): List<MedicationWithTimes>

    @Query("SELECT * FROM medication_intake_logs WHERE epochDay = :epochDay")
    fun observeLogsForDay(epochDay: Long): Flow<List<MedicationIntakeLogEntity>>

    @Query("SELECT * FROM medication_intake_logs WHERE epochDay = :epochDay")
    suspend fun getLogsForDay(epochDay: Long): List<MedicationIntakeLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: MedicationIntakeLogEntity)

    @Query("DELETE FROM medication_intake_logs WHERE timeId = :timeId AND epochDay = :epochDay")
    suspend fun deleteLog(timeId: Long, epochDay: Long)

    @Insert
    suspend fun insertMedication(medication: MedicationEntity): Long

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedication(id: Long)

    @Insert
    suspend fun insertTime(time: MedicationTimeEntity): Long

    @Query("DELETE FROM medication_times WHERE medicationId = :medicationId")
    suspend fun deleteTimesForMedication(medicationId: Long)

    @Query("SELECT * FROM medication_times WHERE medicationId = :medicationId ORDER BY timeText ASC, id ASC")
    suspend fun getTimesForMedication(medicationId: Long): List<MedicationTimeEntity>

    @Query("DELETE FROM medication_times WHERE id IN (:ids)")
    suspend fun deleteTimesByIds(ids: List<Long>)

    @Query("SELECT * FROM medication_times WHERE id = :timeId")
    suspend fun getTime(timeId: Long): MedicationTimeEntity?

    @Query("SELECT id FROM medication_times")
    suspend fun getAllTimeIds(): List<Long>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedication(id: Long): MedicationEntity?

    @Query("DELETE FROM medication_intake_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM medication_times")
    suspend fun deleteAllTimes()

    @Query("DELETE FROM medications")
    suspend fun deleteAllMedications()

    /**
     * 药品与时间点必须原子写入；任何一步失败都会回滚，避免出现“有药品但没有提醒时间”。
     */
    @Transaction
    suspend fun insertMedicationWithTimes(
        medication: MedicationEntity,
        timeTexts: List<String>
    ): Long {
        val id = insertMedication(medication)
        timeTexts.forEach { timeText ->
            insertTime(MedicationTimeEntity(medicationId = id, timeText = timeText))
        }
        return id
    }

    /**
     * 按时间文本做差量更新。未改变的 MedicationTimeEntity 会保留原 id，
     * 因而其既有服药打卡历史不会再被级联删除。
     */
    @Transaction
    suspend fun updateMedicationWithTimes(
        medication: MedicationEntity,
        timeTexts: List<String>
    ) {
        updateMedication(medication)
        val desired = timeTexts.toSet()
        val existing = getTimesForMedication(medication.id)
        val keptTexts = hashSetOf<String>()
        val idsToDelete = existing.mapNotNull { time ->
            if (time.timeText in desired && keptTexts.add(time.timeText)) null else time.id
        }
        if (idsToDelete.isNotEmpty()) deleteTimesByIds(idsToDelete)
        desired.filterNot(keptTexts::contains).sorted().forEach { timeText ->
            insertTime(MedicationTimeEntity(medicationId = medication.id, timeText = timeText))
        }
    }
}
