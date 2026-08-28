package com.stickerit.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.stickerit.app.data.local.StickerDao
import com.stickerit.app.data.local.StickerPackDao
import com.stickerit.app.data.model.DEFAULT_STICKER_PACK_ID
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.model.StickerPackEntity
import com.stickerit.app.data.model.StickerPackItemEntity
import com.stickerit.app.data.provider.WhatsAppPackStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns named pack records and their ordered sticker metadata.
 *
 * Room is the source of truth. The legacy JSON manifest is read once only to
 * preserve the old single-pack selection during the v3 -> v4 migration.
 */
@Singleton
class StickerPackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packDao: StickerPackDao,
    private val stickerDao: StickerDao,
    private val stickerRepository: StickerRepository,
    private val legacyStore: WhatsAppPackStore,
) {

    val packs: Flow<List<StickerPackEntity>> = packDao.observePacks()

    fun items(packId: String): Flow<List<StickerPackItemEntity>> = packDao.observeItems(packId)

    /** Ensure a fresh install has a useful starting point without recreating a deleted pack. */
    suspend fun migrateLegacyPackIfNeeded() = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(LEGACY_MIGRATION_KEY, false)) return@withContext

        val defaultPack = packDao.getPack(DEFAULT_STICKER_PACK_ID) ?: StickerPackEntity(
            id = DEFAULT_STICKER_PACK_ID,
            name = "Sticker It library",
            trayImageFileName = DEFAULT_TRAY_IMAGE,
        ).also(packDao::insertPack)

        legacyStore.readPack()
            ?.takeIf { it.identifier == DEFAULT_STICKER_PACK_ID }
            ?.let { legacy ->
                val stickersByName = stickerDao.getAll().associateBy { File(it.filePath).name }
                val items = legacy.fileNames.mapIndexedNotNull { index, fileName ->
                    stickersByName[fileName]?.let { sticker ->
                        StickerPackItemEntity(
                            packId = DEFAULT_STICKER_PACK_ID,
                            stickerId = sticker.id,
                            sortOrder = index,
                            emojis = "😀",
                            accessibilityText = sticker.name,
                        )
                    }
                }
                packDao.replaceItems(DEFAULT_STICKER_PACK_ID, items)
                packDao.updatePack(
                    defaultPack.copy(
                        name = legacy.name.ifBlank { defaultPack.name },
                        imageDataVersion = legacy.imageDataVersion,
                    ),
                )
            }

        preferences.edit().putBoolean(LEGACY_MIGRATION_KEY, true).apply()
    }

    suspend fun createPack(requestedName: String): StickerPackEntity = withContext(Dispatchers.IO) {
        val existing = packDao.getAllPacks()
        val name = uniqueName(requestedName, existing.map { it.name })
        val id = "pack_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val pack = StickerPackEntity(
            id = id,
            name = name,
            trayImageFileName = "whatsapp_tray_${id.removePrefix("pack_")}.png",
            sortOrder = existing.size,
        )
        packDao.insertPack(pack)
        pack
    }

    suspend fun renamePack(packId: String, requestedName: String): Boolean = withContext(Dispatchers.IO) {
        val pack = packDao.getPack(packId) ?: return@withContext false
        val otherNames = packDao.getAllPacks().filterNot { it.id == packId }.map { it.name }
        val name = uniqueName(requestedName, otherNames)
        packDao.updatePack(
            pack.copy(
                name = name,
                imageDataVersion = System.currentTimeMillis().toString(),
            ),
        )
        true
    }

    /** Keeps one pack available so the WhatsApp action never opens into a dead end. */
    suspend fun deletePack(packId: String): Boolean = withContext(Dispatchers.IO) {
        if (packDao.countPacks() <= 1) return@withContext false
        val pack = packDao.getPack(packId) ?: return@withContext false
        packDao.deletePack(packId)
        File(context.filesDir, pack.trayImageFileName).takeIf { it.name == pack.trayImageFileName }?.delete()
        true
    }

    suspend fun setTrayImage(packId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val pack = packDao.getPack(packId) ?: return@withContext false
        val source = stickerRepository.loadBitmapFromUri(uri) ?: return@withContext false
        val tray = Bitmap.createScaledBitmap(source, TRAY_SIZE, TRAY_SIZE, true)
        val target = File(context.filesDir, pack.trayImageFileName)
        try {
            val bytes = ByteArrayOutputStream().use { output ->
                check(tray.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
            writeBytesAtomically(target, bytes)
            packDao.updatePack(
                pack.copy(
                    trayImageIsCustom = true,
                    imageDataVersion = System.currentTimeMillis().toString(),
                ),
            )
            true
        } catch (_: Exception) {
            false
        } finally {
            if (tray !== source && !tray.isRecycled) tray.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    /** Replace the pack's ordered contents while retaining existing per-sticker metadata. */
    suspend fun replaceItems(packId: String, stickers: List<Sticker>): StickerPackEntity? = withContext(Dispatchers.IO) {
        val pack = packDao.getPack(packId) ?: return@withContext null
        val currentItems = packDao.getItems(packId)
        val existing = currentItems.associateBy { it.stickerId }
        val selectedById = stickers.distinctBy { it.id }.associateBy { it.id }
        // Preserve the order the user set in the pack manager. Newly selected
        // stickers are appended in the order they appear in the gallery.
        val orderedStickers = currentItems.mapNotNull { selectedById[it.stickerId] } +
            stickers.distinctBy { it.id }.filter { it.id !in existing }
        val items = orderedStickers.take(30).mapIndexed { index, sticker ->
            val previous = existing[sticker.id]
            StickerPackItemEntity(
                packId = packId,
                stickerId = sticker.id,
                sortOrder = index,
                emojis = previous?.emojis?.ifBlank { DEFAULT_EMOJIS } ?: DEFAULT_EMOJIS,
                accessibilityText = previous?.accessibilityText?.ifBlank { sticker.name } ?: sticker.name,
            )
        }
        packDao.replaceItems(packId, items)
        pack.copy(imageDataVersion = System.currentTimeMillis().toString()).also(packDao::updatePack)
    }

    suspend fun reorderItems(packId: String, orderedStickerIds: List<Long>) = withContext(Dispatchers.IO) {
        val current = packDao.getItems(packId)
        if (current.isEmpty()) return@withContext
        val byId = current.associateBy { it.stickerId }
        val ordered = orderedStickerIds.distinct().mapNotNull { byId[it] } +
            current.filter { it.stickerId !in orderedStickerIds }
        packDao.replaceItems(
            packId,
            ordered.mapIndexed { index, item -> item.copy(sortOrder = index) },
        )
        packDao.getPack(packId)?.let { packDao.updatePack(it.copy(imageDataVersion = System.currentTimeMillis().toString())) }
    }

    suspend fun updateItemMetadata(
        packId: String,
        stickerId: Long,
        emojis: String,
        accessibilityText: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val item = packDao.getItems(packId).firstOrNull { it.stickerId == stickerId }
            ?: return@withContext false
        val normalizedEmojis = emojis.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(10)
            .joinToString(",")
            .ifBlank { DEFAULT_EMOJIS }
        val description = accessibilityText.trim().take(MAX_ACCESSIBILITY_LENGTH).ifBlank { "Sticker" }
        packDao.updateItem(item.copy(emojis = normalizedEmojis, accessibilityText = description))
        packDao.getPack(packId)?.let { packDao.updatePack(it.copy(imageDataVersion = System.currentTimeMillis().toString())) }
        true
    }

    private fun uniqueName(requestedName: String, existingNames: List<String>): String {
        val base = requestedName.trim().take(MAX_NAME_LENGTH).ifBlank { "New pack" }
        val taken = existingNames.map { it.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var suffix = 2
        while (true) {
            val suffixText = " $suffix"
            val candidate = base.take((MAX_NAME_LENGTH - suffixText.length).coerceAtLeast(1)) + suffixText
            if (candidate.lowercase() !in taken) return candidate
            suffix++
        }
    }

    private fun writeBytesAtomically(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            target.parentFile?.mkdirs()
            temporary.outputStream().use { it.write(bytes) }
            if (target.exists() && !target.delete()) error("Could not replace ${target.name}")
            check(temporary.renameTo(target)) { "Could not finish writing ${target.name}" }
        } finally {
            temporary.delete()
        }
    }

    companion object {
        const val DEFAULT_TRAY_IMAGE = "whatsapp_tray.png"
        private const val PREFERENCES_NAME = "sticker_pack_preferences"
        private const val LEGACY_MIGRATION_KEY = "legacy_single_pack_migrated"
        private const val DEFAULT_EMOJIS = "😀"
        private const val MAX_NAME_LENGTH = 40
        private const val MAX_ACCESSIBILITY_LENGTH = 120
        private const val TRAY_SIZE = 96
    }
}
