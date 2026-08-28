package com.stickerit.app.data.backup

import org.json.JSONArray
import org.json.JSONObject

/** One sticker record in a portable Sticker It archive. */
data class BackupStickerRecord(
    val originalId: Long,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int,
    val width: Int,
    val height: Int,
    val legacyPackFlag: Boolean,
    val assetEntry: String,
    val sourceEntry: String?,
    val maskEntry: String?,
    val backgroundEntry: String?,
    val finishRecipeJson: String?,
)

data class BackupPackItemRecord(
    val stickerEntry: String,
    val sortOrder: Int,
    val emojis: String,
    val accessibilityText: String,
)

data class BackupPackRecord(
    val originalId: String,
    val name: String,
    val publisher: String,
    val trayImageEntry: String?,
    val trayImageIsCustom: Boolean,
    val imageDataVersion: String,
    val createdAt: Long,
    val sortOrder: Int,
    val items: List<BackupPackItemRecord>,
)

data class ParsedStickerBackup(
    val stickers: List<BackupStickerRecord>,
    val packs: List<BackupPackRecord>,
)

/**
 * Versioned JSON contract for the zip backup. Paths in the manifest are
 * archive-relative only; private filesystem paths never leave the app.
 */
object StickerBackupFormat {
    const val FORMAT = "stickerit-library"
    const val VERSION = 2
    const val MANIFEST_ENTRY = "manifest.json"
    const val ASSET_DIRECTORY = "assets/"
    const val MAX_STICKERS = 1_000
    const val MAX_PACKS = 100
    const val MAX_PACK_ITEMS = 30
    const val MAX_NAME_LENGTH = 200
    const val MAX_PACK_ID_LENGTH = 80
    const val MAX_PUBLISHER_LENGTH = 200
    const val MAX_IMAGE_DATA_VERSION_LENGTH = 128
    const val MAX_EMOJIS_LENGTH = 512
    const val MAX_ACCESSIBILITY_LENGTH = 120
    const val MAX_RECIPE_LENGTH = 128 * 1024

    fun buildManifest(records: List<BackupStickerRecord>, createdAt: Long): ByteArray =
        buildManifest(records, emptyList(), createdAt)

    fun buildManifest(
        records: List<BackupStickerRecord>,
        packs: List<BackupPackRecord>,
        createdAt: Long,
    ): ByteArray {
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", createdAt)
            .put(
                "stickers",
                JSONArray().apply {
                    records.forEach { record ->
                        put(
                            JSONObject()
                                .put("originalId", record.originalId)
                                .put("name", record.name)
                                .put("createdAt", record.createdAt)
                                .put("sortOrder", record.sortOrder)
                                .put("width", record.width)
                                .put("height", record.height)
                                .put("legacyPackFlag", record.legacyPackFlag)
                                .put("asset", record.assetEntry)
                                .put("source", record.sourceEntry ?: JSONObject.NULL)
                                .put("mask", record.maskEntry ?: JSONObject.NULL)
                                .put("background", record.backgroundEntry ?: JSONObject.NULL)
                                .put("finishRecipeJson", record.finishRecipeJson ?: JSONObject.NULL),
                           )
                       }
                   },
               )
            .put(
                "packs",
                JSONArray().apply {
                    packs.forEach { pack ->
                        put(
                            JSONObject()
                                .put("originalId", pack.originalId)
                                .put("name", pack.name)
                                .put("publisher", pack.publisher)
                                .put("trayImage", pack.trayImageEntry ?: JSONObject.NULL)
                                .put("trayImageIsCustom", pack.trayImageIsCustom)
                                .put("imageDataVersion", pack.imageDataVersion)
                                .put("createdAt", pack.createdAt)
                                .put("sortOrder", pack.sortOrder)
                                .put(
                                    "items",
                                    JSONArray().apply {
                                        pack.items.forEach { item ->
                                            put(
                                                JSONObject()
                                                    .put("sticker", item.stickerEntry)
                                                    .put("sortOrder", item.sortOrder)
                                                    .put("emojis", item.emojis)
                                                    .put("accessibilityText", item.accessibilityText),
                                            )
                                        }
                                    },
                                ),
                        )
                    }
                },
            )
        return json.toString().encodeToByteArray()
    }

    fun parseManifest(bytes: ByteArray): List<BackupStickerRecord> = parseBackupManifest(bytes).stickers

    fun parseBackupManifest(bytes: ByteArray): ParsedStickerBackup {
        val root = runCatching { JSONObject(bytes.decodeToString()) }
            .getOrElse { throw BackupFormatException("The backup manifest is not valid JSON") }
        if (root.optString("format") != FORMAT) {
            throw BackupFormatException("This file is not a Sticker It backup")
        }
        val version = root.optInt("version", -1)
        if (version !in 1..VERSION) {
            throw BackupFormatException("This Sticker It backup version is not supported")
        }
        val array = root.optJSONArray("stickers")
            ?: throw BackupFormatException("The backup manifest has no sticker list")
        if (array.length() > MAX_STICKERS) {
            throw BackupFormatException("The backup contains too many stickers")
        }

        val assetEntries = mutableSetOf<String>()
        val stickers = List(array.length()) { index ->
            val item = array.optJSONObject(index)
                ?: throw BackupFormatException("The backup contains an invalid sticker record")
            val name = item.optString("name").trim()
            if (name.isBlank() || name.length > MAX_NAME_LENGTH) {
                throw BackupFormatException("A sticker name in the backup is invalid")
            }
            val width = item.optInt("width", -1)
            val height = item.optInt("height", -1)
            if (width !in 1..10_000 || height !in 1..10_000) {
                throw BackupFormatException("A sticker dimension in the backup is invalid")
            }
            val recipe = optionalText(item, "finishRecipeJson")
            if (recipe != null && recipe.length > MAX_RECIPE_LENGTH) {
                throw BackupFormatException("A finishing recipe in the backup is too large")
            }
            val assetEntry = requiredEntry(item, "asset")
            if (!assetEntries.add(assetEntry)) {
                throw BackupFormatException("The backup contains duplicate sticker assets")
            }
            BackupStickerRecord(
                originalId = item.optLong("originalId", 0L).coerceAtLeast(0L),
                name = name,
                createdAt = item.optLong("createdAt", 0L).coerceAtLeast(0L),
                sortOrder = item.optInt("sortOrder", 0),
                width = width,
                height = height,
                legacyPackFlag = item.optBoolean("legacyPackFlag", false),
                assetEntry = assetEntry,
                sourceEntry = optionalEntry(item, "source"),
                maskEntry = optionalEntry(item, "mask"),
                backgroundEntry = optionalEntry(item, "background"),
                finishRecipeJson = recipe,
            )
        }

        val packs = if (version >= 2) parsePacks(root) else emptyList()
        return ParsedStickerBackup(stickers = stickers, packs = packs)
    }

    fun isSafeEntryName(name: String): Boolean {
        if (!name.startsWith(ASSET_DIRECTORY)) return false
        val fileName = name.removePrefix(ASSET_DIRECTORY)
        return fileName.isNotBlank() &&
            fileName != "." &&
            fileName != ".." &&
            !fileName.contains('/') &&
            !fileName.contains('\\') &&
            !fileName.contains("..")
    }

    private fun requiredEntry(item: JSONObject, key: String): String {
        return optionalEntry(item, key)
            ?: throw BackupFormatException("The backup is missing a required asset")
    }

    private fun optionalEntry(item: JSONObject, key: String): String? {
        val value = item.optString(key, "").trim()
        if (value.isBlank()) return null
        if (!isSafeEntryName(value)) {
            throw BackupFormatException("The backup contains an unsafe file path")
        }
        return value
    }

    private fun optionalText(item: JSONObject, key: String): String? =
        item.optString(key, "").takeIf { it.isNotBlank() }

    private fun parsePacks(root: JSONObject): List<BackupPackRecord> {
        val array = root.optJSONArray("packs") ?: JSONArray()
        if (array.length() > MAX_PACKS) {
            throw BackupFormatException("The backup contains too many packs")
        }
        val packIds = mutableSetOf<String>()
        return List(array.length()) { index ->
            val item = array.optJSONObject(index)
                ?: throw BackupFormatException("The backup contains an invalid pack record")
            val originalId = item.optString("originalId").trim()
            if (!isSafePackId(originalId)) {
                throw BackupFormatException("A pack identifier in the backup is invalid")
            }
            if (!packIds.add(originalId)) {
                throw BackupFormatException("The backup contains duplicate pack identifiers")
            }
            val name = item.optString("name").trim()
            if (name.isBlank() || name.length > MAX_NAME_LENGTH) {
                throw BackupFormatException("A pack name in the backup is invalid")
            }
            val publisher = item.optString("publisher", "Sticker It").trim()
                .ifBlank { "Sticker It" }
            if (publisher.length > MAX_PUBLISHER_LENGTH) {
                throw BackupFormatException("A pack publisher in the backup is invalid")
            }
            val itemsArray = item.optJSONArray("items")
                ?: throw BackupFormatException("A pack in the backup has no sticker list")
            if (itemsArray.length() > MAX_PACK_ITEMS) {
                throw BackupFormatException("A pack in the backup contains too many stickers")
            }
            val stickerEntries = mutableSetOf<String>()
            val items = List(itemsArray.length()) { itemIndex ->
                val packItem = itemsArray.optJSONObject(itemIndex)
                    ?: throw BackupFormatException("The backup contains an invalid pack sticker")
                val emojis = packItem.optString("emojis", "😀").trim()
                    .ifBlank { "😀" }
                val accessibilityText = packItem.optString("accessibilityText", "Sticker").trim()
                    .ifBlank { "Sticker" }
                if (emojis.length > MAX_EMOJIS_LENGTH ||
                    accessibilityText.length > MAX_ACCESSIBILITY_LENGTH
                ) {
                    throw BackupFormatException("Pack sticker metadata in the backup is too large")
                }
                val stickerEntry = requiredEntry(packItem, "sticker")
                if (!stickerEntries.add(stickerEntry)) {
                    throw BackupFormatException("A pack contains duplicate sticker entries")
                }
                BackupPackItemRecord(
                    stickerEntry = stickerEntry,
                    sortOrder = packItem.optInt("sortOrder", itemIndex),
                    emojis = emojis,
                    accessibilityText = accessibilityText,
                )
            }
            val imageDataVersion = item.optString("imageDataVersion", "1").trim().ifBlank { "1" }
            if (imageDataVersion.length > MAX_IMAGE_DATA_VERSION_LENGTH) {
                throw BackupFormatException("A pack version in the backup is invalid")
            }
            BackupPackRecord(
                originalId = originalId,
                name = name,
                publisher = publisher,
                trayImageEntry = optionalEntry(item, "trayImage"),
                trayImageIsCustom = item.optBoolean("trayImageIsCustom", false),
                imageDataVersion = imageDataVersion,
                createdAt = item.optLong("createdAt", 0L).coerceAtLeast(0L),
                sortOrder = item.optInt("sortOrder", index),
                items = items,
            )
        }
    }

    fun isSafePackId(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_PACK_ID_LENGTH &&
            value.all { it.isLetterOrDigit() || it == '_' || it == '-' }
}

class BackupFormatException(message: String) : IllegalArgumentException(message)
