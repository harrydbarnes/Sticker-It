package com.stickerit.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BrushOverlayTest {

    @Test
    fun sourceRadiusIsScaledToTheFittedCanvas() {
        assertEquals(
            6f,
            brushRadiusInCanvasPixels(
                sourceRadiusPx = 12f,
                canvasWidthPx = 540f,
                sourceImageWidthPx = 1080,
            ),
            0.001f,
        )
    }

    @Test
    fun invalidDimensionsKeepTheSuppliedRadius() {
        assertEquals(
            12f,
            brushRadiusInCanvasPixels(
                sourceRadiusPx = 12f,
                canvasWidthPx = 0f,
                sourceImageWidthPx = 1080,
            ),
            0.001f,
        )
    }
}
