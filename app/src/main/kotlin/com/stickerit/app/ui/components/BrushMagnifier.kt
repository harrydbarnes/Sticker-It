package com.stickerit.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stickerit.app.data.model.BrushMode
import kotlin.math.roundToInt

/**
 * A touch magnifier for fine brushwork. The lens shows the source image and
 * the live selection dimming layer, so it remains useful while the mask is
 * being edited rather than showing a separate, stale preview.
 */
@Composable
fun BrushMagnifier(
    originalBitmap: Bitmap,
    selectionDimBitmap: Bitmap,
    position: Offset?,
    brushMode: BrushMode,
    modifier: Modifier = Modifier,
) {
    if (position == null) return

    val density = LocalDensity.current
    val lensDiameter = with(density) { 124.dp.toPx() }
    val lensGap = with(density) { 14.dp.toPx() }
    val zoom = 2.4f
    val originalImage = remember(originalBitmap) { originalBitmap.asImageBitmap() }
    val selectionDimImage = remember(selectionDimBitmap) { selectionDimBitmap.asImageBitmap() }

    Canvas(modifier = modifier) {
        if (size.width <= lensDiameter || size.height <= lensDiameter) return@Canvas

        val lensRadius = lensDiameter / 2f
        val borderWidth = 3.dp.toPx()
        val targetColour = if (brushMode == BrushMode.INCLUDE) {
            Color(0xFF00C864)
        } else {
            Color(0xFFFF3B30)
        }

        val sourceWidth = (
            originalBitmap.width * (lensDiameter / size.width) / zoom
        ).roundToInt().coerceIn(1, originalBitmap.width)
        val sourceHeight = (
            originalBitmap.height * (lensDiameter / size.height) / zoom
        ).roundToInt().coerceIn(1, originalBitmap.height)
        val sourceLeft = (
            (position.x / size.width) * originalBitmap.width - sourceWidth / 2f
        ).roundToInt().coerceIn(0, originalBitmap.width - sourceWidth)
        val sourceTop = (
            (position.y / size.height) * originalBitmap.height - sourceHeight / 2f
        ).roundToInt().coerceIn(0, originalBitmap.height - sourceHeight)

        val lensCenterX = position.x.coerceIn(lensRadius, size.width - lensRadius)
        val aboveY = position.y - lensRadius - lensGap
        val belowY = position.y + lensRadius + lensGap
        val lensCenterY = (if (aboveY >= lensRadius) aboveY else belowY)
            .coerceIn(lensRadius, size.height - lensRadius)
        val lensTopLeft = Offset(lensCenterX - lensRadius, lensCenterY - lensRadius)
        val destinationOffset = IntOffset(
            lensTopLeft.x.roundToInt(),
            lensTopLeft.y.roundToInt(),
        )
        val destinationSize = IntSize(
            lensDiameter.roundToInt(),
            lensDiameter.roundToInt(),
        )

        val sourcePointX = (position.x / size.width) * originalBitmap.width
        val sourcePointY = (position.y / size.height) * originalBitmap.height
        val magnifiedTarget = Offset(
            lensTopLeft.x + (sourcePointX - sourceLeft) / sourceWidth * lensDiameter,
            lensTopLeft.y + (sourcePointY - sourceTop) / sourceHeight * lensDiameter,
        )
        val connectorEnd = if (lensCenterY < position.y) {
            Offset(lensCenterX, lensCenterY + lensRadius)
        } else {
            Offset(lensCenterX, lensCenterY - lensRadius)
        }

        drawLine(
            color = targetColour.copy(alpha = 0.6f),
            start = position,
            end = connectorEnd,
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.25f),
            radius = lensRadius + borderWidth + 2.dp.toPx(),
            center = Offset(lensCenterX, lensCenterY),
        )

        val lensPath = Path().apply {
            addOval(
                Rect(
                    left = lensTopLeft.x,
                    top = lensTopLeft.y,
                    right = lensTopLeft.x + lensDiameter,
                    bottom = lensTopLeft.y + lensDiameter,
                )
            )
        }
        clipPath(lensPath) {
            drawImage(
                image = originalImage,
                srcOffset = IntOffset(sourceLeft, sourceTop),
                srcSize = IntSize(sourceWidth, sourceHeight),
                dstOffset = destinationOffset,
                dstSize = destinationSize,
            )
            drawImage(
                image = selectionDimImage,
                srcOffset = IntOffset(sourceLeft, sourceTop),
                srcSize = IntSize(sourceWidth, sourceHeight),
                dstOffset = destinationOffset,
                dstSize = destinationSize,
            )
        }

        drawCircle(
            color = Color.White,
            radius = lensRadius,
            center = Offset(lensCenterX, lensCenterY),
            style = Stroke(width = borderWidth),
        )
        drawCircle(
            color = targetColour,
            radius = lensRadius - borderWidth,
            center = Offset(lensCenterX, lensCenterY),
            style = Stroke(width = 1.dp.toPx()),
        )
        drawLine(
            color = targetColour,
            start = magnifiedTarget.copy(x = magnifiedTarget.x - 7.dp.toPx()),
            end = magnifiedTarget.copy(x = magnifiedTarget.x + 7.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = targetColour,
            start = magnifiedTarget.copy(y = magnifiedTarget.y - 7.dp.toPx()),
            end = magnifiedTarget.copy(y = magnifiedTarget.y + 7.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
