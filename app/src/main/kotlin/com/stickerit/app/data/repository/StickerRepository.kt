package com.stickerit.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.stickerit.app.data.local.StickerDao
import com.stickerit.app.data.model.Sticker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: StickerDao,
) {

    /** Stream of all stickers ordered by sortOrder */
    val stickers: Flow<List<Sticker>> = dao.observeAll()

    /** Decode a URI to a Bitmap, handling content:// and file:// schemes */
    suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    /**
     * Save a processed sticker [bitmap] (ARGB_8888 with transparency) to internal
     * storage as a lossless WebP file and persist a [Sticker] record to the database.
     *
     * @return the newly created [Sticker] entity.
     */
    suspend fun saveSticker(bitmap: Bitmap, name: String): Sticker = withContext(Dispatchers.IO) {
        val stickersDir = File(context.filesDir, "stickers").apply { mkdirs() }
        val fileName = "sticker_${System.currentTimeMillis()}.webp"
        val file = File(stickersDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
        }

        val sticker = Sticker(
            filePath = file.absolutePath,
            name = name,
            width = bitmap.width,
            height = bitmap.height,
        )
        val id = dao.insert(sticker)
        sticker.copy(id = id)
    }

    suspend fun deleteSticker(sticker: Sticker) = withContext(Dispatchers.IO) {
        // Delete physical file
        File(sticker.filePath).delete()
        dao.delete(sticker)
    }

    suspend fun reorderStickers(stickers: List<Sticker>) = withContext(Dispatchers.IO) {
        dao.reorder(stickers)
    }

    suspend fun markAddedToGboard(stickerId: Long) = withContext(Dispatchers.IO) {
        dao.markAddedToGboard(stickerId)
    }

    suspend fun renameSticker(sticker: Sticker, newName: String) = withContext(Dispatchers.IO) {
        dao.update(sticker.copy(name = newName))
    }

    /** Return a [File] for sharing via a FileProvider */
    fun getStickerFile(sticker: Sticker): File = File(sticker.filePath)

    /** Load a sticker bitmap from its file path */
    suspend fun loadStickerBitmap(sticker: Sticker): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(sticker.filePath) }.getOrNull()
    }
}
