package com.example.bloodpressurerecord.domain.time

/**
 * 测量时间允许有少量设备时钟误差，但不能被用来写入明显的未来记录。
 * 这个规则需要在所有写入入口复用，避免导入、编辑和新增记录产生不同结果。
 */
object MeasurementTimestampValidator {
    const val FUTURE_TOLERANCE_MILLIS: Long = 2 * 60 * 1000L
    const val FUTURE_MEASUREMENT_TIME_MESSAGE: String = "测量时间不能晚于当前时间 2 分钟以上"

    fun isFuture(measuredAt: Long, nowMillis: Long): Boolean {
        return measuredAt > nowMillis + FUTURE_TOLERANCE_MILLIS
    }

    fun validate(measuredAt: Long, nowMillis: Long): String? {
        return if (isFuture(measuredAt, nowMillis)) {
            FUTURE_MEASUREMENT_TIME_MESSAGE
        } else {
            null
        }
    }
}
