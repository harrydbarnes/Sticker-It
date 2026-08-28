package com.stickerit.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerit.app.data.backup.BackupEvent
import com.stickerit.app.data.backup.BackupOperation
import com.stickerit.app.data.backup.BackupUiState
import com.stickerit.app.data.backup.StickerBackupRepository
import com.stickerit.app.data.backup.StickerBackupResult
import com.stickerit.app.data.repository.EditorSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: EditorSettingsRepository,
    private val backupRepository: StickerBackupRepository,
) : ViewModel() {
    val zoomAssistEnabled = settingsRepository.zoomAssistEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = false,
    )

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState = _backupState.asStateFlow()

    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvents = _backupEvents.asSharedFlow()

    fun setZoomAssistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setZoomAssistEnabled(enabled)
        }
    }

    fun exportLibrary(destination: Uri) {
        if (_backupState.value.isBusy) return
        runBackup(BackupOperation.EXPORTING) {
            when (val result = backupRepository.exportLibrary(destination)) {
                is StickerBackupResult.Exported -> _backupEvents.emit(
                    BackupEvent.Exported(result.stickerCount, result.packCount),
                )

                else -> _backupEvents.emit(BackupEvent.Failed)
            }
        }
    }

    fun importLibrary(source: Uri) {
        if (_backupState.value.isBusy) return
        runBackup(BackupOperation.IMPORTING) {
            when (val result = backupRepository.importLibrary(source)) {
                is StickerBackupResult.Imported -> _backupEvents.emit(
                    BackupEvent.Imported(
                        importedCount = result.importedCount,
                        skippedCount = result.skippedCount,
                        importedPackCount = result.importedPackCount,
                        skippedPackCount = result.skippedPackCount,
                    ),
                )

                else -> _backupEvents.emit(BackupEvent.Failed)
            }
        }
    }

    private fun runBackup(operation: BackupOperation, action: suspend () -> Unit) {
        _backupState.value = BackupUiState(operation)
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _backupEvents.emit(BackupEvent.Failed)
            } finally {
                _backupState.value = BackupUiState()
            }
        }
    }
}
