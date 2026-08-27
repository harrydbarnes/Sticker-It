package com.stickerit.app.ui.batch

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerit.app.data.model.BatchImportItem
import com.stickerit.app.data.model.BatchImportUiState
import com.stickerit.app.data.model.BatchItemStatus
import com.stickerit.app.data.repository.StickerRepository
import com.stickerit.app.domain.ImageSegmentationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BatchImportViewModel @Inject constructor(
    private val repository: StickerRepository,
    private val segmentationHelper: ImageSegmentationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchImportUiState())
    val uiState: StateFlow<BatchImportUiState> = _uiState.asStateFlow()

    private var processingJob: Job? = null
    private var initializedKey: String? = null

    fun initialize(uriStrings: List<String>) {
        val key = uriStrings.joinToString("\n")
        if (key == initializedKey) return
        initializedKey = key
        _uiState.value = BatchImportUiState(
            items = uriStrings.mapIndexed { index, uri ->
                BatchImportItem(
                    uriString = uri,
                    displayName = "Sticker ${index + 1}",
                )
            },
        )
    }

    fun start() {
        if (processingJob?.isActive == true) return
        if (_uiState.value.pendingCount == 0) return

        processingJob = viewModelScope.launch {
            val pending = _uiState.value.items.mapIndexedNotNull { index, item ->
                index.takeIf {
                    item.status == BatchItemStatus.QUEUED || item.status == BatchItemStatus.CANCELLED
                }
            }

            for (index in pending) {
                if (!isActive) break
                updateItem(index) { it.copy(status = BatchItemStatus.PROCESSING, errorMessage = null) }
                processItem(index)
            }

            if (isActive) {
                _uiState.update { it.copy(isRunning = false, currentIndex = null) }
            }
        }
        _uiState.update { it.copy(isRunning = true) }
    }

    fun cancel() {
        processingJob?.cancel()
        processingJob = null
        _uiState.update { state ->
            state.copy(
                isRunning = false,
                currentIndex = null,
                items = state.items.map { item ->
                    if (item.status == BatchItemStatus.PROCESSING) {
                        item.copy(status = BatchItemStatus.CANCELLED, errorMessage = "Cancelled")
                    } else {
                        item
                    }
                },
            )
        }
    }

    fun retry(index: Int) {
        if (_uiState.value.isRunning) return
        updateItem(index) { it.copy(status = BatchItemStatus.QUEUED, errorMessage = null) }
        start()
    }

    private suspend fun processItem(index: Int) {
        _uiState.update { it.copy(currentIndex = index) }
        var loadedBitmap: Bitmap? = null
        var segmentedBitmap: Bitmap? = null
        var stickerBitmap: Bitmap? = null

        try {
            val item = _uiState.value.items.getOrNull(index) ?: return
            loadedBitmap = repository.loadBitmapFromUri(Uri.parse(item.uriString))
                ?: error("Could not load image")
            val result = segmentationHelper.segment(loadedBitmap)
            segmentedBitmap = result.original
            stickerBitmap = segmentationHelper.buildStickerBitmap(
                original = result.original,
                confidenceMask = result.confidenceMask,
                maskWidth = result.maskWidth,
                maskHeight = result.maskHeight,
            )
            repository.saveSticker(
                bitmap = stickerBitmap,
                name = item.displayName,
                originalBitmap = result.original,
                confidenceMask = result.confidenceMask,
                maskWidth = result.maskWidth,
                maskHeight = result.maskHeight,
            )
            updateItem(index) { it.copy(status = BatchItemStatus.COMPLETE, errorMessage = null) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: OutOfMemoryError) {
            updateItem(index) {
                it.copy(status = BatchItemStatus.FAILED, errorMessage = "Image is too large")
            }
        } catch (error: Exception) {
            updateItem(index) {
                it.copy(
                    status = BatchItemStatus.FAILED,
                    errorMessage = error.message ?: "Could not create sticker",
                )
            }
        } finally {
            stickerBitmap?.takeUnless { it.isRecycled }?.recycle()
            segmentedBitmap?.takeUnless { it.isRecycled || it === loadedBitmap }?.recycle()
            loadedBitmap?.takeUnless { it.isRecycled }?.recycle()
        }
    }

    private fun updateItem(index: Int, transform: (BatchImportItem) -> BatchImportItem) {
        _uiState.update { state ->
            if (index !in state.items.indices) return@update state
            state.copy(items = state.items.mapIndexed { itemIndex, item ->
                if (itemIndex == index) transform(item) else item
            })
        }
    }

    override fun onCleared() {
        processingJob?.cancel()
        super.onCleared()
    }
}
