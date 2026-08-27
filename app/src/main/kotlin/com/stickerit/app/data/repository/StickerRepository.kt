package com.stickerit.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.stickerit.app.data.local.StickerDao
import com.stickerit.app.data.model.FinishBackgroundType
import com.stickerit.app.data.model.FinishRecipe
import com.stickerit.app.data.model.Sticker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: StickerDao,
) {

    /** Stream of all stickers ordered by sortOrder */
    val stickers: Flow<List<Sticker>> = dao.observeAll()

    data class EditableSticker(
        val originalBitmap: Bitmap,
        val confidenceMask: FloatArray,
        val maskWidth: Int,
        val maskHeight: Int,
        val finishRecipe: FinishRecipe = FinishRecipe(),
        val backgroundBitmap: Bitmap? = null,
    )

    /** Decode a URI to a Bitmap, handling content:// and file:// schemes */
    suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1080)
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    /** Persist a user-selected finishing background inside the app's private files. */
    suspend fun persistBackgroundImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = loadBitmapFromUri(uri) ?: return@withContext null
        val directory = File(stickersDirectory(), "backgrounds").apply { mkdirs() }
        val file = File(directory, "background_${UUID.randomUUID().toString().take(12)}.webp")
        try {
            writeBytesAtomically(file, encodeSource(bitmap))
            file.absolutePath
        } catch (_: Exception) {
            file.delete()
            null
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    suspend fun loadBitmapFromPath(path: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    /**
     * Save a processed sticker [bitmap] (ARGB_8888 with transparency) to internal
     * storage as a lossless WebP file and persist a [Sticker] record to the database.
     *
     * @return the newly created [Sticker] entity.
     */
    suspend fun getSticker(id: Long): Sticker? = withContext(Dispatchers.IO) { dao.getById(id) }

    /**
     * Save a sticker and, when supplied, the source/mask pair that makes it editable later.
     * The source and mask are private app files; WhatsApp continues to receive only the
     * flattened WebP through the existing provider.
     */
    suspend fun saveSticker(
        bitmap: Bitmap,
        name: String,
        originalBitmap: Bitmap? = null,
        confidenceMask: FloatArray? = null,
        maskWidth: Int = originalBitmap?.width ?: bitmap.width,
        maskHeight: Int = originalBitmap?.height ?: bitmap.height,
        finishRecipe: FinishRecipe = FinishRecipe(),
    ): Sticker = withContext(Dispatchers.IO) {
        val stickersDir = stickersDirectory()
        val baseName = "sticker_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val file = File(stickersDir, "$baseName.webp")
        val sourceFile = originalBitmap?.let { File(stickersDir, "$baseName.source.webp") }
        val maskFile = confidenceMask?.let { File(stickersDir, "$baseName.mask") }

        try {
            writeBytesAtomically(file, encodeForWhatsApp(bitmap))
            if (originalBitmap != null && confidenceMask != null) {
                writeBytesAtomically(sourceFile!!, encodeSource(originalBitmap))
                writeMaskAtomically(maskFile!!, confidenceMask, maskWidth, maskHeight)
            }

            val sticker = Sticker(
                filePath = file.absolutePath,
                name = name,
                width = bitmap.width,
                height = bitmap.height,
                sourceFilePath = sourceFile?.absolutePath,
                maskFilePath = maskFile?.absolutePath,
                finishRecipeJson = finishRecipeToJson(finishRecipe),
            )
            val id = dao.insert(sticker)
            sticker.copy(id = id)
        } catch (error: Exception) {
            file.delete()
            sourceFile?.delete()
            maskFile?.delete()
            throw error
        }
    }

    /** Update the existing gallery item while retaining its identity and pack references. */
    suspend fun updateSticker(
        sticker: Sticker,
        bitmap: Bitmap,
        originalBitmap: Bitmap,
        confidenceMask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        name: String,
        finishRecipe: FinishRecipe = FinishRecipe(),
    ): Sticker = withContext(Dispatchers.IO) {
        val stickersDir = stickersDirectory()
        val baseName = File(sticker.filePath).nameWithoutExtension
        val sourceFile = sticker.sourceFilePath?.let(::File) ?: File(stickersDir, "$baseName.source.webp")
        val maskFile = sticker.maskFilePath?.let(::File) ?: File(stickersDir, "$baseName.mask")

        writeBytesAtomically(File(sticker.filePath), encodeForWhatsApp(bitmap))
        writeBytesAtomically(sourceFile, encodeSource(originalBitmap))
        writeMaskAtomically(maskFile, confidenceMask, maskWidth, maskHeight)

        val updated = sticker.copy(
            name = name,
            width = bitmap.width,
            height = bitmap.height,
            sourceFilePath = sourceFile.absolutePath,
            maskFilePath = maskFile.absolutePath,
            finishRecipeJson = finishRecipeToJson(finishRecipe),
        )
        dao.update(updated)
        val previousBackgroundPath = finishRecipeFromJson(sticker.finishRecipeJson).backgroundImagePath
        if (previousBackgroundPath != finishRecipe.backgroundImagePath) {
            deleteBackgroundIfOwned(previousBackgroundPath)
        }
        updated
    }

    /** Load an editable source and current mask, falling back to the final sticker alpha. */
    suspend fun loadEditableSticker(sticker: Sticker): EditableSticker? = withContext(Dispatchers.IO) {
        try {
            val finishRecipe = finishRecipeFromJson(sticker.finishRecipeJson)
            val backgroundBitmap = finishRecipe.backgroundImagePath
                ?.let { BitmapFactory.decodeFile(it) }
            val sourcePath = sticker.sourceFilePath
            val maskPath = sticker.maskFilePath
            if (!sourcePath.isNullOrBlank() && !maskPath.isNullOrBlank()) {
                val source = BitmapFactory.decodeFile(sourcePath)
                val mask = readMask(File(maskPath))
                if (source != null && mask != null &&
                    source.width == mask.width && source.height == mask.height
                ) {
                    return@withContext EditableSticker(
                        originalBitmap = source,
                        confidenceMask = mask.values,
                        maskWidth = mask.width,
                        maskHeight = mask.height,
                        finishRecipe = finishRecipe,
                        backgroundBitmap = backgroundBitmap,
                    )
                }
                source?.takeUnless { it.isRecycled }?.recycle()
            }

            // Stickers created before editable storage can still be refined. Their
            // transparent alpha becomes the initial mask and the 512px asset is used
            // as the editable source.
            val legacy = BitmapFactory.decodeFile(sticker.filePath) ?: return@withContext null
            val pixels = IntArray(legacy.width * legacy.height)
            legacy.getPixels(pixels, 0, legacy.width, 0, 0, legacy.width, legacy.height)
            val mask = FloatArray(pixels.size) { index -> (pixels[index] ushr 24) / 255f }
            EditableSticker(
                originalBitmap = legacy,
                confidenceMask = mask,
                maskWidth = legacy.width,
                maskHeight = legacy.height,
                finishRecipe = finishRecipe,
                backgroundBitmap = backgroundBitmap,
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteSticker(sticker: Sticker) = withContext(Dispatchers.IO) {
        // Delete physical file
        File(sticker.filePath).delete()
        sticker.sourceFilePath?.let(::File)?.delete()
        sticker.maskFilePath?.let(::File)?.delete()
        deleteBackgroundIfOwned(finishRecipeFromJson(sticker.finishRecipeJson).backgroundImagePath)
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
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        for (quality in 90 downTo 30 step 10) {
            val output = ByteArrayOutputStream()
            bitmap.compress(format, quality, output)
            val bytes = output.toByteArray()
            if (bytes.size <= 100 * 1024) return bytes
        }
        error("This image is too detailed for WhatsApp's 100 KB sticker limit. Try a simpler cut-out.")
    }

    private fun stickersDirectory(): File = File(context.filesDir, "stickers").apply { mkdirs() }

    private fun encodeSource(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            Bitmap.CompressFormat.WEBP
        }
        check(bitmap.compress(format, 100, output)) { "Could not persist editable source" }
        return output.toByteArray()
    }

    private fun writeBytesAtomically(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { it.write(bytes) }
            if (target.exists() && !target.delete()) error("Could not replace ${target.name}")
            check(temporary.renameTo(target)) { "Could not finish writing ${target.name}" }
        } finally {
            temporary.delete()
        }
    }

    private fun writeMaskAtomically(target: File, values: FloatArray, width: Int, height: Int) {
        require(width > 0 && height > 0) { "Invalid editable mask dimensions" }
        require(values.size >= width * height) { "Editable mask is smaller than its dimensions" }
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                output.writeInt(MASK_FORMAT_VERSION)
                output.writeInt(width)
                output.writeInt(height)
                repeat(width * height) { output.writeFloat(values[it]) }
            }
            if (target.exists() && !target.delete()) error("Could not replace ${target.name}")
            check(temporary.renameTo(target)) { "Could not finish writing ${target.name}" }
        } finally {
            temporary.delete()
        }
    }

    private fun finishRecipeToJson(recipe: FinishRecipe): String = JSONObject().apply {
        put("backgroundType", recipe.backgroundType.name)
        put("backgroundPrimaryColor", recipe.backgroundPrimaryColor)
        put("backgroundSecondaryColor", recipe.backgroundSecondaryColor)
        put("backgroundImagePath", recipe.backgroundImagePath ?: JSONObject.NULL)
        put("outlineEnabled", recipe.outlineEnabled)
        put("outlineColor", recipe.outlineColor)
        put("outlineWidth", recipe.outlineWidth.toDouble())
        put("scale", recipe.scale.toDouble())
        put("offsetX", recipe.offsetX.toDouble())
        put("offsetY", recipe.offsetY.toDouble())
        put("text", recipe.text)
        put("emoji", recipe.emoji)
    }.toString()

    private fun finishRecipeFromJson(value: String?): FinishRecipe {
        if (value.isNullOrBlank()) return FinishRecipe()
        val defaults = FinishRecipe()
        return runCatching {
            val json = JSONObject(value)
            val backgroundType = runCatching {
                FinishBackgroundType.valueOf(json.optString("backgroundType"))
            }.getOrDefault(defaults.backgroundType)
            FinishRecipe(
                backgroundType = backgroundType,
                backgroundPrimaryColor = json.optInt(
                    "backgroundPrimaryColor",
                    defaults.backgroundPrimaryColor,
                ),
                backgroundSecondaryColor = json.optInt(
                    "backgroundSecondaryColor",
                    defaults.backgroundSecondaryColor,
                ),
                backgroundImagePath = json.optString("backgroundImagePath", "")
                    .takeIf { it.isNotBlank() },
                outlineEnabled = json.optBoolean("outlineEnabled", defaults.outlineEnabled),
                outlineColor = json.optInt("outlineColor", defaults.outlineColor),
                outlineWidth = json.optDouble(
                    "outlineWidth",
                    defaults.outlineWidth.toDouble(),
                ).toFloat(),
                scale = json.optDouble("scale", defaults.scale.toDouble()).toFloat(),
                offsetX = json.optDouble("offsetX", defaults.offsetX.toDouble()).toFloat(),
                offsetY = json.optDouble("offsetY", defaults.offsetY.toDouble()).toFloat(),
                text = json.optString("text", defaults.text),
                emoji = json.optString("emoji", defaults.emoji),
            )
        }.getOrDefault(defaults)
    }

    private fun deleteBackgroundIfOwned(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val backgroundDirectory = File(stickersDirectory(), "backgrounds").canonicalFile
            val candidate = File(path).canonicalFile
            if (candidate.parentFile == backgroundDirectory) candidate.delete()
        }
    }

    private data class StoredMask(val values: FloatArray, val width: Int, val height: Int)

    private fun readMask(file: File): StoredMask? {
        if (!file.isFile) return null
        return DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            if (input.readInt() != MASK_FORMAT_VERSION) return@use null
            val width = input.readInt()
            val height = input.readInt()
            if (width <= 0 || height <= 0 || width.toLong() * height > MAX_MASK_PIXELS) return@use null
            val values = FloatArray(width * height) { input.readFloat().coerceIn(0f, 1f) }
            StoredMask(values, width, height)
        }
    }

    companion object {
        private const val MASK_FORMAT_VERSION = 1
        private const val MAX_MASK_PIXELS = 4_000_000L
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (width / sample > maxDimension || height / sample > maxDimension) sample *= 2
        return sample
    }
}
