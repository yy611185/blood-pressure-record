package com.example.bloodpressurerecord.domain.model

/**
 * 平均值计算策略。
 *
 * 家庭自测指南通常建议：连续测 2–3 次、每次间隔 1–2 分钟，
 * 因第一次读数常因适应效应偏高，可弃用第一次、取其余读数平均。
 */
enum class AverageStrategy {
    /** 全部读数参与平均（默认）。 */
    ALL,

    /** 弃用第一组，取其余读数平均；仅一组读数时退化为全部平均。 */
    DISCARD_FIRST
}
