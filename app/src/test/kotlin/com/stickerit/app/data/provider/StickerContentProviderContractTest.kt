package com.stickerit.app.data.provider

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StickerContentProviderContractTest {
    @Test
    fun metadataColumnsMatchWhatsAppsPackContractOrder() {
        assertArrayEquals(
            arrayOf(
                "identifier", "name", "publisher", "tray_image_file", "image_data_version",
                "avoid_cache", "publisher_email", "publisher_website", "privacy_policy_website",
                "license_agreement_website", "android_play_store_link", "ios_app_store_link", "animated_sticker_pack",
            ),
            WhatsAppStickerContract.metadataColumns,
        )
    }

    @Test
    fun stickerColumnsExposeFileEmojiKeywordsAndAccessibilityDescription() {
        assertArrayEquals(
            arrayOf("sticker_file_name", "sticker_emojis", "accessibility_text"),
            WhatsAppStickerContract.stickerColumns,
        )
        assertEquals(3, WhatsAppStickerContract.stickerColumns.size)
    }
}
