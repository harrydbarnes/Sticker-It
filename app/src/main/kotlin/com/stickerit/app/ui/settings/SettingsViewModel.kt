package com.stickerit.app.ui.settings

import androidx.lifecycle.ViewModel
import com.stickerit.app.data.repository.EditorSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: EditorSettingsRepository,
) : ViewModel() {
    val zoomAssistEnabled = settingsRepository.zoomAssistEnabled

    fun setZoomAssistEnabled(enabled: Boolean) {
        settingsRepository.setZoomAssistEnabled(enabled)
    }
}
