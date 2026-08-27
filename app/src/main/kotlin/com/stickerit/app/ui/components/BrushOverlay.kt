package com.stickerit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.stickerit.app.data.model.BrushMode
import com.stickerit.app.R

/**
 * A transparent canvas overlay that captures brush gestures and draws only
 * the stroke currently being drawn. Reports normalised (0..1) coordinates
 * back to the ViewModel. Once the gesture ends, the mask preview becomes the
 * source of truth and the temporary guide is removed.
 */
@Composable
fun BrushOverlay(
    modifier: Modifier = Modifier,
    brushMode: BrushMode,
    brushRadius: Float,
    onDragStart: (normX: Float, normY: Float) -> Unit,
    onDrag: (normX: Float, normY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onCursorPositionChange: (Offset?) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val activeStroke = remember { mutableStateListOf<Offset>() }

    val includeStrokeColour = Color(0xCC00C864)
    val excludeStrokeColour = Color(0xCCFF3B30)
    val canvasDescription = stringResource(
        if (brushMode == BrushMode.INCLUDE) R.string.brush_canvas_include else R.string.brush_canvas_exclude,
    )

    Canvas(
        modifier = modifier
            .onSizeChanged { size ->
                canvasSize = Size(size.width.toFloat(), size.height.toFloat())
            }
            .semantics { contentDescription = canvasDescription }
            .pointerInput(brushMode, brushRadius) {
                detectDragGestures(
                    onDragStart = { offset ->
                        activeStroke.clear()
                        activeStroke.add(offset)
                        onCursorPositionChange(offset)
                        if (canvasSize != androidx.compose.ui.geometry.Size.Zero) {
                            onDragStart(
                                offset.x / canvasSize.width,
                                offset.y / canvasSize.height,
                            )
                        }
                    },
                    onDrag = { change, _ ->
                        val pos = change.position
                        activeStroke.add(pos)
                        onCursorPositionChange(pos)
                        if (canvasSize != androidx.compose.ui.geometry.Size.Zero) {
                            onDrag(
                                pos.x / canvasSize.width,
                                pos.y / canvasSize.height,
                            )
                        }
                    },
                    onDragEnd = {
                        activeStroke.clear()
                        onCursorPositionChange(null)
                        onDragEnd()
                    },
                    onDragCancel = {
                        activeStroke.clear()
                        onCursorPositionChange(null)
                        onDragEnd()
                    }
                )
            }
    ) {
        // Draw active stroke
        val activeColour = if (brushMode == BrushMode.INCLUDE) includeStrokeColour else excludeStrokeColour
        if (activeStroke.size == 1) {
            drawCircle(
                color = activeColour,
                radius = brushRadius,
                center = activeStroke.first(),
            )
        } else if (activeStroke.size > 1) {
            val path = Path().apply {
                moveTo(activeStroke.first().x, activeStroke.first().y)
                activeStroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = path,
                color = activeColour,
                style = Stroke(
                    width = brushRadius * 2,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
            )
        }
    }
}

/**
 * A simple cursor indicator that follows the user's brush position.
 */
@Composable
fun BrushCursor(
    modifier: Modifier = Modifier,
    position: Offset?,
    radius: Float,
    mode: BrushMode,
) {
    if (position == null) return
    Canvas(modifier = modifier) {
        val colour = if (mode == BrushMode.INCLUDE) Color(0xFF00C864) else Color(0xFFFF3B30)
        drawCircle(
            color = colour.copy(alpha = 0.25f),
            radius = radius,
            center = position,
        )
        drawCircle(
            color = colour,
            radius = radius,
            center = position,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
