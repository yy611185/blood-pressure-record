package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.domain.report.ReportOptions
import com.example.bloodpressurerecord.domain.report.ReportPeriod
import com.example.bloodpressurerecord.domain.report.ReportSnapshot

interface ReportRepository {
    /** 返回当前周期的一致快照；被隐藏的内容不得进入快照。 */
    suspend fun loadSnapshot(period: ReportPeriod, options: ReportOptions): ReportSnapshot

    /** 是否已填写可用于医生报告的姓名或年龄。 */
    suspend fun hasProfileIdentity(): Boolean = true
}
