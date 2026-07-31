package com.example.bloodpressurerecord.ui.common

/**
 * 测量表单标签的唯一来源：场景（时段）、症状、影响血压因素。
 *
 * 影响因素与症状共用记录里的同一份标签存储（symptomsJson），
 * 通过 [splitSymptomsAndFactors] 在展示层拆分，不需要数据库迁移，
 * 备份导入导出天然兼容。
 */
object MeasurementTags {
    /** 场景标签：按测量时间所在时段自动预选，用户可改。 */
    val scenes = listOf("晨起", "上午", "下午", "晚上", "凌晨", "其他")

    val symptoms = listOf("无症状", "头痛", "头晕", "心悸", "胸闷或胸痛", "视物模糊", "其他")

    /** 影响血压的常见因素（服药情况 / 饮食刺激 / 身体状态 / 其它诱因）。 */
    val factors = listOf(
        "已服降压药", "未服药",
        "饮酒后", "咖啡浓茶后", "饱餐后",
        "睡眠不足", "情绪紧张", "刚运动完",
        "吸烟后", "洗澡后", "憋尿"
    )

    /**
     * 时段默认场景：5–9 点晨起、9–12 点上午、12–18 点下午、
     * 18–24 点晚上、0–5 点凌晨。
     */
    fun defaultSceneFor(hour: Int): String = when (hour) {
        in 5..8 -> "晨起"
        in 9..11 -> "上午"
        in 12..17 -> "下午"
        in 18..23 -> "晚上"
        else -> "凌晨"
    }

    /** 把记录里合并存储的标签拆回（症状, 影响因素）。 */
    fun splitSymptomsAndFactors(all: Collection<String>): Pair<Set<String>, Set<String>> {
        val factorSet = all.filterTo(linkedSetOf()) { it in factors }
        val symptomSet = all.filterTo(linkedSetOf()) { it !in factors }
        return symptomSet to factorSet
    }
}
