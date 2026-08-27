package com.stickerit.app.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    companion object {
        private const val PREFERENCES_NAME = "editor_settings"
        private const val KEY_ZOOM_ASSIST_ENABLED = "zoom_assist_enabled"
    }

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _zoomAssistEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_ZOOM_ASSIST_ENABLED, false),
    )

    val zoomAssistEnabled: StateFlow<Boolean> = _zoomAssistEnabled.asStateFlow()

    fun setZoomAssistEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ZOOM_ASSIST_ENABLED, enabled) }
        _zoomAssistEnabled.value = enabled
    }
}
