package com.stickerit.app.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.stickerit.app.data.model.FinishBackgroundType
import com.stickerit.app.data.model.FinishRecipe
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Renders the reusable finishing recipe into the same square asset that gets saved. */
object StickerFinishRenderer {

    fun render(
        cutout: Bitmap,
        recipe: FinishRecipe,
        backgroundBitmap: Bitmap? = null,
        outputSize: Int = OUTPUT_SIZE,
    ): Bitmap {
        require(outputSize > 0) { "Output size must be positive" }

        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(output)
        drawBackground(canvas, recipe, backgroundBitmap, outputSize)

        val stickerRect = stickerRect(cutout, recipe, outputSize)
        if (recipe.outlineEnabled) {
            drawOutline(canvas, cutout, stickerRect, recipe)
        }
        canvas.drawBitmap(cutout, null, stickerRect, bitmapPaint())

        drawOverlayText(canvas, recipe, outputSize)
        return output
    }

    private fun drawBackground(
        canvas: Canvas,
        recipe: FinishRecipe,
        backgroundBitmap: Bitmap?,
        outputSize: Int,
    ) {
        when (recipe.backgroundType) {
            FinishBackgroundType.TRANSPARENT -> Unit
            FinishBackgroundType.SOLID -> canvas.drawColor(recipe.backgroundPrimaryColor)
            FinishBackgroundType.GRADIENT -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f,
                        0f,
                        outputSize.toFloat(),
                        outputSize.toFloat(),
                        recipe.backgroundPrimaryColor,
                        recipe.backgroundSecondaryColor,
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(0f, 0f, outputSize.toFloat(), outputSize.toFloat(), paint)
            }
            FinishBackgroundType.IMAGE -> {
                if (backgroundBitmap == null || backgroundBitmap.isRecycled) return
                canvas.drawBitmap(
                    backgroundBitmap,
                    squareCrop(backgroundBitmap),
                    Rect(0, 0, outputSize, outputSize),
                    bitmapPaint(),
                )
            }
        }
    }

    private fun stickerRect(cutout: Bitmap, recipe: FinishRecipe, outputSize: Int): RectF {
        val longestSide = max(cutout.width, cutout.height).coerceAtLeast(1)
        val baseScale = outputSize * BASE_STICKER_FRACTION / longestSide.toFloat()
        val scale = recipe.scale.coerceIn(MIN_SCALE, MAX_SCALE)
        val width = cutout.width * baseScale * scale
        val height = cutout.height * baseScale * scale
        val offsetX = recipe.offsetX.coerceIn(-MAX_OFFSET, MAX_OFFSET) * outputSize
        val offsetY = recipe.offsetY.coerceIn(-MAX_OFFSET, MAX_OFFSET) * outputSize
        val left = (outputSize - width) / 2f + offsetX
        val top = (outputSize - height) / 2f + offsetY
        return RectF(left, top, left + width, top + height)
    }

    private fun drawOutline(
        canvas: Canvas,
        cutout: Bitmap,
        stickerRect: RectF,
        recipe: FinishRecipe,
    ) {
        val width = recipe.outlineWidth.coerceIn(0f, MAX_OUTLINE_WIDTH)
        if (width <= 0f) return

        val paint = bitmapPaint().apply {
            colorFilter = PorterDuffColorFilter(recipe.outlineColor, PorterDuff.Mode.SRC_IN)
        }
        val steps = 32
        for (step in 0 until steps) {
            val angle = step * (Math.PI * 2.0 / steps)
            val dx = (cos(angle) * width).toFloat()
            val dy = (sin(angle) * width).toFloat()
            canvas.drawBitmap(
                cutout,
                null,
                RectF(
                    stickerRect.left + dx,
                    stickerRect.top + dy,
                    stickerRect.right + dx,
                    stickerRect.bottom + dy,
                ),
                paint,
            )
        }
    }

    private fun drawOverlayText(canvas: Canvas, recipe: FinishRecipe, outputSize: Int) {
        if (recipe.emoji.isNotBlank()) {
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 64f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(5f, 0f, 2f, Color.BLACK)
            }
            canvas.drawText(recipe.emoji.trim(), outputSize / 2f, 78f, emojiPaint)
        }

        if (recipe.text.isNotBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(5f, 0f, 2f, Color.BLACK)
            }
            canvas.drawText(recipe.text.trim(), outputSize / 2f, outputSize - 42f, textPaint)
        }
    }

    private fun bitmapPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun squareCrop(bitmap: Bitmap): Rect {
        val edge = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - edge) / 2
        val top = (bitmap.height - edge) / 2
        return Rect(left, top, left + edge, top + edge)
    }

    private const val OUTPUT_SIZE = 512
    // Keep the default recipe visually identical to the existing 512px cut-out.
    // Scale and position controls intentionally allow the user to move beyond it.
    private const val BASE_STICKER_FRACTION = 1f
    private const val MIN_SCALE = 0.55f
    private const val MAX_SCALE = 1.35f
    private const val MAX_OFFSET = 0.45f
    private const val MAX_OUTLINE_WIDTH = 28f
}
