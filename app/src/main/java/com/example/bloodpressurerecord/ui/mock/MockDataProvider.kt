package com.example.bloodpressurerecord.ui.mock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

data class MockSession(
    val id: String,
    val timestamp: LocalDateTime,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?,
    val category: String,
    val note: String,
    val isAbnormal: Boolean,
    val readings: List<MockReading>
)

data class MockReading(
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?
)

object MockDataProvider {
    private val _sessions = MutableStateFlow<List<MockSession>>(emptyList())
    val sessions: StateFlow<List<MockSession>> = _sessions.asStateFlow()

    init {
        // Populate with fake data
        val now = LocalDateTime.now()
        _sessions.value = listOf(
            MockSession(
                id = "1",
                timestamp = now.minusHours(2),
                avgSystolic = 128,
                avgDiastolic = 82,
                avgPulse = 75,
                category = "正常高值",
                note = "饭后",
                isAbnormal = false,
                readings = listOf(
                    MockReading(130, 85, 76),
                    MockReading(126, 79, 74)
                )
            ),
            MockSession(
                id = "2",
                timestamp = now.minusDays(1).minusHours(5),
                avgSystolic = 118,
                avgDiastolic = 78,
                avgPulse = 70,
                category = "正常血压",
                note = "晨起",
                isAbnormal = false,
                readings = listOf(
                    MockReading(118, 78, 70),
                    MockReading(118, 78, 70)
                )
            ),
            MockSession(
                id = "3",
                timestamp = now.minusDays(2).minusHours(3),
                avgSystolic = 145,
                avgDiastolic = 92,
                avgPulse = 82,
                category = "高血压1级",
                note = "头晕",
                isAbnormal = true,
                readings = listOf(
                    MockReading(148, 94, 84),
                    MockReading(142, 90, 80)
                )
            )
        )
    }

    fun addSession(session: MockSession) {
        _sessions.value = listOf(session) + _sessions.value
    }

    fun getSessionById(id: String): MockSession? {
        return _sessions.value.find { it.id == id }
    }
}
