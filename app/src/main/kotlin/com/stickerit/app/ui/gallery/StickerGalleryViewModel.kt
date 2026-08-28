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

    private val _snackbarMessage = MutableSharedFlow<GalleryMessage>()
    val snackbarMessage: SharedFlow<GalleryMessage> = _snackbarMessage

    private val _createdPack = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val createdPack: SharedFlow<String> = _createdPack

    fun packItems(packId: String): Flow<List<StickerPackItemEntity>> = packRepository.items(packId)

    fun deleteSticker(sticker: Sticker) {
        viewModelScope.launch {
            repository.deleteSticker(sticker)
            _snackbarMessage.emit(GalleryMessage.StickerDeleted)
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
            _snackbarMessage.emit(GalleryMessage.PackCreated)
        }
    }

    fun renamePack(packId: String, name: String) {
        viewModelScope.launch {
            if (packRepository.renamePack(packId, name)) _snackbarMessage.emit(GalleryMessage.PackRenamed)
        }
    }

    fun deletePack(packId: String) {
        viewModelScope.launch {
            if (packRepository.deletePack(packId)) {
                _snackbarMessage.emit(GalleryMessage.PackDeleted)
            } else {
                _snackbarMessage.emit(GalleryMessage.KeepOnePack)
            }
        }
    }

    fun setPackTrayImage(packId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            if (packRepository.setTrayImage(packId, uri)) _snackbarMessage.emit(GalleryMessage.TrayImageUpdated)
            else _snackbarMessage.emit(GalleryMessage.CouldNotUseImage)
        }
    }

    fun reorderPackItems(packId: String, orderedStickerIds: List<Long>) {
        viewModelScope.launch { packRepository.reorderItems(packId, orderedStickerIds) }
    }

    fun updatePackItemMetadata(packId: String, stickerId: Long, emojis: String, accessibilityText: String) {
        viewModelScope.launch {
            if (packRepository.updateItemMetadata(packId, stickerId, emojis, accessibilityText)) {
                _snackbarMessage.emit(GalleryMessage.StickerMetadataSaved)
            }
        }
    }

    fun addOrUpdateWhatsAppPack(packId: String, stickers: List<Sticker>) {
        viewModelScope.launch {
            val result = runCatching { whatsAppHelper.addOrUpdateWhatsAppPack(packId, stickers) }
                .getOrElse {
                    _snackbarMessage.emit(GalleryMessage.CouldNotPrepareWhatsAppPack)
                    return@launch
                }
            _snackbarMessage.emit(
                when (result) {
                    WhatsAppResult.Opened -> GalleryMessage.ConfirmWhatsAppPack
                    WhatsAppResult.NotInstalled -> GalleryMessage.WhatsAppNotInstalled
                    WhatsAppResult.InvalidStickerCount -> GalleryMessage.InvalidWhatsAppStickerCount
                    WhatsAppResult.PackNotFound -> GalleryMessage.WhatsAppPackNotFound
                }
            )
        }
    }

    fun buildShareIntent(sticker: Sticker) = whatsAppHelper.buildShareIntent(sticker)
}
