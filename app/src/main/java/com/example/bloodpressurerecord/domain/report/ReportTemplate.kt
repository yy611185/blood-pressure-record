package com.example.bloodpressurerecord.domain.report

enum class ReportTemplate(val title: String) {
    GENERAL(ReportPolicy.TITLE),
    HOME_SEVEN_DAY(ReportPolicy.SEVEN_DAY_TITLE)
}
