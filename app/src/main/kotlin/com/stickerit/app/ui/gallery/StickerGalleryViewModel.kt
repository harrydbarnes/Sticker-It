package com.stickerit.app.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerit.app.data.model.GalleryUiState
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.provider.GboardHelper
import com.stickerit.app.data.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StickerGalleryViewModel @Inject constructor(
    private val repository: StickerRepository,
    private val gboardHelper: GboardHelper,
) : ViewModel() {

    val uiState: StateFlow<GalleryUiState> = repository.stickers
        .map { stickers ->
            when {
                stickers.isEmpty() -> GalleryUiState.Empty
                else -> GalleryUiState.Ready(stickers)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GalleryUiState.Loading,
        )

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage

    val isGboardInstalled: Boolean get() = gboardHelper.isGboardInstalled()

    fun deleteSticker(sticker: Sticker) {
        viewModelScope.launch {
            repository.deleteSticker(sticker)
            _snackbarMessage.emit("Sticker deleted")
        }
    }

    fun renameSticker(sticker: Sticker, newName: String) {
        viewModelScope.launch {
            repository.renameSticker(sticker, newName)
        }
    }

    fun reorderStickers(stickers: List<Sticker>) {
        viewModelScope.launch {
            repository.reorderStickers(stickers)
        }
    }

    fun addPackToGboard() {
        val sent = gboardHelper.addPackToGboard()
        viewModelScope.launch {
            _snackbarMessage.emit(
                if (sent) "Opening GBoard sticker import..."
                else "GBoard not found on this device"
            )
        }
    }

    fun buildShareIntent(sticker: Sticker) = gboardHelper.buildShareIntent(sticker)
}
