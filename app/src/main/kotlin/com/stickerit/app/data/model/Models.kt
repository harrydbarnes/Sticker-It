package com.stickerit.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    /**
     * Legacy on-disk column retained so version 1 libraries remain readable.
     * WhatsApp packs are generated from the current user selection, so this value
     * is not used to represent a destination or sync state.
     */
    @ColumnInfo(name = "addedToGboard")
    val legacyPackFlag: Boolean = false,
    /** Internal copy of the source used to create this sticker, when available. */
    val sourceFilePath: String? = null,
    /** Binary confidence mask used to reopen the sticker for non-destructive editing. */
    val maskFilePath: String? = null,
    /** JSON-encoded finishing recipe used to rebuild the sticker for later edits. */
    val finishRecipeJson: String? = null,
)

// ---------------------------------------------------------------------------
// StickerPack metadata
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

/**
 * A named WhatsApp pack owned by the user. The tray image is kept as a
 * filename inside the app's private files directory so the content provider
 * can serve it without exposing arbitrary paths.
 */
@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val publisher: String = "Sticker It",
    val trayImageFileName: String = "whatsapp_tray.png",
    val trayImageIsCustom: Boolean = false,
    val imageDataVersion: String = "1",
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)

/** Metadata and ordering for one sticker inside one named pack. */
@Entity(
    tableName = "sticker_pack_items",
    primaryKeys = ["packId", "stickerId"],
    foreignKeys = [
        ForeignKey(
            entity = StickerPackEntity::class,
            parentColumns = ["id"],
            childColumns = ["packId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Sticker::class,
            parentColumns = ["id"],
            childColumns = ["stickerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["stickerId"])],
)
data class StickerPackItemEntity(
    val packId: String,
    val stickerId: Long,
    val sortOrder: Int = 0,
    /** Comma-separated Unicode emoji keywords for WhatsApp search. */
    val emojis: String = "😀",
    /** Screen-reader-friendly description shown by the WhatsApp contract. */
    val accessibilityText: String = "",
)

/** Flattened provider row so the content provider never needs to load Room relations. */
data class PackStickerRow(
    val filePath: String,
    val emojis: String,
    val accessibilityText: String,
)

const val DEFAULT_STICKER_PACK_ID = "stickerit_library"

// ---------------------------------------------------------------------------
// UI State helpers
// ---------------------------------------------------------------------------

enum class BrushMode { INCLUDE, EXCLUDE }

enum class FinishBackgroundType { TRANSPARENT, SOLID, GRADIENT, IMAGE }

/**
 * Non-destructive finishing choices applied after the cut-out is made.
 * Colors are stored as ARGB ints so this model stays independent of Compose.
 */
data class FinishRecipe(
    val backgroundType: FinishBackgroundType = FinishBackgroundType.TRANSPARENT,
    val backgroundPrimaryColor: Int = 0xFFFFF4E8.toInt(),
    val backgroundSecondaryColor: Int = 0xFFFFD4C4.toInt(),
    val backgroundImagePath: String? = null,
    val outlineEnabled: Boolean = false,
    val outlineColor: Int = 0xFFFFFFFF.toInt(),
    val outlineWidth: Float = 10f,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val text: String = "",
    val emoji: String = "",
)

data class EditorBrushState(
    val mode: BrushMode = BrushMode.INCLUDE,
    val radius: Float = 12f,
    val opacity: Float = 1f,
)

sealed interface EditorUiState {
    data object Idle : EditorUiState
    data object Loading : EditorUiState
    data class SegmentationReady(
        val originalBitmap: android.graphics.Bitmap,
        val previewBitmap: android.graphics.Bitmap,
        /** Transparent black over the pixels that are currently outside the selection. */
        val selectionDimBitmap: android.graphics.Bitmap,
        /** 512px composition preview including the current finishing recipe. */
        val finishedPreviewBitmap: android.graphics.Bitmap = previewBitmap,
    ) : EditorUiState
    data class Error(val message: String) : EditorUiState
    data object Saved : EditorUiState
}

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data class Ready(val stickers: List<Sticker>) : GalleryUiState
    data object Empty : GalleryUiState
}

enum class BatchItemStatus { QUEUED, PROCESSING, COMPLETE, FAILED, CANCELLED }

data class BatchImportItem(
    val uriString: String,
    val displayName: String,
    val status: BatchItemStatus = BatchItemStatus.QUEUED,
    val errorMessage: String? = null,
)

data class BatchImportUiState(
    val items: List<BatchImportItem> = emptyList(),
    val isRunning: Boolean = false,
    val currentIndex: Int? = null,
) {
    val completedCount: Int get() = items.count { it.status == BatchItemStatus.COMPLETE }
    val failedCount: Int get() = items.count { it.status == BatchItemStatus.FAILED }
    val pendingCount: Int get() = items.count {
        it.status == BatchItemStatus.QUEUED || it.status == BatchItemStatus.CANCELLED
    }
    val isFinished: Boolean get() = items.isNotEmpty() && pendingCount == 0 && !isRunning
}
