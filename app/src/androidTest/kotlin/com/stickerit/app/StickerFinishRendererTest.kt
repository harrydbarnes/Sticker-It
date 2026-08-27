package com.stickerit.app

import android.graphics.Bitmap
import android.graphics.Color
import com.stickerit.app.data.model.FinishBackgroundType
import com.stickerit.app.data.model.FinishRecipe
import com.stickerit.app.domain.StickerFinishRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerFinishRendererTest {

    @Test
    fun rendersSquareCompositionWithBackgroundAndOverlay() {
        val cutout = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        cutout.eraseColor(Color.WHITE)

        val rendered = StickerFinishRenderer.render(
            cutout = cutout,
            recipe = FinishRecipe(
                backgroundType = FinishBackgroundType.SOLID,
                backgroundPrimaryColor = Color.BLUE,
                outlineEnabled = true,
                text = "Hi",
            ),
        )

        assertEquals(512, rendered.width)
        assertEquals(512, rendered.height)
        assertEquals(Color.BLUE, rendered.getPixel(0, 0))
        assertTrue(rendered.getPixel(256, 256) != Color.TRANSPARENT)

        rendered.recycle()
        cutout.recycle()
    }
}
