package com.example.bloodpressurerecord.domain.model

/** 示意条的固定顺序；仅用于展示用户配置的区间。 */
enum class BpZone {
    LOW,
    NORMAL,
    ELEVATED,
    HIGH
}

data class BpZoneCuts(val low: Int, val normal: Int, val high: Int)

data class BpAxisConfig(val cuts: BpZoneCuts, val min: Int, val max: Int) {
    fun isValid(): Boolean =
        min > 0 && min < max &&
            cuts.low in min..max && cuts.normal in min..max && cuts.high in min..max &&
            cuts.low < cuts.normal && cuts.normal < cuts.high
}

/** 收缩压与舒张压分别配置，所有分界数值集中在此模型中。 */
data class BpZoneConfig(val sys: BpAxisConfig, val dia: BpAxisConfig) {
    fun isValid(): Boolean = sys.isValid() && dia.isValid()

    companion object {
        /**
         * 这些数字只是占位示例，不构成医学标准；实际分级与默认目标范围由产品确认。
         * 该配置仅用于首页分段示意条，不替代现有的医学分级或风险判断。
         */
        val Default = BpZoneConfig(
            sys = BpAxisConfig(BpZoneCuts(low = 90, normal = 120, high = 140), min = 70, max = 180),
            dia = BpAxisConfig(BpZoneCuts(low = 60, normal = 80, high = 90), min = 40, max = 110)
        )
    }
}

data class BpZonePosition(
    val zone: BpZone,
    val offset: Float,
    val percent: Float
)
