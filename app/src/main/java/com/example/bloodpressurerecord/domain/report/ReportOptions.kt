package com.example.bloodpressurerecord.domain.report

/** 仅属于本次导出，不持久化隐私选择。 */
data class ReportOptions(
    val includeIdentity: Boolean = false,
    val includeNotes: Boolean = true,
    val includeMedications: Boolean = true,
    val template: ReportTemplate = ReportTemplate.GENERAL
)
