package com.stickerit.app.data.repository

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val EDITOR_SETTINGS_PREFERENCES_NAME = "editor_settings"
private const val KEY_ZOOM_ASSIST_ENABLED = "zoom_assist_enabled"

/**
 * One process-wide Preferences DataStore for editor settings.
 *
 * The migration keeps the original SharedPreferences key readable for existing
 * installs, then DataStore becomes the only source of truth for new writes.
 */
private val Context.editorSettingsDataStore by preferencesDataStore(
    name = EDITOR_SETTINGS_PREFERENCES_NAME,
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context = context,
                sharedPreferencesName = EDITOR_SETTINGS_PREFERENCES_NAME,
                keysToMigrate = setOf(KEY_ZOOM_ASSIST_ENABLED),
            ),
        )
    },
)

@Singleton
class EditorSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.applicationContext.editorSettingsDataStore
    private val zoomAssistKey = booleanPreferencesKey(KEY_ZOOM_ASSIST_ENABLED)

    /** Emits the persisted value and automatically reflects updates from Settings. */
    val zoomAssistEnabled: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> preferences[zoomAssistKey] ?: false }
        .distinctUntilChanged()

    /** Persists the setting atomically; callers own the lifecycle-aware coroutine. */
    suspend fun setZoomAssistEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[zoomAssistKey] = enabled
        }
    }
}
