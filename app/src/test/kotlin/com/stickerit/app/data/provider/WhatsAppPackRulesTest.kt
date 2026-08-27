package com.stickerit.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppPackRulesTest {
    @Test
    fun stickerCountMustBeWithinWhatsAppsStaticPackLimits() {
        assertFalse(WhatsAppPackRules.isValidStickerCount(2))
        assertTrue(WhatsAppPackRules.isValidStickerCount(3))
        assertTrue(WhatsAppPackRules.isValidStickerCount(30))
        assertFalse(WhatsAppPackRules.isValidStickerCount(31))
    }

    @Test
    fun normalisingSelectionKeepsOrderRemovesDuplicatesAndCapsAtThirty() {
        val selection = listOf(1, 1) + (2..35).toList()
        val normalized = WhatsAppPackRules.normalizeSelection(selection)

        assertEquals((1..30).toList(), normalized)
    }
}
