package com.example.bloodpressurerecord

import android.content.Context
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.datastore.appDataStore
import com.example.bloodpressurerecord.data.repository.BpZoneConfigRepository
import com.example.bloodpressurerecord.data.repository.DefaultBpZoneConfigRepository
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.DefaultBloodPressureRepository
import com.example.bloodpressurerecord.data.repository.DefaultMedicationRepository
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.DefaultSettingsRepository
import com.example.bloodpressurerecord.data.repository.DefaultTrendRepository
import com.example.bloodpressurerecord.data.repository.TrendRepository
import com.example.bloodpressurerecord.reminder.MedicationReminderCoordinator
import com.example.bloodpressurerecord.widget.AppWidgetUpdater
import com.example.bloodpressurerecord.ui.common.FileSessionDraftRepository
import com.example.bloodpressurerecord.ui.common.SessionDraftRepository
import com.example.bloodpressurerecord.update.UpdatePreferences
import com.example.bloodpressurerecord.update.UpdateRepository
import com.example.bloodpressurerecord.update.UpdateDownloadManager
import com.example.bloodpressurerecord.update.UpdateVerifier
import com.example.bloodpressurerecord.update.UpdateInstaller
import com.example.bloodpressurerecord.update.UpdatePromptGate
import com.example.bloodpressurerecord.data.repository.ReportRepository
import com.example.bloodpressurerecord.data.repository.DefaultReportRepository
import com.example.bloodpressurerecord.export.report.PdfReportRenderer
import com.example.bloodpressurerecord.export.report.ReportCache
import com.example.bloodpressurerecord.ui.report.ReportGenerator
import com.example.bloodpressurerecord.ui.report.DefaultReportGenerator
import java.io.File

interface AppContainer {
    val bpZoneConfigRepository: BpZoneConfigRepository
    val bloodPressureRepository: BloodPressureRepository
    val settingsRepository: SettingsRepository
    val trendRepository: TrendRepository
    val medicationRepository: MedicationRepository
    val medicationReminderCoordinator: MedicationReminderCoordinator
    val sessionDraftRepository: SessionDraftRepository
    val updatePreferences: UpdatePreferences
    val updateRepository: UpdateRepository
    val updateDownloadManager: UpdateDownloadManager
    val updateInstaller: UpdateInstaller
    val updatePromptGate: UpdatePromptGate
    val reportRepository: ReportRepository
    val reportCache: ReportCache
    val reportGenerator: ReportGenerator
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.create(appContext) }
    private val appSettingsStore by lazy { AppSettingsStore(appContext) }
    override val updatePreferences by lazy { UpdatePreferences(appContext) }
    override val updateRepository by lazy { UpdateRepository(updatePreferences) }
    private val updateVerifier by lazy { UpdateVerifier(appContext) }
    override val updateDownloadManager by lazy {
        UpdateDownloadManager(appContext, updatePreferences, updateVerifier)
    }
    override val updateInstaller by lazy { UpdateInstaller(appContext, updateVerifier) }
    override val updatePromptGate = UpdatePromptGate()
    override val reportRepository: ReportRepository by lazy { DefaultReportRepository(database) }
    override val reportCache by lazy { ReportCache(File(appContext.cacheDir, "reports")) }
    override val reportGenerator: ReportGenerator by lazy {
        DefaultReportGenerator(PdfReportRenderer(appContext.getString(R.string.app_name)), reportCache)
    }

    override val bpZoneConfigRepository: BpZoneConfigRepository by lazy {
        DefaultBpZoneConfigRepository(appContext.appDataStore)
    }

    override val sessionDraftRepository: SessionDraftRepository by lazy {
        FileSessionDraftRepository(appContext)
    }

    override val bloodPressureRepository: BloodPressureRepository by lazy {
        DefaultBloodPressureRepository(
            sessionDao = database.measurementSessionDao(),
            onDataChanged = { AppWidgetUpdater.requestUpdate(appContext) }
        )
    }

    override val medicationRepository: MedicationRepository by lazy {
        DefaultMedicationRepository(
            dao = database.medicationDao(),
            onDataChanged = { AppWidgetUpdater.requestUpdate(appContext) }
        )
    }

    override val medicationReminderCoordinator: MedicationReminderCoordinator by lazy {
        MedicationReminderCoordinator(appContext, medicationRepository)
    }

    override val settingsRepository: SettingsRepository by lazy {
        DefaultSettingsRepository(
            context = appContext,
            appSettingsStore = appSettingsStore,
            database = database,
            userProfileDao = database.userProfileDao(),
            measurementSessionDao = database.measurementSessionDao(),
            measurementDao = database.measurementDao(),
            medicationResync = { medicationReminderCoordinator.resyncAll() },
            medicationAlarmResync = { medicationReminderCoordinator.resyncAlarms() },
            onDataChanged = { AppWidgetUpdater.requestUpdate(appContext) }
        )
    }

    override val trendRepository: TrendRepository by lazy {
        DefaultTrendRepository(database.measurementSessionDao())
    }
}
