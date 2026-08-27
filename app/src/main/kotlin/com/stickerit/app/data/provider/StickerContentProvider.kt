package com.stickerit.app.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
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
        private val metadataColumns = arrayOf(
            "identifier", "name", "publisher", "tray_image_file", "image_data_version",
            "avoid_cache", "publisher_email", "publisher_website", "privacy_policy_website",
            "license_agreement_website", "android_play_store_link", "ios_app_store_link", "animated_sticker_pack",
        )
        private val stickerColumns = arrayOf("sticker_file_name", "sticker_emojis", "accessibility_text")
    }

    override fun onCreate(): Boolean {
        val authority = context?.packageName + ".stickercontentprovider"
        matcher.addURI(authority, "metadata", METADATA)
        matcher.addURI(authority, "metadata/*", METADATA_ITEM)
        matcher.addURI(authority, "stickers/*", STICKERS)
        matcher.addURI(authority, "stickers_asset/*/*", STICKER_ASSET)
        return context != null
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = when (matcher.match(uri)) {
        METADATA -> metadataCursor(null)
        METADATA_ITEM -> metadataCursor(uri.lastPathSegment)
        STICKERS -> stickerCursor(uri.lastPathSegment)
        else -> null
    }

    private fun pack() = context?.let(::WhatsAppPackStore)?.readPack()

    private fun metadataCursor(identifier: String?): Cursor {
        val cursor = MatrixCursor(metadataColumns)
        val pack = pack() ?: return cursor
        if (identifier == null || identifier == pack.identifier) cursor.addRow(arrayOf(
            pack.identifier, pack.name, "Sticker It", "whatsapp_tray.png", pack.imageDataVersion,
            false, "", "", "", "", "", "", false,
        ))
        return cursor
    }

    private fun stickerCursor(identifier: String?): Cursor {
        val cursor = MatrixCursor(stickerColumns)
        val pack = pack()?.takeIf { it.identifier == identifier } ?: return cursor
        pack.fileNames.forEach { cursor.addRow(arrayOf(it, "😀", "A custom sticker")) }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (matcher.match(uri) != STICKER_ASSET || mode != "r") return null
        val parts = uri.pathSegments
        val pack = pack() ?: throw FileNotFoundException("No sticker pack")
        val fileName = parts.lastOrNull() ?: throw FileNotFoundException("Invalid sticker URI")
        if (parts.getOrNull(1) != pack.identifier || (fileName !in pack.fileNames && fileName != "whatsapp_tray.png")) throw FileNotFoundException("Sticker is not in this pack")
        val file = if (fileName == "whatsapp_tray.png") File(context!!.filesDir, fileName) else File(context!!.filesDir, "stickers/$fileName")
        if (!file.isFile) throw FileNotFoundException("Sticker not found")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? =
        openFile(uri, mode)?.let { AssetFileDescriptor(it, 0, AssetFileDescriptor.UNKNOWN_LENGTH) }

    override fun getType(uri: Uri) = when {
        matcher.match(uri) != STICKER_ASSET -> null
        uri.lastPathSegment == "whatsapp_tray.png" -> "image/png"
        else -> "image/webp"
    }
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
