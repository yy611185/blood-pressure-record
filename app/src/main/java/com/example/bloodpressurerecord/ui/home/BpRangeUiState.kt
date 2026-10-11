package com.example.bloodpressurerecord.ui.home

import com.example.bloodpressurerecord.domain.model.BpZone
import com.example.bloodpressurerecord.domain.model.BpZonePosition

/** 区间渲染与读数卡片共用的状态；颜色由界面主题按 [zone] 映射。 */
sealed interface BpRangeUiState {
    val zone: BpZone?

    data object Empty : BpRangeUiState {
        override val zone: BpZone? = null
    }

    data class Ready(
        val sys: Int,
        val dia: Int,
        val position: BpZonePosition,
        val zoneLabelRes: Int
    ) : BpRangeUiState {
        override val zone: BpZone get() = position.zone
    }
}
