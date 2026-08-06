package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.data.db.dao.MedicationDao
import com.example.bloodpressurerecord.data.db.dao.MedicationWithTimes
import com.example.bloodpressurerecord.data.db.entity.MedicationEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationIntakeLogEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationTimeEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 首页/小部件展示用：一条“时间点 × 药品”的打卡行。 */
data class MedicationSlot(
    val medicationId: Long,
    val timeId: Long,
    val name: String,
    val dosage: String,
    val timeText: String,
    val taken: Boolean
)

interface MedicationRepository {
    fun observeMedicationsWithTimes(): Flow<List<MedicationWithTimes>>

    /** 观察某一天的打卡行（按时间升序）。 */
    fun observeSlotsForDay(date: LocalDate): Flow<List<MedicationSlot>>

    suspend fun getSlotsForDay(date: LocalDate): List<MedicationSlot>

    /** 返回数据库中现有时间点的 id；调度器会与 DataStore 中的旧 id 合并清理。 */
    suspend fun getAllTimeIds(): List<Long> = emptyList()

    suspend fun setTaken(medicationId: Long, timeId: Long, date: LocalDate, taken: Boolean)

    suspend fun addMedication(name: String, dosage: String, times: List<String>): Result<Long>

    suspend fun updateMedication(
        id: Long,
        name: String,
        dosage: String,
        enabled: Boolean,
        times: List<String>
    ): Result<Unit>

    suspend fun deleteMedication(id: Long): Result<Unit>
}

class DefaultMedicationRepository(
    private val dao: MedicationDao,
    /** 数据变化后的回调（用于刷新桌面小部件、重排提醒等）。 */
    private val onDataChanged: (() -> Unit)? = null
) : MedicationRepository {

    override fun observeMedicationsWithTimes(): Flow<List<MedicationWithTimes>> =
        dao.observeMedicationsWithTimes()

    override fun observeSlotsForDay(date: LocalDate): Flow<List<MedicationSlot>> =
        combine(
            dao.observeMedicationsWithTimes(),
            dao.observeLogsForDay(date.toEpochDay())
        ) { meds, logs -> buildSlots(meds, logs) }

    override suspend fun getSlotsForDay(date: LocalDate): List<MedicationSlot> =
        buildSlots(dao.getMedicationsWithTimes(), dao.getLogsForDay(date.toEpochDay()))

    override suspend fun getAllTimeIds(): List<Long> = dao.getAllTimeIds()

    private fun buildSlots(
        meds: List<MedicationWithTimes>,
        logs: List<MedicationIntakeLogEntity>
    ): List<MedicationSlot> {
        val takenTimeIds = logs.mapTo(hashSetOf()) { it.timeId }
        return meds
            .filter { it.medication.enabled }
            .flatMap { med ->
                med.times.map { time ->
                    MedicationSlot(
                        medicationId = med.medication.id,
                        timeId = time.id,
                        name = med.medication.name,
                        dosage = med.medication.dosage,
                        timeText = time.timeText,
                        taken = time.id in takenTimeIds
                    )
                }
            }
            .sortedWith(compareBy({ it.timeText }, { it.name }))
    }

    override suspend fun setTaken(
        medicationId: Long,
        timeId: Long,
        date: LocalDate,
        taken: Boolean
    ) {
        if (taken) {
            dao.insertLog(
                MedicationIntakeLogEntity(
                    medicationId = medicationId,
                    timeId = timeId,
                    epochDay = date.toEpochDay(),
                    takenAt = System.currentTimeMillis()
                )
            )
        } else {
            dao.deleteLog(timeId, date.toEpochDay())
        }
        onDataChanged?.invoke()
    }

    override suspend fun addMedication(
        name: String,
        dosage: String,
        times: List<String>
    ): Result<Long> = runCatching {
        val normalizedTimes = times.map(String::trim).filter(String::isNotEmpty).distinct().sorted()
        require(normalizedTimes.isNotEmpty()) { "请至少添加一个服药时间" }
        val id = dao.insertMedicationWithTimes(
            MedicationEntity(
                name = name.trim(),
                dosage = dosage.trim(),
                enabled = true,
                createdAt = System.currentTimeMillis()
            ),
            normalizedTimes
        )
        onDataChanged?.invoke()
        id
    }

    override suspend fun updateMedication(
        id: Long,
        name: String,
        dosage: String,
        enabled: Boolean,
        times: List<String>
    ): Result<Unit> = runCatching {
        val existing = dao.getMedication(id) ?: error("药品不存在")
        val normalizedTimes = times.map(String::trim).filter(String::isNotEmpty).distinct().sorted()
        require(normalizedTimes.isNotEmpty()) { "请至少添加一个服药时间" }
        dao.updateMedicationWithTimes(
            medication = existing.copy(
                name = name.trim(),
                dosage = dosage.trim(),
                enabled = enabled
            ),
            timeTexts = normalizedTimes
        )
        onDataChanged?.invoke()
    }

    override suspend fun deleteMedication(id: Long): Result<Unit> = runCatching {
        dao.deleteMedication(id)
        onDataChanged?.invoke()
    }
}
