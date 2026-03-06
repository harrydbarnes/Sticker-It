package com.stickerit.app.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps ML Kit's Subject Segmentation API.
 *
 * Returns a float confidence mask (0..1 per pixel) that represents how likely
 * each pixel belongs to the primary subject.  The caller can then threshold
 * this to produce a binary mask, or use the raw floats for soft-edge blending.
 */
@Singleton
class ImageSegmentationHelper @Inject constructor() {

    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .enableForegroundConfidenceMask()
                .build()
        )
    }

    data class SegmentationResult(
        /** The original bitmap passed in */
        val original: Bitmap,
        /** ARGB_8888 bitmap containing only the foreground subject (transparent bg) */
        val foregroundBitmap: Bitmap,
        /** Float array, size == width*height, values 0..1 indicating subject confidence */
        val confidenceMask: FloatArray,
        val maskWidth: Int,
        val maskHeight: Int,
    )

    /**
     * Run subject segmentation on [bitmap].
     *
     * @param bitmap source image — must be ARGB_8888; will be scaled if too large.
     */
    suspend fun segment(bitmap: Bitmap): SegmentationResult = withContext(Dispatchers.Default) {
        // Scale down very large images to keep ML Kit processing fast
        val scaled = scaleBitmapIfNeeded(bitmap, maxDim = 1080)

        val image = InputImage.fromBitmap(scaled, 0)

        suspendCancellableCoroutine { cont ->
            segmenter.process(image)
                .addOnSuccessListener { result ->
                    val foreground = result.foregroundBitmap
                        ?: buildForegroundFromMask(scaled, result)
                    val mask = result.foregroundConfidenceMask?.array
                        ?: FloatArray(scaled.width * scaled.height) { 0f }
                    cont.resume(
                        SegmentationResult(
                            original = scaled,
                            foregroundBitmap = foreground,
                            confidenceMask = mask,
                            maskWidth = scaled.width,
                            maskHeight = scaled.height,
                        )
                    )
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /**
     * Apply brush strokes from [brushStrokes] on top of [confidenceMask].
     *
     * Each stroke is a [BrushStroke] — either INCLUDE (set mask to 1) or
     * EXCLUDE (set mask to 0) — applied as filled circles at each point.
     *
     * Returns a new FloatArray representing the updated mask.
     */
    fun applyBrushStrokes(
        confidenceMask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        brushStrokes: List<BrushStroke>,
    ): FloatArray {
        val updated = confidenceMask.copyOf()
        for (stroke in brushStrokes) {
            val value = if (stroke.include) 1f else 0f
            for (point in stroke.points) {
                // Convert normalised coordinates (0..1) to mask pixel coordinates
                val cx = (point.x * maskWidth).toInt()
                val cy = (point.y * maskHeight).toInt()
                val r = (stroke.radiusNorm * maskWidth).toInt().coerceAtLeast(1)
                paintCircle(updated, maskWidth, maskHeight, cx, cy, r, value)
            }
        }
        return updated
    }

    /**
     * Convert a (possibly edited) confidence mask into a final sticker bitmap.
     * Pixels with confidence >= [threshold] are kept; the rest are made transparent.
     * Optionally applies a soft feathered edge based on the raw confidence value.
     */
    fun buildStickerBitmap(
        original: Bitmap,
        confidenceMask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float = 0.5f,
        outputSize: Int = 512,
    ): Bitmap {
        // Scale mask back to original dimensions if needed
        val sticker = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(maskWidth * maskHeight)
        val originalPixels = IntArray(maskWidth * maskHeight)
        original.getPixels(originalPixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

        for (i in pixels.indices) {
            val conf = confidenceMask[i]
            val alpha = when {
                conf >= threshold -> {
                    // Soft feather near the threshold boundary
                    val feather = ((conf - threshold) / 0.1f).coerceIn(0f, 1f)
                    (feather * 255).toInt()
                }
                else -> 0
            }
            val rgb = originalPixels[i] and 0x00FFFFFF
            pixels[i] = (alpha shl 24) or rgb
        }
        sticker.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

        // Scale to target output size
        return Bitmap.createScaledBitmap(sticker, outputSize, outputSize, true)
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= maxDim) return bitmap
        val scale = maxDim.toFloat() / max
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun buildForegroundFromMask(bitmap: Bitmap, result: SubjectSegmentationResult): Bitmap {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val mask = result.foregroundConfidenceMask?.array ?: return out
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            val alpha = (mask[i] * 255).toInt()
            pixels[i] = (alpha shl 24) or (pixels[i] and 0x00FFFFFF)
        }
        out.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    private fun paintCircle(
        mask: FloatArray,
        w: Int,
        h: Int,
        cx: Int,
        cy: Int,
        radius: Int,
        value: Float,
    ) {
        val r2 = radius * radius
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy <= r2) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until w && y in 0 until h) {
                        mask[y * w + x] = value
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Brush stroke data
// ---------------------------------------------------------------------------

data class BrushStroke(
    /** Whether this stroke adds (true) or removes (false) area */
    val include: Boolean,
    /** List of normalised (0..1) canvas points */
    val points: List<PointF>,
    /** Brush radius as a fraction of image width */
    val radiusNorm: Float = 0.05f,
)
