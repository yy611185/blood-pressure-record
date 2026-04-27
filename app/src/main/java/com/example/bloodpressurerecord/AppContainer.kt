package com.example.bloodpressurerecord

import android.content.Context
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.DefaultBloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.SettingsRepositoryStable

interface AppContainer {
    val bloodPressureRepository: BloodPressureRepository
    val settingsRepository: SettingsRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.create(appContext) }
    private val appSettingsStore by lazy { AppSettingsStore(appContext) }

    override val bloodPressureRepository: BloodPressureRepository by lazy {
        DefaultBloodPressureRepository(
            sessionDao = database.measurementSessionDao()
        )
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryStable(
            context = appContext,
            appSettingsStore = appSettingsStore,
            userProfileDao = database.userProfileDao(),
            measurementSessionDao = database.measurementSessionDao(),
            measurementDao = database.measurementDao()
        )
    }
}
