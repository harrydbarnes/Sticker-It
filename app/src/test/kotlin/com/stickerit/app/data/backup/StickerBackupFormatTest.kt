package com.stickerit.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerBackupFormatTest {

    @Test
    fun manifestRoundTripsStickerMetadataAndOptionalAssets() {
        val record = BackupStickerRecord(
            originalId = 42L,
            name = "Kitchen helper",
            createdAt = 1234L,
            sortOrder = 3,
            width = 512,
            height = 512,
            legacyPackFlag = true,
            assetEntry = "assets/sticker_0.webp",
            sourceEntry = "assets/sticker_0_source.webp",
            maskEntry = "assets/sticker_0_mask.bin",
            backgroundEntry = "assets/background_0.webp",
            finishRecipeJson = "{\"outlineWidth\":4}",
        )

        val parsed = StickerBackupFormat.parseManifest(
            StickerBackupFormat.buildManifest(listOf(record), createdAt = 999L),
        )

        assertEquals(listOf(record), parsed)
    }

    @Test
    fun manifestRoundTripsNamedPackMembershipAndTray() {
        val sticker = BackupStickerRecord(
            originalId = 7L,
            name = "Lunch",
            createdAt = 123L,
            sortOrder = 0,
            width = 512,
            height = 512,
            legacyPackFlag = false,
            assetEntry = "assets/sticker_0.webp",
            sourceEntry = null,
            maskEntry = null,
            backgroundEntry = null,
            finishRecipeJson = null,
        )
        val pack = BackupPackRecord(
            originalId = "pack_food",
            name = "Food",
            publisher = "Sticker It",
            trayImageEntry = "assets/pack_tray_0.png",
            trayImageIsCustom = true,
            imageDataVersion = "2",
            createdAt = 456L,
            sortOrder = 1,
            items = listOf(
                BackupPackItemRecord(
                    stickerEntry = sticker.assetEntry,
                    sortOrder = 0,
                    emojis = "🍕, food",
                    accessibilityText = "A slice of pizza",
                ),
            ),
        )

        val parsed = StickerBackupFormat.parseBackupManifest(
            StickerBackupFormat.buildManifest(listOf(sticker), listOf(pack), createdAt = 999L),
        )

        assertEquals(listOf(sticker), parsed.stickers)
        assertEquals(listOf(pack), parsed.packs)
    }

    @Test
    fun entryNamesCannotEscapeAssetsDirectory() {
        assertTrue(StickerBackupFormat.isSafeEntryName("assets/sticker.webp"))
        assertFalse(StickerBackupFormat.isSafeEntryName("../manifest.json"))
        assertFalse(StickerBackupFormat.isSafeEntryName("assets/../sticker.webp"))
        assertFalse(StickerBackupFormat.isSafeEntryName("assets\\sticker.webp"))
        assertFalse(StickerBackupFormat.isSafeEntryName("assets/"))
    }

    @Test
    fun manifestRejectsUnsafeAssetReference() {
        val record = BackupStickerRecord(
            originalId = 1L,
            name = "Unsafe",
            createdAt = 1L,
            sortOrder = 0,
            width = 512,
            height = 512,
            legacyPackFlag = false,
            assetEntry = "assets/../outside.webp",
            sourceEntry = null,
            maskEntry = null,
            backgroundEntry = null,
            finishRecipeJson = null,
        )

        var rejected = false
        try {
            StickerBackupFormat.parseManifest(
                StickerBackupFormat.buildManifest(listOf(record), createdAt = 1L),
            )
        } catch (_: BackupFormatException) {
            rejected = true
        }

        assertTrue(rejected)
    }
}
