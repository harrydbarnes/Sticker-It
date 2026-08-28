package com.stickerit.app.data.backup

import android.content.Context
import android.net.Uri
import com.stickerit.app.data.local.StickerDao
import com.stickerit.app.data.local.StickerPackDao
import com.stickerit.app.data.model.DEFAULT_STICKER_PACK_ID
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.model.StickerPackEntity
import com.stickerit.app.data.model.StickerPackItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface StickerBackupResult {
    data class Exported(val stickerCount: Int, val packCount: Int = 0) : StickerBackupResult
    data class Imported(
        val importedCount: Int,
        val skippedCount: Int,
        val importedPackCount: Int = 0,
        val skippedPackCount: Int = 0,
    ) : StickerBackupResult
    data object Failed : StickerBackupResult
}

/** Creates and restores portable Sticker It library archives. */
@Singleton
class StickerBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stickerDao: StickerDao,
    private val packDao: StickerPackDao,
) {

    suspend fun exportLibrary(destination: Uri): StickerBackupResult = withContext(Dispatchers.IO) {
        try {
            val stickers = stickerDao.getAll()
            check(stickers.size <= StickerBackupFormat.MAX_STICKERS) {
                "The library contains too many stickers for one backup"
            }
            val files = LinkedHashMap<String, File>()
            val paths = mutableMapOf<String, String>()
            val stickerAssetEntries = mutableMapOf<Long, String>()
            val stickersById = stickers.associateBy { it.id }
            val stickerRoot = File(context.filesDir, "stickers")
            val backgroundRoot = File(stickerRoot, "backgrounds")

            fun register(file: File, preferredEntry: String): String {
                val canonicalPath = file.canonicalPath
                return paths.getOrPut(canonicalPath) {
                    files[preferredEntry] = file
                    preferredEntry
                }
            }

            // Keep one final-sticker entry per record. A shared entry would make
            // pack membership ambiguous when two records happen to have the
            // same final bytes.
            fun registerUnique(file: File, entry: String): String {
                files[entry] = file
                return entry
            }

            val records = stickers.mapIndexed { index, sticker ->
                val asset = ownedFile(sticker.filePath, stickerRoot)
                    ?: error("A sticker file is missing")
                val assetEntry = registerUnique(
                    asset,
                    "${StickerBackupFormat.ASSET_DIRECTORY}sticker_$index.webp",
                )
                stickerAssetEntries[sticker.id] = assetEntry

                val sourceEntry = ownedFile(sticker.sourceFilePath, stickerRoot)?.let {
                    register(it, "${StickerBackupFormat.ASSET_DIRECTORY}sticker_${index}_source.webp")
                }
                val maskEntry = ownedFile(sticker.maskFilePath, stickerRoot)?.let {
                    register(it, "${StickerBackupFormat.ASSET_DIRECTORY}sticker_${index}_mask.bin")
                }
                val backgroundPath = backgroundPathFromRecipe(sticker.finishRecipeJson)
                val backgroundEntry = ownedFile(backgroundPath, backgroundRoot)?.let {
                    register(it, "${StickerBackupFormat.ASSET_DIRECTORY}background_$index.webp")
                }

                BackupStickerRecord(
                    originalId = sticker.id,
                    name = sticker.name.trim()
                        .take(StickerBackupFormat.MAX_NAME_LENGTH)
                        .ifBlank { "My Sticker" },
                    createdAt = sticker.createdAt,
                    sortOrder = sticker.sortOrder,
                    width = sticker.width,
                    height = sticker.height,
                    legacyPackFlag = sticker.legacyPackFlag,
                    assetEntry = assetEntry,
                    sourceEntry = sourceEntry,
                    maskEntry = maskEntry,
                    backgroundEntry = backgroundEntry,
                    finishRecipeJson = sanitizeRecipeForBackup(sticker.finishRecipeJson),
                )
            }

            val packs = packDao.getAllPacks()
            check(packs.size <= StickerBackupFormat.MAX_PACKS) {
                "The library contains too many packs for one backup"
            }
            val packRecords = packs.mapIndexed { packIndex, pack ->
                check(StickerBackupFormat.isSafePackId(pack.id)) {
                    "A pack identifier is invalid"
                }
                val packItems = packDao.getItems(pack.id)
                check(packItems.size <= StickerBackupFormat.MAX_PACK_ITEMS) {
                    "A pack contains too many stickers"
                }
                val trayImageEntry = ownedTrayFile(pack.trayImageFileName)?.let { tray ->
                    registerUnique(
                        tray,
                        "${StickerBackupFormat.ASSET_DIRECTORY}pack_tray_$packIndex.png",
                    )
                }
                BackupPackRecord(
                    originalId = pack.id,
                    name = pack.name.trim()
                        .take(StickerBackupFormat.MAX_NAME_LENGTH)
                        .ifBlank { "New pack" },
                    publisher = pack.publisher.trim()
                        .take(StickerBackupFormat.MAX_PUBLISHER_LENGTH)
                        .ifBlank { "Sticker It" },
                    trayImageEntry = trayImageEntry,
                    trayImageIsCustom = pack.trayImageIsCustom && trayImageEntry != null,
                    imageDataVersion = pack.imageDataVersion.trim()
                        .take(StickerBackupFormat.MAX_IMAGE_DATA_VERSION_LENGTH)
                        .ifBlank { "1" },
                    createdAt = pack.createdAt,
                    sortOrder = pack.sortOrder,
                    items = packItems.mapIndexed { itemIndex, item ->
                        val sticker = stickersById[item.stickerId]
                            ?: error("A pack references a missing sticker")
                        BackupPackItemRecord(
                            stickerEntry = stickerAssetEntries[item.stickerId]
                                ?: error("A pack references a sticker without an asset"),
                            sortOrder = item.sortOrder.takeIf { it >= 0 } ?: itemIndex,
                            emojis = item.emojis.trim()
                                .take(StickerBackupFormat.MAX_EMOJIS_LENGTH)
                                .ifBlank { "😀" },
                            accessibilityText = item.accessibilityText.trim()
                                .take(StickerBackupFormat.MAX_ACCESSIBILITY_LENGTH)
                                .ifBlank {
                                    sticker.name.take(StickerBackupFormat.MAX_ACCESSIBILITY_LENGTH)
                                        .ifBlank { "Sticker" }
                                },
                        )
                    },
                )
            }

            val manifest = StickerBackupFormat.buildManifest(
                records,
                packRecords,
                System.currentTimeMillis(),
            )
            context.contentResolver.openOutputStream(destination)
                ?.let { output ->
                    output.use { rawOutput ->
                        ZipOutputStream(BufferedOutputStream(rawOutput)).use { zip ->
                            zip.putNextEntry(ZipEntry(StickerBackupFormat.MANIFEST_ENTRY))
                            zip.write(manifest)
                            zip.closeEntry()
                            files.forEach { (entryName, file) ->
                                zip.putNextEntry(ZipEntry(entryName))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
                ?: error("Could not open the selected destination")

            StickerBackupResult.Exported(stickers.size, packs.size)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            StickerBackupResult.Failed
        }
    }

    /**
     * Imports new sticker assets and pack definitions without replacing
     * existing non-empty data, making the action safe to retry after a restore.
     * The migration-created empty default pack is populated so a reinstall can
     * recover the original library pack.
     */
    suspend fun importLibrary(source: Uri): StickerBackupResult = withContext(Dispatchers.IO) {
        val temporaryDirectory = File(
            context.cacheDir,
            "stickerit-import-${UUID.randomUUID().toString().take(12)}",
        )
        val createdFiles = mutableListOf<File>()
        val insertedIds = mutableListOf<Long>()
        val insertedPackIds = mutableListOf<String>()
        val updatedPacks = mutableListOf<PackSnapshot>()
        val replacedFiles = mutableListOf<ReplacedFile>()

        fun rollback() {
            insertedPackIds.asReversed().forEach { id ->
                runCatching { packDao.deletePack(id) }
            }
            updatedPacks.asReversed().forEach { snapshot ->
                runCatching {
                    packDao.updatePack(snapshot.pack)
                    packDao.replaceItems(snapshot.pack.id, snapshot.items)
                }
            }
            insertedIds.asReversed().forEach { id ->
                runCatching { stickerDao.deleteById(id) }
            }
            createdFiles.asReversed().forEach { it.delete() }
            replacedFiles.asReversed().forEach { replacement ->
                runCatching { copyToOwned(replacement.backup, replacement.target) }
            }
        }

        try {
            temporaryDirectory.mkdirs()
            val entries = extractArchive(source, temporaryDirectory)
            val manifest = entries[StickerBackupFormat.MANIFEST_ENTRY]
                ?: throw BackupFormatException("The backup has no manifest")
            val parsedBackup = StickerBackupFormat.parseBackupManifest(manifest.readBytes())
            val records = parsedBackup.stickers
            val stickerEntries = records.map { it.assetEntry }.toSet()
            require(stickerEntries.size == records.size) {
                "The backup contains duplicate sticker assets"
            }
            records.forEach { record ->
                require(entries.containsKey(record.assetEntry)) { "A sticker asset is missing" }
                require(record.sourceEntry == null || entries.containsKey(record.sourceEntry)) {
                    "An editable source asset is missing"
                }
                require(record.maskEntry == null || entries.containsKey(record.maskEntry)) {
                    "An editable mask asset is missing"
                }
                require(record.backgroundEntry == null || entries.containsKey(record.backgroundEntry)) {
                    "A background asset is missing"
                }
            }
            parsedBackup.packs.forEach { pack ->
                require(pack.trayImageEntry == null || entries.containsKey(pack.trayImageEntry)) {
                    "A pack tray image is missing"
                }
                pack.items.forEach { item ->
                    require(item.stickerEntry in stickerEntries) {
                        "A pack references a sticker that is not in the backup"
                    }
                }
            }

            val existingStickers = stickerDao.getAll()
            val stickerIdsByHash = mutableMapOf<String, Long>()
            existingStickers.forEach { sticker ->
                runCatching { sha256(File(sticker.filePath)) }
                    .onSuccess { hash -> stickerIdsByHash.putIfAbsent(hash, sticker.id) }
            }
            val stickersDirectory = File(context.filesDir, "stickers").apply { mkdirs() }
            val backgroundsDirectory = File(stickersDirectory, "backgrounds").apply { mkdirs() }
            var nextSortOrder = (existingStickers.maxOfOrNull { it.sortOrder } ?: -1) + 1
            var importedCount = 0
            var skippedCount = 0
            val stickerIdsByEntry = mutableMapOf<String, Long>()

            records.forEach { record ->
                val asset = entries.getValue(record.assetEntry)
                val hash = sha256(asset)
                val existingId = stickerIdsByHash[hash]
                if (existingId != null) {
                    skippedCount++
                    stickerIdsByEntry[record.assetEntry] = existingId
                    return@forEach
                }

                val baseName = "sticker_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
                val target = File(stickersDirectory, "$baseName.webp")
                copyToOwned(asset, target)
                createdFiles += target

                val sourceTarget = record.sourceEntry?.let { entry ->
                    File(stickersDirectory, "$baseName.source.webp").also { targetFile ->
                        copyToOwned(entries.getValue(entry), targetFile)
                        createdFiles += targetFile
                    }
                }
                val maskTarget = record.maskEntry?.let { entry ->
                    File(stickersDirectory, "$baseName.mask").also { targetFile ->
                        copyToOwned(entries.getValue(entry), targetFile)
                        createdFiles += targetFile
                    }
                }
                val backgroundTarget = record.backgroundEntry?.let { entry ->
                    File(backgroundsDirectory, "background_${UUID.randomUUID().toString().take(12)}.webp")
                        .also { targetFile ->
                            copyToOwned(entries.getValue(entry), targetFile)
                            createdFiles += targetFile
                        }
                }

                val importedSticker = Sticker(
                    filePath = target.absolutePath,
                    name = record.name,
                    createdAt = record.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    sortOrder = nextSortOrder++,
                    width = record.width,
                    height = record.height,
                    legacyPackFlag = record.legacyPackFlag,
                    sourceFilePath = sourceTarget?.absolutePath,
                    maskFilePath = maskTarget?.absolutePath,
                    finishRecipeJson = restoreRecipe(
                        record.finishRecipeJson,
                        backgroundTarget?.absolutePath,
                    ),
                )
                val id = stickerDao.insert(importedSticker)
                insertedIds += id
                stickerIdsByHash[hash] = id
                stickerIdsByEntry[record.assetEntry] = id
                importedCount++
            }

            val existingPacks = packDao.getAllPacks()
            val existingTrayNames = existingPacks.mapTo(mutableSetOf()) { it.trayImageFileName }
            var importedPackCount = 0
            var skippedPackCount = 0

            parsedBackup.packs.forEach { pack ->
                val existingPack = packDao.getPack(pack.originalId)
                if (existingPack != null &&
                    !(pack.originalId == DEFAULT_STICKER_PACK_ID &&
                        packDao.getItems(pack.originalId).isEmpty())
                ) {
                    skippedPackCount++
                    return@forEach
                }

                val targetPackId = pack.originalId
                val trayFileName = if (existingPack != null) {
                    existingPack.trayImageFileName
                } else {
                    importedTrayFileName(targetPackId, existingTrayNames)
                }
                val trayTarget = File(context.filesDir, trayFileName)
                pack.trayImageEntry?.let { entry ->
                    if (trayTarget.exists()) {
                        val previous = File(
                            temporaryDirectory,
                            "previous-tray-${UUID.randomUUID().toString().take(8)}.png",
                        )
                        trayTarget.copyTo(previous, overwrite = true)
                        replacedFiles += ReplacedFile(trayTarget, previous)
                    } else {
                        createdFiles += trayTarget
                    }
                    copyToOwned(entries.getValue(entry), trayTarget)
                }

                val packItems = pack.items.mapIndexed { index, item ->
                    StickerPackItemEntity(
                        packId = targetPackId,
                        stickerId = stickerIdsByEntry[item.stickerEntry]
                            ?: error("A pack references an unavailable sticker"),
                        sortOrder = item.sortOrder.takeIf { it >= 0 } ?: index,
                        emojis = item.emojis,
                        accessibilityText = item.accessibilityText,
                    )
                }.distinctBy { it.stickerId }
                val restoredTrayIsCustom = pack.trayImageIsCustom && pack.trayImageEntry != null
                if (existingPack != null) {
                    updatedPacks += PackSnapshot(
                        pack = existingPack,
                        items = packDao.getItems(existingPack.id),
                    )
                    packDao.updatePack(
                        existingPack.copy(
                            name = pack.name,
                            publisher = pack.publisher,
                            trayImageFileName = trayFileName,
                            trayImageIsCustom = restoredTrayIsCustom,
                            imageDataVersion = pack.imageDataVersion,
                            createdAt = pack.createdAt,
                            sortOrder = pack.sortOrder,
                        ),
                    )
                } else {
                    packDao.insertPack(
                        StickerPackEntity(
                            id = targetPackId,
                            name = pack.name,
                            publisher = pack.publisher,
                            trayImageFileName = trayFileName,
                            trayImageIsCustom = restoredTrayIsCustom,
                            imageDataVersion = pack.imageDataVersion,
                            createdAt = pack.createdAt,
                            sortOrder = pack.sortOrder,
                        ),
                    )
                    insertedPackIds += targetPackId
                    existingTrayNames += trayFileName
                }
                packDao.replaceItems(targetPackId, packItems)
                importedPackCount++
            }

            StickerBackupResult.Imported(
                importedCount = importedCount,
                skippedCount = skippedCount,
                importedPackCount = importedPackCount,
                skippedPackCount = skippedPackCount,
            )
        } catch (error: CancellationException) {
            rollback()
            throw error
        } catch (_: Exception) {
            rollback()
            StickerBackupResult.Failed
        } finally {
            temporaryDirectory.deleteRecursively()
        }
    }

    private fun extractArchive(source: Uri, temporaryDirectory: File): Map<String, File> {
        val extracted = LinkedHashMap<String, File>()
        var entryCount = 0
        var totalBytes = 0L
        val input = context.contentResolver.openInputStream(source)
            ?: error("Could not open the selected backup")
        input.use { rawInput ->
            ZipInputStream(BufferedInputStream(rawInput)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) error("The backup contains too many files")
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (name != StickerBackupFormat.MANIFEST_ENTRY &&
                            !StickerBackupFormat.isSafeEntryName(name)
                        ) {
                            error("The backup contains an unsafe file path")
                        }
                        if (extracted.containsKey(name)) error("The backup contains a duplicate file")
                        val fileName = if (name == StickerBackupFormat.MANIFEST_ENTRY) {
                            StickerBackupFormat.MANIFEST_ENTRY
                        } else {
                            name.removePrefix(StickerBackupFormat.ASSET_DIRECTORY)
                        }
                        val target = File(temporaryDirectory, fileName)
                        val maxEntryBytes = if (name == StickerBackupFormat.MANIFEST_ENTRY) {
                            MAX_MANIFEST_BYTES
                        } else {
                            MAX_ASSET_BYTES
                        }
                        if (entry.size > maxEntryBytes) error("A backup file is too large")

                        var entryBytes = 0L
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalBytes += read
                                if (entryBytes > maxEntryBytes || totalBytes > MAX_ARCHIVE_BYTES) {
                                    error("The backup is too large")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                        extracted[name] = target
                    }
                    zip.closeEntry()
                }
            }
        }
        return extracted
    }

    private fun ownedTrayFile(path: String?): File? {
        if (path.isNullOrBlank() || File(path).name != path) return null
        return runCatching {
            val root = context.filesDir.canonicalFile
            val candidate = File(root, path).canonicalFile
            candidate.takeIf { it.isFile && it.parentFile == root }
        }.getOrNull()
    }

    private fun importedTrayFileName(packId: String, existingNames: Set<String>): String {
        val base = if (packId == DEFAULT_STICKER_PACK_ID) {
            DEFAULT_TRAY_IMAGE
        } else {
            "whatsapp_tray_$packId.png"
        }
        if (base !in existingNames && !File(context.filesDir, base).exists()) return base
        return "whatsapp_tray_import_${UUID.randomUUID().toString().replace("-", "").take(16)}.png"
    }

    private fun ownedFile(path: String?, root: File): File? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val candidate = File(path).canonicalFile
            val canonicalRoot = root.canonicalFile
            candidate.takeIf { it.isFile && it.parentFile == canonicalRoot }
        }.getOrNull()
    }

    private fun backgroundPathFromRecipe(recipe: String?): String? = runCatching {
        JSONObject(recipe ?: return@runCatching null)
            .optString("backgroundImagePath", "")
            .takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun sanitizeRecipeForBackup(recipe: String?): String? = runCatching {
        if (recipe.isNullOrBlank()) return@runCatching null
        JSONObject(recipe).put("backgroundImagePath", JSONObject.NULL).toString()
    }.getOrNull()

    private fun restoreRecipe(recipe: String?, backgroundPath: String?): String? = runCatching {
        if (recipe.isNullOrBlank()) return@runCatching null
        JSONObject(recipe).put(
            "backgroundImagePath",
            backgroundPath ?: JSONObject.NULL,
        ).toString()
    }.getOrNull()

    private fun copyToOwned(source: File, target: File) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            target.parentFile?.mkdirs()
            source.inputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.exists() && !target.delete()) error("Could not replace imported file")
            check(temporary.renameTo(target)) { "Could not finish importing file" }
        } finally {
            temporary.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class PackSnapshot(
        val pack: StickerPackEntity,
        val items: List<StickerPackItemEntity>,
    )

    private data class ReplacedFile(
        val target: File,
        val backup: File,
    )

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
        private const val DEFAULT_TRAY_IMAGE = "whatsapp_tray.png"
        private const val MAX_ZIP_ENTRIES = 4_000
        private const val MAX_MANIFEST_BYTES = 2L * 1024 * 1024
        private const val MAX_ASSET_BYTES = 20L * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 250L * 1024 * 1024
    }
}
