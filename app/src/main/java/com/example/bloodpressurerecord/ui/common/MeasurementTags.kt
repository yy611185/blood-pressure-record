package com.example.bloodpressurerecord.ui.common

/** 仅拆分旧记录中合并保存的标签；新记录使用独立自由文本字段。 */
object MeasurementTags {
    private val legacyFactors = setOf(
        "已服降压药", "未服药", "饮酒后", "咖啡浓茶后", "饱餐后",
        "睡眠不足", "情绪紧张", "刚运动完", "吸烟后", "洗澡后", "憋尿"
    )

    fun splitSymptomsAndFactors(all: Collection<String>): Pair<Set<String>, Set<String>> {
        val factorSet = all.filterTo(linkedSetOf()) { it in legacyFactors }
        val symptomSet = all.filterTo(linkedSetOf()) { it !in legacyFactors }
        return symptomSet to factorSet
    }
}
