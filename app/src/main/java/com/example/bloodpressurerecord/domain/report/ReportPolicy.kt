package com.example.bloodpressurerecord.domain.report

object ReportPolicy {
    const val MAX_PERIOD_DAYS = 365
    const val MIN_VALID_DAYS_FOR_STATS = 3
    const val TITLE = "家庭血压报告"
    const val SEVEN_DAY_TITLE = "家庭血压 7 日监测"
    const val SEVEN_DAY_LENGTH = 7
    const val SEVEN_DAY_RULES = "首日计入平均；全部时段参与统计；各次测量等权；当天有记录即为有效日。"
    const val SEVEN_DAY_DETAIL_LEGEND = "早晚单元格为该时段各次测量代表值的平均值；多次测量逐次明细见补充页。"
    const val MULTIPLE_MEASUREMENTS = "多次"
    const val MULTIPLE_MEASUREMENTS_DETAIL = "同一时段多次测量明细"
    const val DISCLAIMER = "仅供参考，不替代医疗诊断。"
    const val INSUFFICIENT_DATA = "测量天数较少，统计仅供参考"
    const val NO_DATA = "所选周期内没有测量记录，请先记录血压。"
    const val SCENE_NOTICE = "未区分测量场景，分级与参考线采用诊室标准"
    const val MEDICATION_NOTICE = "仅列当前药品及保留的打卡次数；历史计划不足，无法统计依从率、漏服或调药。"
}
