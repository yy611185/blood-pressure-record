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

    @Update
    suspend fun updateTime(time: MedicationTimeEntity)

    /**
     * 智能差量更新：
     * 1. 相同 timeText 的保留原 id；
     * 2. 修改时间点时（如 08:00 改为 08:30），优先复用原有实体的 id 并 update 其 timeText，
     *    彻底避免触发 ON DELETE CASCADE 误删已有的打卡历史（medication_intake_logs）；
     * 3. 仅当用户明确减少时间点数量时，才删除多余的时间实体；
     * 4. 增加时间点时插入新实体。
     */
    @Transaction
    suspend fun updateMedicationWithTimes(
        medication: MedicationEntity,
        timeTexts: List<String>
    ) {
        updateMedication(medication)
        val desiredList = timeTexts.distinct().sorted()
        val existingList = getTimesForMedication(medication.id)
        
        val matchedExistingIds = hashSetOf<Long>()
        val unassignedDesired = mutableListOf<String>()

        for (desired in desiredList) {
            val exactMatch = existingList.firstOrNull { it.id !in matchedExistingIds && it.timeText == desired }
            if (exactMatch != null) {
                matchedExistingIds.add(exactMatch.id)
            } else {
                unassignedDesired.add(desired)
            }
        }

        val availableExisting = existingList.filter { it.id !in matchedExistingIds }.toMutableList()
        val toUpdate = mutableListOf<MedicationTimeEntity>()
        val toInsert = mutableListOf<String>()

        for (desired in unassignedDesired) {
            if (availableExisting.isNotEmpty()) {
                val reuseEntity = availableExisting.removeAt(0)
                toUpdate.add(reuseEntity.copy(timeText = desired))
            } else {
                toInsert.add(desired)
            }
        }

        val toDeleteIds = availableExisting.map { it.id }

        if (toDeleteIds.isNotEmpty()) {
            deleteTimesByIds(toDeleteIds)
        }
        for (item in toUpdate) {
            updateTime(item)
        }
        for (timeText in toInsert) {
            insertTime(MedicationTimeEntity(medicationId = medication.id, timeText = timeText))
        }
    }
}
