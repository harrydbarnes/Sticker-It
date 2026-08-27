package com.stickerit.app.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerit.app.data.model.GalleryUiState
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.provider.WhatsAppHelper
import com.stickerit.app.data.provider.WhatsAppResult
import com.stickerit.app.data.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StickerGalleryViewModel @Inject constructor(
    private val repository: StickerRepository,
    private val whatsAppHelper: WhatsAppHelper,
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

    fun addOrUpdateWhatsAppPack(stickers: List<Sticker>) {
        viewModelScope.launch {
            val result = runCatching { whatsAppHelper.addOrUpdateWhatsAppPack(stickers) }
                .getOrElse {
                    _snackbarMessage.emit("Could not prepare the WhatsApp pack")
                    return@launch
                }
            _snackbarMessage.emit(
                when (result) {
                    WhatsAppResult.Opened -> "Confirm the pack in WhatsApp to add or update it"
                    WhatsAppResult.NotInstalled -> "WhatsApp is not installed on this device"
                    WhatsAppResult.InvalidStickerCount -> "Choose between 3 and 30 stickers for a WhatsApp pack"
                }
            )
        }
    }

    fun buildShareIntent(sticker: Sticker) = whatsAppHelper.buildShareIntent(sticker)
}
