package com.stickerit.app.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class GallerySelectionTest {

    @Test
    fun pruningKeepsOnlyStickersStillVisible() {
        assertEquals(
            linkedSetOf(2L, 4L),
            pruneSelectedStickerIds(
                selectedIds = linkedSetOf(2L, 3L, 4L),
                visibleStickerIds = listOf(4L, 2L),
            ),
        )
    }

    @Test
    fun pruningAnEmptyGalleryClearsSelection() {
        assertEquals(
            emptySet<Long>(),
            pruneSelectedStickerIds(
                selectedIds = setOf(1L, 2L),
                visibleStickerIds = emptyList(),
            ),
        )
    }
}
