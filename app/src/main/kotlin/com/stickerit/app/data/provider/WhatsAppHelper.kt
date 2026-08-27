package com.stickerit.app.data.provider

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.stickerit.app.data.model.Sticker
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Bridges a user-selected library set to WhatsApp's public sticker-pack contract. */
@Singleton
class WhatsAppHelper @Inject constructor(
    private val context: Context,
    private val packStore: WhatsAppPackStore,
) {
    companion object {
        const val PACK_ID = "stickerit_library"
        private const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    }

    private val authority get() = "${context.packageName}.stickercontentprovider"

    /** Writes the chosen set, then opens WhatsApp's explicit add/update confirmation. */
    fun addOrUpdateWhatsAppPack(stickers: List<Sticker>): WhatsAppResult {
        if (!WhatsAppPackRules.isValidStickerCount(stickers.size)) return WhatsAppResult.InvalidStickerCount
        writeTrayIcon(stickers.first())
        packStore.writePack(WhatsAppPackStore.Pack(PACK_ID, "Sticker It library", stickers.map { File(it.filePath).name }, System.currentTimeMillis().toString()))
        val intent = Intent(ACTION_ENABLE_STICKER_PACK).apply {
            putExtra("sticker_pack_id", PACK_ID)
            putExtra("sticker_pack_authority", authority)
            putExtra("sticker_pack_name", "Sticker It library")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            WhatsAppResult.Opened
        } else WhatsAppResult.NotInstalled
    }

    private fun writeTrayIcon(sticker: Sticker) {
        val source = BitmapFactory.decodeFile(sticker.filePath) ?: return
        val tray = Bitmap.createScaledBitmap(source, 96, 96, true)
        File(context.filesDir, "whatsapp_tray.png").outputStream().use {
            tray.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        if (tray !== source) tray.recycle()
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

enum class WhatsAppResult { Opened, NotInstalled, InvalidStickerCount }
