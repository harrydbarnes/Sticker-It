package com.stickerit.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

// ---------------------------------------------------------------------------
// Sticker
// ---------------------------------------------------------------------------

@Entity(tableName = "stickers")
data class Sticker(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Absolute path to the WebP file on internal storage */
    val filePath: String,
    /** Display name chosen by the user */
    val name: String,
    /** Epoch millis of creation */
    val createdAt: Long = System.currentTimeMillis(),
    /** Position within the gallery for manual reordering */
    val sortOrder: Int = 0,
    /** Width of the sticker in pixels */
    val width: Int = 512,
    /** Height of the sticker in pixels */
    val height: Int = 512,
    /** Whether the sticker has been pushed to GBoard */
    val addedToGboard: Boolean = false,
)

// ---------------------------------------------------------------------------
// StickerPack  (for GBoard ContentProvider)
// ---------------------------------------------------------------------------

data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val stickers: List<StickerFile>,
)

data class StickerFile(
    val imageFileName: String,
    val emojis: List<String>,
)

// ---------------------------------------------------------------------------
// UI State helpers
// ---------------------------------------------------------------------------

enum class BrushMode { INCLUDE, EXCLUDE }

data class EditorBrushState(
    val mode: BrushMode = BrushMode.EXCLUDE,
    val radius: Float = 40f,
    val opacity: Float = 1f,
)

sealed interface EditorUiState {
    data object Idle : EditorUiState
    data object Loading : EditorUiState
    data class SegmentationReady(
        val originalBitmap: android.graphics.Bitmap,
        val maskBitmap: android.graphics.Bitmap,
        val previewBitmap: android.graphics.Bitmap,
    ) : EditorUiState
    data class Error(val message: String) : EditorUiState
    data object Saved : EditorUiState
}

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data class Ready(val stickers: List<Sticker>) : GalleryUiState
    data object Empty : GalleryUiState
}
