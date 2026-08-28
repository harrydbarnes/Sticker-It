package com.stickerit.app.data.provider

/** Column names shared by the provider implementation and contract tests. */
object WhatsAppStickerContract {
    val metadataColumns = arrayOf(
        "identifier", "name", "publisher", "tray_image_file", "image_data_version",
        "avoid_cache", "publisher_email", "publisher_website", "privacy_policy_website",
        "license_agreement_website", "android_play_store_link", "ios_app_store_link", "animated_sticker_pack",
    )
    val stickerColumns = arrayOf("sticker_file_name", "sticker_emojis", "accessibility_text")
}
