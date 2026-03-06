package com.stickerit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.stickerit.app.data.model.BrushMode

data class VisualStroke(
    val mode: BrushMode,
    val points: List<Offset>,
    val radius: Float,
)

/**
 * A transparent canvas overlay that captures brush gestures and draws
 * a visual preview of strokes.  Reports normalised (0..1) coordinates
 * back to the ViewModel.
 */
@Composable
fun BrushOverlay(
    modifier: Modifier = Modifier,
    brushMode: BrushMode,
    brushRadius: Float,
    onDragStart: (normX: Float, normY: Float) -> Unit,
    onDrag: (normX: Float, normY: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val activeStroke = remember { mutableStateListOf<Offset>() }
    val committedStrokes = remember { mutableStateListOf<VisualStroke>() }

    val includeColour = Color(0x6600C864)  // semi-transparent green
    val excludeColour = Color(0x66FF3B30)  // semi-transparent red
    val includeStrokeColour = Color(0xCC00C864)
    val excludeStrokeColour = Color(0xCCFF3B30)

    Canvas(
        modifier = modifier
            .pointerInput(brushMode, brushRadius) {
                detectDragGestures(
                    onDragStart = { offset ->
                        activeStroke.clear()
                        activeStroke.add(offset)
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
                        if (canvasSize != androidx.compose.ui.geometry.Size.Zero) {
                            onDrag(
                                pos.x / canvasSize.width,
                                pos.y / canvasSize.height,
                            )
                        }
                    },
                    onDragEnd = {
                        if (activeStroke.isNotEmpty()) {
                            committedStrokes.add(
                                VisualStroke(brushMode, activeStroke.toList(), brushRadius)
                            )
                            activeStroke.clear()
                        }
                        onDragEnd()
                    },
                    onDragCancel = {
                        activeStroke.clear()
                        onDragEnd()
                    }
                )
            }
    ) {
        canvasSize = size

        // Draw committed strokes
        for (stroke in committedStrokes) {
            val fillColour = if (stroke.mode == BrushMode.INCLUDE) includeColour else excludeColour
            val strokeColour = if (stroke.mode == BrushMode.INCLUDE) includeStrokeColour else excludeStrokeColour

            if (stroke.points.size == 1) {
                drawCircle(
                    color = fillColour,
                    radius = stroke.radius,
                    center = stroke.points.first(),
                )
            } else {
                val path = Path().apply {
                    moveTo(stroke.points.first().x, stroke.points.first().y)
                    stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = strokeColour,
                    style = Stroke(
                        width = stroke.radius * 2,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                )
            }
        }

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
