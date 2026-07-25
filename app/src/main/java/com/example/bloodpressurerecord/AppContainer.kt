package com.example.bloodpressurerecord

import android.content.Context
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.DefaultBloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.DefaultSettingsRepository
import com.example.bloodpressurerecord.data.repository.DefaultTrendRepository
import com.example.bloodpressurerecord.data.repository.TrendRepository

interface AppContainer {
    val bloodPressureRepository: BloodPressureRepository
    val settingsRepository: SettingsRepository
    val trendRepository: TrendRepository
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
        DefaultSettingsRepository(
            context = appContext,
            appSettingsStore = appSettingsStore,
            database = database,
            userProfileDao = database.userProfileDao(),
            measurementSessionDao = database.measurementSessionDao(),
            measurementDao = database.measurementDao()
        )
    }

    override val trendRepository: TrendRepository by lazy {
        DefaultTrendRepository(database.measurementSessionDao())
    }
}
