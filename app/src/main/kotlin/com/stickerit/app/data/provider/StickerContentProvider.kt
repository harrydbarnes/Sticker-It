package com.stickerit.app.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileNotFoundException

/**
 * ContentProvider that exposes sticker files to keyboards (GBoard and others).
 *
 * Sticker pack apps communicate with GBoard via a ContentProvider that
 * responds to specific URI patterns. This provider also doubles as a
 * FileProvider-style provider so sticker bitmaps can be shared via Intents.
 *
 * URI scheme:
 *   content://<authority>/sticker_asset/<pack_id>/<filename>
 *   content://<authority>/sticker_pack             → cursor of available packs
 */
class StickerContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "StickerContentProvider"

        // Column names expected by GBoard sticker pack protocol
        const val COL_STICKER_FILE_NAME = "sticker_file_name"
        const val COL_STICKER_EMOJIS = "sticker_emojis"
        const val COL_IDENTIFIER = "identifier"
        const val COL_NAME = "name"
        const val COL_PUBLISHER = "publisher"
        const val COL_TRAY_IMAGE = "tray_image_file"
        const val COL_ANDROID_PLAY_STORE_LINK = "android_play_store_link"
        const val COL_STICKER_COUNT = "sticker_count"

        // URI matcher codes
        private const val STICKER_PACK_LIST = 1
        private const val STICKER_PACK_DETAIL = 2
        private const val STICKER_ASSET = 3

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    }

    private val authority: String by lazy {
        context!!.packageName + ".stickercontentprovider"
    }

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        uriMatcher.addURI(ctx.packageName + ".stickercontentprovider", "sticker_pack", STICKER_PACK_LIST)
        uriMatcher.addURI(ctx.packageName + ".stickercontentprovider", "sticker_pack/*", STICKER_PACK_DETAIL)
        uriMatcher.addURI(ctx.packageName + ".stickercontentprovider", "sticker_asset/*/*", STICKER_ASSET)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            STICKER_PACK_LIST -> buildPackListCursor()
            STICKER_PACK_DETAIL -> buildPackDetailCursor(uri.lastPathSegment ?: "")
            else -> null
        }
    }

    private fun buildPackListCursor(): Cursor {
        val stickersDir = File(context!!.filesDir, "stickers")
        val files = stickersDir.listFiles { f -> f.extension == "webp" } ?: emptyArray()
        val cursor = MatrixCursor(
            arrayOf(
                COL_IDENTIFIER, COL_NAME, COL_PUBLISHER,
                COL_TRAY_IMAGE, COL_ANDROID_PLAY_STORE_LINK, COL_STICKER_COUNT,
            )
        )
        if (files.isNotEmpty()) {
            cursor.addRow(
                arrayOf(
                    "stickerit_pack",
                    "Sticker It",
                    "Sticker It App",
                    files.first().name, // first sticker as tray image
                    "",
                    files.size,
                )
            )
        }
        return cursor
    }

    private fun buildPackDetailCursor(packId: String): Cursor {
        val stickersDir = File(context!!.filesDir, "stickers")
        val files = stickersDir.listFiles { f -> f.extension == "webp" }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()

        val cursor = MatrixCursor(arrayOf(COL_STICKER_FILE_NAME, COL_STICKER_EMOJIS))
        files.forEach { file ->
            cursor.addRow(arrayOf(file.name, "😀"))
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return when (uriMatcher.match(uri)) {
            STICKER_ASSET -> {
                val pathSegments = uri.pathSegments
                if (pathSegments.size < 3) throw FileNotFoundException("Invalid URI: $uri")
                val fileName = pathSegments.last()
                val file = File(context!!.filesDir, "stickers/$fileName")
                if (!file.exists()) throw FileNotFoundException("Sticker not found: $fileName")
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            STICKER_ASSET -> "image/webp"
            else -> null
        }
    }

    // Not used — read-only provider
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    /** Build a URI for a specific sticker asset for use in GBoard intents */
    fun buildStickerAssetUri(fileName: String): Uri =
        Uri.parse("content://$authority/sticker_asset/stickerit_pack/$fileName")
}
