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

data class MedicationFullSnapshot(
    val medication: MedicationEntity,
    val times: List<MedicationTimeEntity>,
    val logs: List<MedicationIntakeLogEntity>
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

    /** 报告按打卡归属自然日读取，操作时间 takenAt 不改变归属。 */
    @Query(
        """
        SELECT * FROM medication_intake_logs
        WHERE epochDay >= :startInclusiveEpochDay AND epochDay < :endExclusiveEpochDay
        ORDER BY epochDay ASC, timeId ASC, id ASC
        """
    )
    suspend fun getLogsInRange(
        startInclusiveEpochDay: Long,
        endExclusiveEpochDay: Long
    ): List<MedicationIntakeLogEntity>

    @Query("SELECT * FROM medication_intake_logs ORDER BY epochDay ASC, timeId ASC")
    suspend fun getAllLogs(): List<MedicationIntakeLogEntity>

    @Query("SELECT * FROM medication_intake_logs WHERE medicationId = :medicationId ORDER BY epochDay ASC, timeId ASC, id ASC")
    suspend fun getLogsForMedication(medicationId: Long): List<MedicationIntakeLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: MedicationIntakeLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLogs(logs: List<MedicationIntakeLogEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLogsExact(logs: List<MedicationIntakeLogEntity>)

    @Query("DELETE FROM medication_intake_logs WHERE timeId = :timeId AND epochDay = :epochDay")
    suspend fun deleteLog(timeId: Long, epochDay: Long)

    @Insert
    suspend fun insertMedication(medication: MedicationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedicationExact(medication: MedicationEntity): Long

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedication(id: Long): Int

    @Insert
    suspend fun insertTime(time: MedicationTimeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTimesExact(times: List<MedicationTimeEntity>)

    @Query("UPDATE medication_times SET active = :active WHERE id = :id")
    suspend fun setTimeActive(id: Long, active: Boolean)

    @Query("DELETE FROM medication_times WHERE medicationId = :medicationId")
    suspend fun deleteTimesForMedication(medicationId: Long)

    @Query("SELECT * FROM medication_times WHERE medicationId = :medicationId ORDER BY timeText ASC, id ASC")
    suspend fun getTimesForMedication(medicationId: Long): List<MedicationTimeEntity>

    @Query("DELETE FROM medication_times WHERE id IN (:ids)")
    suspend fun deleteTimesByIds(ids: List<Long>)

    @Query("SELECT * FROM medication_times WHERE id = :timeId")
    suspend fun getTime(timeId: Long): MedicationTimeEntity?

    @Query("SELECT id FROM medication_times WHERE id IN (:ids)")
    suspend fun getExistingTimeIds(ids: List<Long>): List<Long>

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
     * 在同一数据库事务中校验药品与时间点存在且有效后再写入打卡，
     * 防止删药或清空并发时触发外键约束异常。重复打卡因 IGNORE 保持幂等。
     */
    @Transaction
    suspend fun insertLogIfSlotActive(
        log: MedicationIntakeLogEntity,
        expectedTime: String? = null,
        expectedName: String? = null,
        expectedDosage: String? = null
    ): Long? {
        val time = getTime(log.timeId) ?: return null
        if (!time.active || time.medicationId != log.medicationId) return null
        val medication = getMedication(log.medicationId) ?: return null
        if (!medication.enabled ||
            (expectedTime != null && time.timeText != expectedTime) ||
            (expectedName != null && medication.name != expectedName) ||
            (expectedDosage != null && medication.dosage != expectedDosage)
        ) return null
        return insertLog(log)
    }

    /**
     * 在事务中完整快照药品、全部时间点（含已停用）与历史服药打卡后再执行删除。
     */
    @Transaction
    suspend fun deleteMedicationReturningSnapshot(id: Long): MedicationFullSnapshot? {
        val medication = getMedication(id) ?: return null
        val times = getTimesForMedication(id)
        val logs = getLogsForMedication(id)
        if (deleteMedication(id) != 1) return null
        return MedicationFullSnapshot(
            medication = medication,
            times = times,
            logs = logs
        )
    }

    /**
     * 事务化完整恢复已删药品及其原 ID、停用时间点、启用状态和历史打卡；
     * 若原主键已被占用则拒绝覆盖并返回 false。
     */
    @Transaction
    suspend fun restoreMedicationSnapshot(snapshot: MedicationFullSnapshot): Boolean {
        if (getMedication(snapshot.medication.id) != null) return false
        if (snapshot.times.isNotEmpty() && getExistingTimeIds(snapshot.times.map { it.id }).isNotEmpty()) {
            return false
        }
        insertMedicationExact(snapshot.medication)
        if (snapshot.times.isNotEmpty()) {
            insertTimesExact(snapshot.times)
        }
        if (snapshot.logs.isNotEmpty()) {
            insertLogsExact(snapshot.logs)
        }
        return true
    }

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
     * 按时间文本切换计划；移除的时间点只停用，保留其全部历史打卡。
     * 再次启用同一时间沿用原 id，避免重复计划及同日打卡。
     * 返回本次新停用的时间点 id 列表，供上层清理对应已发出的系统通知。
     */
    @Transaction
    suspend fun updateMedicationWithTimes(
        medication: MedicationEntity,
        timeTexts: List<String>
    ): List<Long> {
        updateMedication(medication)
        val desired = timeTexts.toSet()
        val existing = getTimesForMedication(medication.id)
        val keptTexts = hashSetOf<String>()
        val deactivatedIds = mutableListOf<Long>()
        existing.sortedByDescending { it.active }.forEach { time ->
            val active = time.timeText in desired && keptTexts.add(time.timeText)
            if (time.active != active) {
                setTimeActive(time.id, active)
                if (!active) deactivatedIds += time.id
            }
        }
        desired.filterNot(keptTexts::contains).sorted().forEach { timeText ->
            insertTime(MedicationTimeEntity(medicationId = medication.id, timeText = timeText))
        }
        return deactivatedIds
    }
}
