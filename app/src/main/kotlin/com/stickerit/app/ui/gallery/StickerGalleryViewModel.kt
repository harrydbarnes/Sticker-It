package com.stickerit.app.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerit.app.data.model.GalleryUiState
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.model.StickerPackItemEntity
import com.stickerit.app.data.provider.WhatsAppHelper
import com.stickerit.app.data.provider.WhatsAppResult
import com.stickerit.app.data.repository.StickerRepository
import com.stickerit.app.data.repository.StickerPackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StickerGalleryViewModel @Inject constructor(
    private val repository: StickerRepository,
    private val packRepository: StickerPackRepository,
    private val whatsAppHelper: WhatsAppHelper,
) : ViewModel() {

    init {
        viewModelScope.launch { packRepository.migrateLegacyPackIfNeeded() }
    }

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

    val packs = packRepository.packs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage

    private val _createdPack = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val createdPack: SharedFlow<String> = _createdPack

    fun packItems(packId: String): Flow<List<StickerPackItemEntity>> = packRepository.items(packId)

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

    fun createPack(name: String) {
        viewModelScope.launch {
            val pack = packRepository.createPack(name)
            _createdPack.emit(pack.id)
            _snackbarMessage.emit("Pack created")
        }
    }

    fun renamePack(packId: String, name: String) {
        viewModelScope.launch {
            if (packRepository.renamePack(packId, name)) _snackbarMessage.emit("Pack renamed")
        }
    }

    fun deletePack(packId: String) {
        viewModelScope.launch {
            if (packRepository.deletePack(packId)) {
                _snackbarMessage.emit("Pack deleted")
            } else {
                _snackbarMessage.emit("Keep at least one WhatsApp pack")
            }
        }
    }

    fun setPackTrayImage(packId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            if (packRepository.setTrayImage(packId, uri)) _snackbarMessage.emit("Tray image updated")
            else _snackbarMessage.emit("Could not use that image")
        }
    }

    fun reorderPackItems(packId: String, orderedStickerIds: List<Long>) {
        viewModelScope.launch { packRepository.reorderItems(packId, orderedStickerIds) }
    }

    fun updatePackItemMetadata(packId: String, stickerId: Long, emojis: String, accessibilityText: String) {
        viewModelScope.launch {
            if (packRepository.updateItemMetadata(packId, stickerId, emojis, accessibilityText)) {
                _snackbarMessage.emit("Sticker metadata saved")
            }
        }
    }

    fun addOrUpdateWhatsAppPack(packId: String, stickers: List<Sticker>) {
        viewModelScope.launch {
            val result = runCatching { whatsAppHelper.addOrUpdateWhatsAppPack(packId, stickers) }
                .getOrElse {
                    _snackbarMessage.emit("Could not prepare the WhatsApp pack")
                    return@launch
                }
            _snackbarMessage.emit(
                when (result) {
                    WhatsAppResult.Opened -> "Confirm the pack in WhatsApp to add or update it"
                    WhatsAppResult.NotInstalled -> "WhatsApp is not installed on this device"
                    WhatsAppResult.InvalidStickerCount -> "Choose between 3 and 30 stickers for a WhatsApp pack"
                    WhatsAppResult.PackNotFound -> "Choose a WhatsApp pack first"
                }
            )
        }
    }

    fun buildShareIntent(sticker: Sticker) = whatsAppHelper.buildShareIntent(sticker)
}
