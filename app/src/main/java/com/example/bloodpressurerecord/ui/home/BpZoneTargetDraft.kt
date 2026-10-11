package com.example.bloodpressurerecord.ui.home

import com.example.bloodpressurerecord.domain.model.BpAxisConfig
import com.example.bloodpressurerecord.domain.model.BpZoneConfig
import com.example.bloodpressurerecord.domain.model.BpZoneCuts

enum class BpZoneAxis { SYS, DIA }

enum class BpZoneField { MIN, LOW_CUT, NORMAL_CUT, HIGH_CUT, MAX }

data class BpZoneAxisDraft(
    val min: String,
    val lowCut: String,
    val normalCut: String,
    val highCut: String,
    val max: String
) {
    fun value(field: BpZoneField): String = when (field) {
        BpZoneField.MIN -> min
        BpZoneField.LOW_CUT -> lowCut
        BpZoneField.NORMAL_CUT -> normalCut
        BpZoneField.HIGH_CUT -> highCut
        BpZoneField.MAX -> max
    }

    fun update(field: BpZoneField, value: String): BpZoneAxisDraft = when (field) {
        BpZoneField.MIN -> copy(min = value)
        BpZoneField.LOW_CUT -> copy(lowCut = value)
        BpZoneField.NORMAL_CUT -> copy(normalCut = value)
        BpZoneField.HIGH_CUT -> copy(highCut = value)
        BpZoneField.MAX -> copy(max = value)
    }

    fun toAxisOrNull(): BpAxisConfig? {
        return BpAxisConfig(
            cuts = BpZoneCuts(
                low = lowCut.toIntOrNull() ?: return null,
                normal = normalCut.toIntOrNull() ?: return null,
                high = highCut.toIntOrNull() ?: return null
            ),
            min = min.toIntOrNull() ?: return null,
            max = max.toIntOrNull() ?: return null
        )
    }

    companion object {
        fun from(config: BpAxisConfig) = BpZoneAxisDraft(
            min = config.min.toString(),
            lowCut = config.cuts.low.toString(),
            normalCut = config.cuts.normal.toString(),
            highCut = config.cuts.high.toString(),
            max = config.max.toString()
        )
    }
}

/** 保留编辑中的空值和非数字内容，解析与顺序校验在 ViewModel 保存时完成。 */
data class BpZoneTargetDraft(val sys: BpZoneAxisDraft, val dia: BpZoneAxisDraft) {
    fun value(axis: BpZoneAxis, field: BpZoneField): String = when (axis) {
        BpZoneAxis.SYS -> sys.value(field)
        BpZoneAxis.DIA -> dia.value(field)
    }

    fun update(axis: BpZoneAxis, field: BpZoneField, value: String): BpZoneTargetDraft = when (axis) {
        BpZoneAxis.SYS -> copy(sys = sys.update(field, value))
        BpZoneAxis.DIA -> copy(dia = dia.update(field, value))
    }

    fun toConfigOrNull(): BpZoneConfig? {
        val config = BpZoneConfig(sys.toAxisOrNull() ?: return null, dia.toAxisOrNull() ?: return null)
        return config.takeIf { it.isValid() }
    }

    companion object {
        fun from(config: BpZoneConfig) = BpZoneTargetDraft(
            BpZoneAxisDraft.from(config.sys),
            BpZoneAxisDraft.from(config.dia)
        )
    }
}

data class BpZoneTargetEditorUiState(
    val draft: BpZoneTargetDraft,
    val saving: Boolean = false,
    val errorRes: Int? = null
)
