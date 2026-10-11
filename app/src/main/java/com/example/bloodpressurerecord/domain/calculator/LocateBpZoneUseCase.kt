package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.BpAxisConfig
import com.example.bloodpressurerecord.domain.model.BpZone
import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import com.example.bloodpressurerecord.domain.model.BpZonePosition

/**
 * 根据配置定位示意条；恰好等于分界点时归入更高一级。
 * 普通分界点的区间内位置为 0；若末分界点等于 max，按显示范围右端规则取 1。
 */
class LocateBpZoneUseCase {
    operator fun invoke(sys: Int, dia: Int, config: BpZoneConfig): BpZonePosition? {
        if (sys <= 0 || dia <= 0 || !config.isValid()) return null

        val sysPosition = locateAxis(sys, config.sys)
        val diaPosition = locateAxis(dia, config.dia)
        val selected = when {
            sysPosition.zone.ordinal > diaPosition.zone.ordinal -> sysPosition
            sysPosition.zone.ordinal < diaPosition.zone.ordinal -> diaPosition
            sysPosition.offset >= diaPosition.offset -> sysPosition
            else -> diaPosition
        }
        return BpZonePosition(
            zone = selected.zone,
            offset = selected.offset,
            percent = ((selected.zone.ordinal + selected.offset) * 25f).coerceIn(2f, 98f)
        )
    }

    private fun locateAxis(value: Int, axis: BpAxisConfig): AxisPosition {
        if (value < axis.min) return AxisPosition(BpZone.LOW, 0f)
        if (value >= axis.max) return AxisPosition(BpZone.HIGH, 1f)

        // 使用严格小于：等于 cuts 时进入右侧区间。端点 cuts 可等于 min / max，
        // 此时零宽的首尾区间不会进入插值计算。
        return when {
            value < axis.cuts.low -> position(BpZone.LOW, value, axis.min, axis.cuts.low)
            value < axis.cuts.normal -> position(BpZone.NORMAL, value, axis.cuts.low, axis.cuts.normal)
            value < axis.cuts.high -> position(BpZone.ELEVATED, value, axis.cuts.normal, axis.cuts.high)
            else -> position(BpZone.HIGH, value, axis.cuts.high, axis.max)
        }
    }

    private fun position(zone: BpZone, value: Int, start: Int, end: Int): AxisPosition {
        // 先转 Double 再做差，避免极端 Int 配置或读数发生整数溢出。
        val offset = ((value.toDouble() - start.toDouble()) / (end.toDouble() - start.toDouble()))
            .toFloat().coerceIn(0f, 1f)
        return AxisPosition(zone, offset)
    }

    private data class AxisPosition(val zone: BpZone, val offset: Float)
}
