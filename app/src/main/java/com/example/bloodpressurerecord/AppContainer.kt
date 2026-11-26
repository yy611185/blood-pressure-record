package com.example.bloodpressurerecord

import android.content.Context
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.datastore.appDataStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.BpZoneConfigRepository
import com.example.bloodpressurerecord.data.repository.DefaultBloodPressureRepository
import com.example.bloodpressurerecord.data.repository.DefaultBpZoneConfigRepository
import com.example.bloodpressurerecord.data.repository.DefaultMedicationRepository
import com.example.bloodpressurerecord.data.repository.DefaultReportRepository
import com.example.bloodpressurerecord.data.repository.DefaultSettingsRepository
import com.example.bloodpressurerecord.data.repository.DefaultTrendRepository
import com.example.bloodpressurerecord.data.repository.DeletionUndoManager
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.ReportRepository
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.TrendRepository
import com.example.bloodpressurerecord.export.report.PdfReportRenderer
import com.example.bloodpressurerecord.export.report.ReportCache
import com.example.bloodpressurerecord.reminder.MedicationReminderCoordinator
import com.example.bloodpressurerecord.ui.common.FileSessionDraftRepository
import com.example.bloodpressurerecord.ui.common.SessionDraftRepository
import com.example.bloodpressurerecord.ui.report.DefaultReportGenerator
import com.example.bloodpressurerecord.ui.report.ReportGenerator
import com.example.bloodpressurerecord.update.UpdatePreferences
import com.example.bloodpressurerecord.update.UpdatePromptGate
import com.example.bloodpressurerecord.update.UpdateRepository
import com.example.bloodpressurerecord.widget.AppWidgetUpdater
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface AppContainer {
    val bpZoneConfigRepository: BpZoneConfigRepository
    val bloodPressureRepository: BloodPressureRepository
    val settingsRepository: SettingsRepository
    val trendRepository: TrendRepository
    val medicationRepository: MedicationRepository
    val medicationReminderCoordinator: MedicationReminderCoordinator
    val deletionUndoManager: DeletionUndoManager
    val systemTimeChangeSignal: Flow<Unit>
    val sessionDraftRepository: SessionDraftRepository
    val updatePreferences: UpdatePreferences
    val updateRepository: UpdateRepository
    val updatePromptGate: UpdatePromptGate
    val reportRepository: ReportRepository
    val reportCache: ReportCache
    val reportGenerator: ReportGenerator

    fun notifySystemTimeOrZoneChanged() = Unit
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.create(appContext) }
    private val appSettingsStore by lazy { AppSettingsStore(appContext) }
    private val mutableTimeChangeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val systemTimeChangeSignal: Flow<Unit> = mutableTimeChangeSignal.asSharedFlow()

    override fun notifySystemTimeOrZoneChanged() {
        mutableTimeChangeSignal.tryEmit(Unit)
    }

    override val updatePreferences by lazy { UpdatePreferences(appContext) }
    override val updateRepository by lazy { UpdateRepository(updatePreferences) }
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
            database = database,
            onDataChanged = { AppWidgetUpdater.requestUpdate(appContext) }
        )
    }

    override val medicationRepository: MedicationRepository by lazy {
        DefaultMedicationRepository(
            dao = database.medicationDao(),
            onDataChanged = { AppWidgetUpdater.requestUpdate(appContext) },
            onTimeIdsRemoved = { ids -> medicationReminderCoordinator.cancelRemovedNotifications(ids) },
            onSlotTaken = { timeId, epochDay ->
                medicationReminderCoordinator.dismissTakenNotification(timeId, epochDay)
            }
        )
    }

    override val medicationReminderCoordinator: MedicationReminderCoordinator by lazy {
        MedicationReminderCoordinator(appContext, medicationRepository)
    }

    override val deletionUndoManager: DeletionUndoManager by lazy {
        DeletionUndoManager(
            restoreSession = { snapshot -> bloodPressureRepository.restoreSession(snapshot) },
            restoreMedication = { snapshot -> medicationRepository.restoreMedication(snapshot) },
            onMedicationRestored = { settingsRepository.refreshReminders() }
        )
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
            cleanMedicationCalendar = { medicationReminderCoordinator.clearCalendarEvents() },
            medicationAlarmResync = { medicationReminderCoordinator.resyncAlarms() },
            onDataChanged = { AppWidgetUpdater.updateNow(appContext) },
            reportCache = reportCache,
            deletionUndoManager = deletionUndoManager,
            cancelRemovedMedicationNotifications = { ids ->
                medicationReminderCoordinator.cancelRemovedNotifications(ids)
            }
        )
    }

    override val trendRepository: TrendRepository by lazy {
        DefaultTrendRepository(database.measurementSessionDao())
    }
}
