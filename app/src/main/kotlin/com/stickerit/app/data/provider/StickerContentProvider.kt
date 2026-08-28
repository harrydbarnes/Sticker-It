package com.stickerit.app.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.stickerit.app.data.local.StickerDatabase
import com.stickerit.app.data.local.StickerDatabaseFactory
import com.stickerit.app.data.model.StickerPackEntity
import java.io.File
import java.io.FileNotFoundException

/** WhatsApp's documented dynamic sticker-pack provider. */
class StickerContentProvider : ContentProvider() {
    companion object {
        private const val METADATA = 1
        private const val METADATA_ITEM = 2
        private const val STICKERS = 3
        private const val STICKER_ASSET = 4
        private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    }

    private var database: StickerDatabase? = null

    override fun onCreate(): Boolean {
        val appContext = context ?: return false
        val authority = "${appContext.packageName}.stickercontentprovider"
        matcher.addURI(authority, "metadata", METADATA)
        matcher.addURI(authority, "metadata/*", METADATA_ITEM)
        matcher.addURI(authority, "stickers/*", STICKERS)
        matcher.addURI(authority, "stickers_asset/*/*", STICKER_ASSET)
        database = StickerDatabaseFactory.create(appContext)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = when (matcher.match(uri)) {
        METADATA -> metadataCursor(null)
        METADATA_ITEM -> metadataCursor(uri.lastPathSegment)
        STICKERS -> stickerCursor(uri.lastPathSegment)
        else -> null
    }

    private fun packs(): List<StickerPackEntity> = database?.stickerPackDao()?.getAllPacks().orEmpty()

    private fun metadataCursor(identifier: String?): Cursor {
        val cursor = MatrixCursor(WhatsAppStickerContract.metadataColumns)
        val matchingPacks = if (identifier == null) {
            packs()
        } else {
            packs().filter { it.id == identifier }
        }
        matchingPacks.forEach { pack ->
            cursor.addRow(
                arrayOf<Any?>(
                    pack.id,
                    pack.name,
                    pack.publisher,
                    pack.trayImageFileName,
                    pack.imageDataVersion,
                    false,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    false,
                ),
            )
        }
        return cursor
    }

    private fun stickerCursor(identifier: String?): Cursor {
        val cursor = MatrixCursor(WhatsAppStickerContract.stickerColumns)
        val pack = identifier?.let { database?.stickerPackDao()?.getPack(it) } ?: return cursor
        database?.stickerPackDao()?.getPackStickers(pack.id).orEmpty().forEach { row ->
            val fileName = File(row.filePath).name
            if (fileName.isBlank() || fileName == ".") return@forEach
            cursor.addRow(
                arrayOf(
                    fileName,
                    row.emojis.ifBlank { "😀" },
                    row.accessibilityText.ifBlank { "Sticker" },
                ),
            )
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (matcher.match(uri) != STICKER_ASSET || mode != "r") return null
        val parts = uri.pathSegments
        val packId = parts.getOrNull(1) ?: throw FileNotFoundException("Invalid sticker URI")
        val fileName = parts.getOrNull(2) ?: throw FileNotFoundException("Invalid sticker URI")
        if (File(fileName).name != fileName || fileName.isBlank()) {
            throw FileNotFoundException("Invalid sticker filename")
        }

        val pack = database?.stickerPackDao()?.getPack(packId)
            ?: throw FileNotFoundException("No sticker pack")
        val isTray = fileName == pack.trayImageFileName
        val isSticker = database?.stickerPackDao()?.getPackStickers(pack.id).orEmpty()
            .any { File(it.filePath).name == fileName }
        if (!isTray && !isSticker) throw FileNotFoundException("Sticker is not in this pack")

        val root = if (isTray) context!!.filesDir else File(context!!.filesDir, "stickers")
        val file = File(root, fileName)
        val canonicalRoot = root.canonicalFile
        val canonicalFile = file.canonicalFile
        if (canonicalFile.parentFile != canonicalRoot || !canonicalFile.isFile) {
            throw FileNotFoundException("Sticker not found")
        }
        return ParcelFileDescriptor.open(canonicalFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? =
        openFile(uri, mode)?.let { AssetFileDescriptor(it, 0, AssetFileDescriptor.UNKNOWN_LENGTH) }

    override fun getType(uri: Uri): String? {
        if (matcher.match(uri) != STICKER_ASSET) return null
        val fileName = uri.lastPathSegment ?: return null
        val packId = uri.pathSegments.getOrNull(1)
        val isTray = packs().firstOrNull { it.id == packId }?.trayImageFileName == fileName
        return if (isTray) "image/png" else "image/webp"
    }

    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun shutdown() {
        database?.close()
        database = null
        super.shutdown()
    }
}
