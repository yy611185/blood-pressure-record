package com.example.bloodpressurerecord.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "software_updates")

data class UpdateSettings(
    val autoCheckEnabled: Boolean = true,
    val lastSuccessfulCheckAt: Long? = null,
    val skippedVersionCode: Long? = null,
    val snoozedVersionCode: Long? = null,
    val snoozedUntil: Long? = null,
    val requiredDismissedVersionCode: Long? = null,
    val requiredDismissedUntil: Long? = null
)

/** Separate store: update preferences do not enter AppSettings, Room or health backups. */
class UpdatePreferences internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.updateDataStore)
    private val data = store.data.catch { error ->
        if (error is IOException) {
            emit(emptyPreferences())
        } else {
            throw error
        }
    }
    val settings: Flow<UpdateSettings> = data.map { p ->
        UpdateSettings(
            autoCheckEnabled = p[AUTO_CHECK] ?: true,
            lastSuccessfulCheckAt = p[LAST_SUCCESS],
            skippedVersionCode = p[SKIPPED],
            snoozedVersionCode = p[SNOOZED], snoozedUntil = p[SNOOZE_UNTIL],
            requiredDismissedVersionCode = p[REQUIRED_DISMISSED], requiredDismissedUntil = p[REQUIRED_UNTIL]
        )
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) = set(AUTO_CHECK, enabled)
    suspend fun skip(versionCode: Long) = set(SKIPPED, versionCode)

    suspend fun snooze(versionCode: Long, until: Long) {
        store.edit { it[SNOOZED] = versionCode; it[SNOOZE_UNTIL] = until }
    }

    suspend fun dismissRequired(versionCode: Long, until: Long) {
        store.edit { it[REQUIRED_DISMISSED] = versionCode; it[REQUIRED_UNTIL] = until }
    }

    /** Opening a browser postpones this version's reminder; it never records an installation. */
    suspend fun recordBrowserOpened(versionCode: Long, until: Long) {
        store.edit {
            it[SNOOZED] = versionCode
            it[SNOOZE_UNTIL] = until
            it[REQUIRED_DISMISSED] = versionCode
            it[REQUIRED_UNTIL] = until
        }
    }

    suspend fun readCachedManifest(): String? = data.first()[CACHED_MANIFEST]
    /** Both values are committed together so a successful check always has a validated cache. */
    suspend fun recordSuccessfulCheck(manifest: UpdateManifest, checkedAt: Long) {
        store.edit { it[CACHED_MANIFEST] = manifest.encode(); it[LAST_SUCCESS] = checkedAt }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    companion object {
        private val AUTO_CHECK = booleanPreferencesKey("auto_check")
        private val LAST_SUCCESS = longPreferencesKey("last_success")
        private val SKIPPED = longPreferencesKey("skipped_version")
        private val SNOOZED = longPreferencesKey("snoozed_version")
        private val SNOOZE_UNTIL = longPreferencesKey("snoozed_until")
        private val REQUIRED_DISMISSED = longPreferencesKey("required_dismissed_version")
        private val REQUIRED_UNTIL = longPreferencesKey("required_dismissed_until")
        private val CACHED_MANIFEST = stringPreferencesKey("cached_manifest")
    }
}
