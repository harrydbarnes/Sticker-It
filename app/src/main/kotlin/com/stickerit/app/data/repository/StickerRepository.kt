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
import java.io.ByteArrayOutputStream
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
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048)
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
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

        // WhatsApp accepts static stickers only up to 100 KB. Lossy WebP is required
        // for photographic cut-outs to fit that limit reliably while retaining alpha.
        val encoded = encodeForWhatsApp(bitmap)
        FileOutputStream(file).use { it.write(encoded) }

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

    suspend fun renameSticker(sticker: Sticker, newName: String) = withContext(Dispatchers.IO) {
        dao.update(sticker.copy(name = newName))
    }

    /** Return a [File] for sharing via a FileProvider */
    fun getStickerFile(sticker: Sticker): File = File(sticker.filePath)

    /** Load a sticker bitmap from its file path */
    suspend fun loadStickerBitmap(sticker: Sticker): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(sticker.filePath) }.getOrNull()
    }

    private fun encodeForWhatsApp(bitmap: Bitmap): ByteArray {
        for (quality in 90 downTo 30 step 10) {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, output)
            val bytes = output.toByteArray()
            if (bytes.size <= 100 * 1024) return bytes
        }
        error("This image is too detailed for WhatsApp's 100 KB sticker limit. Try a simpler cut-out.")
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (width / sample > maxDimension || height / sample > maxDimension) sample *= 2
        return sample
    }
}
