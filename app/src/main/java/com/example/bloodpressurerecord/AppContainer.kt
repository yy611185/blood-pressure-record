package com.example.bloodpressurerecord

import android.content.Context
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
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

interface AppContainer {
    val bloodPressureRepository: BloodPressureRepository
    val settingsRepository: SettingsRepository
    val trendRepository: TrendRepository
    val medicationRepository: MedicationRepository
    val medicationReminderCoordinator: MedicationReminderCoordinator
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.create(appContext) }
    private val appSettingsStore by lazy { AppSettingsStore(appContext) }

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
            onDataChanged = { AppWidgetUpdater.requestUpdate(appContext) }
        )
    }

    override val trendRepository: TrendRepository by lazy {
        DefaultTrendRepository(database.measurementSessionDao())
    }
}
