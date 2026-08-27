package com.stickerit.app.data.provider

/** Public WhatsApp limits for a static third-party sticker pack. */
object WhatsAppPackRules {
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30

    fun isValidStickerCount(count: Int): Boolean = count in MIN_STICKERS..MAX_STICKERS

    /** Keeps a stable, duplicate-free selection within the supported pack limit. */
    fun <T> normalizeSelection(items: List<T>): List<T> = items.distinct().take(MAX_STICKERS)
}
