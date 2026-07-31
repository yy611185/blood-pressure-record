package com.example.bloodpressurerecord.ui.common

import com.example.bloodpressurerecord.domain.model.BloodPressureCategory

/**
 * 血压分级中文标签的唯一来源，供全部页面复用。
 * 命名与《中国高血压防治指南》一致。
 */
object CategoryPresentation {
    fun label(category: BloodPressureCategory): String = when (category) {
        BloodPressureCategory.LOW -> "血压偏低"
        BloodPressureCategory.NORMAL -> "正常"
        BloodPressureCategory.HIGH_NORMAL -> "正常高值"
        BloodPressureCategory.STAGE1 -> "1级高血压"
        BloodPressureCategory.STAGE2 -> "2级高血压"
        BloodPressureCategory.STAGE3 -> "3级高血压"
    }

    /**
     * 数据库/备份中的分级字符串转中文标签。
     * 兼容 v5 迁移前的旧命名（ELEVATED/SEVERE），未知值原样返回。
     */
    fun label(category: String): String = when (category.uppercase()) {
        "ELEVATED" -> label(BloodPressureCategory.HIGH_NORMAL)
        "SEVERE" -> label(BloodPressureCategory.STAGE3)
        else -> BloodPressureCategory.entries
            .firstOrNull { it.name == category.uppercase() }
            ?.let(::label)
            ?: category
    }
}
