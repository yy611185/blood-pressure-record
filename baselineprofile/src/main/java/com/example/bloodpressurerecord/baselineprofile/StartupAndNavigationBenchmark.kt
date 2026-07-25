package com.example.bloodpressurerecord.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupAndNavigationBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutCompilation() = measureStartup(
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD
    )

    @Test
    fun coldStartupWithBaselineProfile() = measureStartup(
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require
        ),
        startupMode = StartupMode.COLD
    )

    @Test
    fun warmStartupWithBaselineProfile() = measureStartup(
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require
        ),
        startupMode = StartupMode.WARM
    )

    @Test
    fun calendarAndTrendNavigation() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require
            ),
            iterations = 5,
            setupBlock = {
                killProcess()
                startDashboard()
            }
        ) {
            runCoreJourneys()
        }
    }

    private fun measureStartup(
        compilationMode: CompilationMode,
        startupMode: StartupMode
    ) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = 10,
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
        }
    }
}
