package com.stickerit.app.data.provider

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.stickerit.app.data.model.Sticker
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper that bridges the app's sticker files to GBoard (and other IMEs).
 *
 * GBoard supports adding custom sticker packs via a specific Intent scheme.
 * The sticker pack metadata is served by [StickerContentProvider].
 */
@Singleton
class GboardHelper @Inject constructor(
    private val context: Context,
) {

    companion object {
        // Intent action that GBoard listens for when adding a sticker pack
        private const val ACTION_ADD_STICKER_PACK =
            "com.google.android.inputmethod.latin.ADD_STICKER_PACK"
        // Extra keys
        private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
        private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    }

    private val authority: String get() = "${context.packageName}.stickercontentprovider"

    /**
     * Launch the GBoard sticker pack import flow for our pack.
     * Returns true if GBoard is installed and the intent was sent.
     */
    fun addPackToGboard(packId: String = "stickerit_pack"): Boolean {
        val intent = Intent(ACTION_ADD_STICKER_PACK).apply {
            putExtra(EXTRA_STICKER_PACK_ID, packId)
            putExtra(EXTRA_STICKER_PACK_AUTHORITY, authority)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } else {
            false
        }
    }

    /**
     * Returns a shareable [Uri] for [sticker] so it can be sent via other apps
     * or used in share sheets.
     */
    fun getSharableUri(sticker: Sticker): Uri {
        val file = File(sticker.filePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    /**
     * Build a share intent for [sticker] suitable for Android's share sheet.
     */
    fun buildShareIntent(sticker: Sticker): Intent {
        val uri = getSharableUri(sticker)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Check whether GBoard is installed on this device */
    fun isGboardInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo("com.google.android.inputmethod.latin", 0)
            true
        }.getOrDefault(false)
    }
}
