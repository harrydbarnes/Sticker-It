package com.stickerit.app.data.provider

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.stickerit.app.data.model.DEFAULT_STICKER_PACK_ID
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.repository.StickerPackRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Bridges a user-selected library set to WhatsApp's public sticker-pack contract. */
@Singleton
class WhatsAppHelper @Inject constructor(
    private val context: Context,
    private val packRepository: StickerPackRepository,
) {
    companion object {
        const val PACK_ID = DEFAULT_STICKER_PACK_ID
        private const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    }

    private val authority get() = "${context.packageName}.stickercontentprovider"

    /** Writes one named pack, then opens WhatsApp's explicit add/update confirmation. */
    suspend fun addOrUpdateWhatsAppPack(packId: String, stickers: List<Sticker>): WhatsAppResult {
        if (!WhatsAppPackRules.isValidStickerCount(stickers.size)) return WhatsAppResult.InvalidStickerCount
        val pack = packRepository.replaceItems(packId, stickers) ?: return WhatsAppResult.PackNotFound
        if (!pack.trayImageIsCustom) writeTrayIcon(stickers.first(), pack.trayImageFileName)
        val intent = Intent(ACTION_ENABLE_STICKER_PACK).apply {
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", authority)
            putExtra("sticker_pack_name", pack.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            WhatsAppResult.Opened
        } else WhatsAppResult.NotInstalled
    }

    /** Compatibility entry point for callers that still target the original library pack. */
    suspend fun addOrUpdateWhatsAppPack(stickers: List<Sticker>): WhatsAppResult =
        addOrUpdateWhatsAppPack(PACK_ID, stickers)

    private fun writeTrayIcon(sticker: Sticker, fileName: String) {
        val source = BitmapFactory.decodeFile(sticker.filePath)
            ?: error("Could not create a WhatsApp tray image")
        var tray: Bitmap? = null
        try {
            val trayBitmap = Bitmap.createScaledBitmap(source, 96, 96, true)
            tray = trayBitmap
            val target = File(context.filesDir, fileName)
            require(target.name == fileName) { "Invalid tray image filename" }
            val temporary = File(target.parentFile, "${target.name}.tmp")
            try {
                target.parentFile?.mkdirs()
                temporary.outputStream().use { output ->
                    check(trayBitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                if (target.exists() && !target.delete()) {
                    error("Could not replace WhatsApp tray image")
                }
                check(temporary.renameTo(target)) { "Could not finish WhatsApp tray image" }
            } finally {
                temporary.delete()
            }
        } finally {
            tray?.takeUnless { it.isRecycled || it === source }?.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    fun buildShareIntent(sticker: Sticker): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(sticker.filePath))
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

enum class WhatsAppResult { Opened, NotInstalled, InvalidStickerCount, PackNotFound }
