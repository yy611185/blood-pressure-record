package com.example.bloodpressurerecord.ui.report

import com.example.bloodpressurerecord.domain.report.BpReport
import com.example.bloodpressurerecord.domain.report.ReportLayoutPlan
import com.example.bloodpressurerecord.domain.report.ReportLayoutPlanner
import com.example.bloodpressurerecord.export.report.PdfReportRenderer
import com.example.bloodpressurerecord.export.report.ReportCache
import com.example.bloodpressurerecord.export.report.ReportTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReportArtifact(val file: File, val plan: ReportLayoutPlan)

/** 可替换的生成边界，ViewModel 测试不需要 Canvas、Room 或 Android Context。 */
interface ReportGenerator {
    suspend fun generate(report: BpReport): ReportArtifact
    fun discard(artifact: ReportArtifact)
    fun cleanup()
}

class DefaultReportGenerator(
    private val renderer: PdfReportRenderer,
    private val cache: ReportCache
) : ReportGenerator {
    override suspend fun generate(report: BpReport): ReportArtifact {
        var pendingFile: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val plan = ReportLayoutPlanner.plan(report, ReportTheme.layoutSpec, renderer.textMetrics())
                val file = cache.create(report.period)
                pendingFile = file
                renderer.render(plan, file)
                ReportArtifact(file, plan)
            }
        } catch (error: Throwable) {
            pendingFile?.let(cache::discard)
            throw error
        }
    }

    override fun discard(artifact: ReportArtifact) = cache.discard(artifact.file)
    override fun cleanup() = cache.cleanup()
}
