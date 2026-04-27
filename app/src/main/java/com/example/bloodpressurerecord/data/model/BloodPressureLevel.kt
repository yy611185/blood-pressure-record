package com.example.bloodpressurerecord.data.model

enum class BloodPressureLevel(val label: String) {
    IDEAL("理想范围"),
    NORMAL_HIGH("正常高值"),
    GRADE_1("1级偏高"),
    GRADE_2("2级偏高"),
    GRADE_3("3级偏高"),
    LOW("偏低，请结合自身情况留意"),
    UNKNOWN("待判断")
}
