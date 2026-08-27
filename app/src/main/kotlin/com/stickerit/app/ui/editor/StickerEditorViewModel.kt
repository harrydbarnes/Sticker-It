package com.stickerit.app.ui.editor

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerit.app.data.model.BrushMode
import com.stickerit.app.data.model.EditorBrushState
import com.stickerit.app.data.model.EditorUiState
import com.stickerit.app.data.repository.StickerRepository
import com.stickerit.app.domain.BrushStroke
import com.stickerit.app.domain.ImageSegmentationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class StickerEditorViewModel @Inject constructor(
    private val repository: StickerRepository,
    private val segmentationHelper: ImageSegmentationHelper,
) : ViewModel() {

    companion object {
        private const val TAP_DISTANCE_THRESHOLD_NORM = 0.01
    }

    // ---------- public state ----------

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _brushState = MutableStateFlow(EditorBrushState())
    val brushState: StateFlow<EditorBrushState> = _brushState.asStateFlow()

    private val _stickerName = MutableStateFlow("My Sticker")
    val stickerName: StateFlow<String> = _stickerName.asStateFlow()

    // ---------- internal state ----------

    private var originalBitmap: Bitmap? = null
    private var baseConfidenceMask: FloatArray? = null
    private var confidenceMask: FloatArray? = null
    private var maskWidth: Int = 0
    private var maskHeight: Int = 0
    private var subjects: List<com.google.mlkit.vision.segmentation.subject.Subject> = emptyList()

    // Accumulated brush strokes for undo support
    private val brushStrokes = mutableListOf<BrushStroke>()

    // Current active stroke being drawn
    private var activeStrokePoints = mutableListOf<PointF>()
    private var previewJob: Job? = null
    private var committedRenderJob: Job? = null
    private var previewGeneration = 0L

    // ---------- public actions ----------

    fun loadAndSegment(uri: Uri) {
        cancelPreviewWork()
        brushStrokes.clear()
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            try {
                val bitmap = repository.loadBitmapFromUri(uri)
                    ?: error("Could not load image from URI")

                val result = segmentationHelper.segment(bitmap)
                originalBitmap = result.original
                baseConfidenceMask = result.confidenceMask.copyOf()
                confidenceMask = result.confidenceMask.copyOf()
                maskWidth = result.maskWidth
                maskHeight = result.maskHeight
                subjects = result.subjects

                val preview = buildPreview()
                _uiState.value = EditorUiState.SegmentationReady(
                    originalBitmap = result.original,
                    maskBitmap = buildMaskOverlay(),
                    previewBitmap = preview,
                )
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("download", ignoreCase = true)) {
                    _uiState.value = EditorUiState.Error("Downloading ML Kit module. Please wait a moment and try again.")
                } else {
                    _uiState.value = EditorUiState.Error(e.message ?: "Segmentation failed")
                }
            }
        }
    }

    fun setBrushMode(mode: BrushMode) {
        _brushState.update { it.copy(mode = mode) }
    }

    fun setBrushRadius(radius: Float) {
        _brushState.update { it.copy(radius = radius) }
    }

    fun onBrushDragStart(normX: Float, normY: Float) {
        activeStrokePoints.clear()
        activeStrokePoints.add(PointF(normX, normY))
    }

    fun onBrushDrag(normX: Float, normY: Float) {
        activeStrokePoints.add(PointF(normX, normY))
        applyActiveStroke()
    }

    fun onBrushDragEnd() {
        if (activeStrokePoints.isNotEmpty()) {
            val isInclude = brushState.value.mode == BrushMode.INCLUDE

            // Check if this is a tap
            val isTap = run {
                val first = activeStrokePoints.first()
                val last = activeStrokePoints.last()
                val dist = kotlin.math.hypot(
                    (first.x - last.x).toDouble(),
                    (first.y - last.y).toDouble()
                )
                dist < TAP_DISTANCE_THRESHOLD_NORM
            }

            if (isTap && subjects.isNotEmpty()) {
                val point = activeStrokePoints.first()
                val px = (point.x * maskWidth).toInt()
                val py = (point.y * maskHeight).toInt()

                val tappedSubject = subjects.firstNotNullOfOrNull { subject ->
                    if (segmentationHelper.isTapOnSubject(subject, px, py)) {
                        subject
                    } else {
                        null
                    }
                }

                if (tappedSubject != null) {
                    val stroke = BrushStroke.SubjectFill(
                        subject = tappedSubject,
                        include = isInclude,
                    )
                    brushStrokes.add(stroke)
                    activeStrokePoints.clear()
                    cancelPreviewWork()
                    recomputeMaskFromStrokes()
                    return
                }
            }

            val stroke = BrushStroke.Stroke(
                include = isInclude,
                points = activeStrokePoints.toList(),
                radiusNorm = brushState.value.radius / (maskWidth.toFloat().coerceAtLeast(1f)),
            )
            brushStrokes.add(stroke)
            activeStrokePoints.clear()
            cancelPreviewWork()
            recomputeMaskFromStrokes()
        }
    }

    fun undoLastStroke() {
        if (brushStrokes.isNotEmpty()) {
            cancelPreviewWork()
            brushStrokes.removeLastOrNull()
            recomputeMaskFromStrokes()
        }
    }

    fun resetEdits() {
        cancelPreviewWork()
        brushStrokes.clear()
        activeStrokePoints.clear()
        val base = baseConfidenceMask ?: return
        val orig = originalBitmap ?: return

        viewModelScope.launch {
            confidenceMask = base.copyOf()
            val current = _uiState.value
            if (current is EditorUiState.SegmentationReady) {
                _uiState.value = current.copy(
                    maskBitmap = buildMaskOverlay(),
                    previewBitmap = buildPreview(),
                )
            }
        }
    }

    fun setStickerName(name: String) {
        _stickerName.value = name
    }

    fun saveSticker() {
        cancelPreviewWork()
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            try {
                val mask = confidenceMask ?: error("No mask available")
                val sticker = segmentationHelper.buildStickerBitmap(
                    original = originalBitmap!!,
                    confidenceMask = mask,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                )
                repository.saveSticker(sticker, _stickerName.value)
                _uiState.value = EditorUiState.Saved
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(e.message ?: "Save failed")
            }
        }
    }

    // ---------- private helpers ----------

    private fun applyActiveStroke() {
        val currentStrokes = brushStrokes.toList()
        val currentStroke = BrushStroke.Stroke(
            include = brushState.value.mode == BrushMode.INCLUDE,
            points = activeStrokePoints.toList(),
            radiusNorm = brushState.value.radius / (maskWidth.toFloat().coerceAtLeast(1f)),
        )
        val allStrokes = currentStrokes + currentStroke

        val generation = ++previewGeneration
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            // Conflate pointer events to one preview per visual frame instead of rendering
            // every raw move event. The final stroke remains precise on drag end.
            delay(16)
            val base = baseConfidenceMask ?: return@launch
            val updatedMask = segmentationHelper.applyBrushStrokes(
                confidenceMask = base,
                maskWidth = maskWidth,
                maskHeight = maskHeight,
                brushStrokes = allStrokes,
            )

            val preview = segmentationHelper.buildStickerBitmap(
                original = originalBitmap!!,
                confidenceMask = updatedMask,
                maskWidth = maskWidth,
                maskHeight = maskHeight,
            )

            val current = _uiState.value
            if (current is EditorUiState.SegmentationReady) {
                withContext(Dispatchers.Main) {
                    if (generation == previewGeneration) _uiState.value = current.copy(previewBitmap = preview)
                }
            }
        }
    }

    private fun recomputeMaskFromStrokes() {
        val currentStrokes = brushStrokes.toList()
        val generation = ++previewGeneration
        committedRenderJob?.cancel()
        committedRenderJob = viewModelScope.launch(Dispatchers.Default) {
            val base = baseConfidenceMask ?: return@launch
            val orig = originalBitmap ?: return@launch
            val updatedMask = segmentationHelper.applyBrushStrokes(
                confidenceMask = base,
                maskWidth = maskWidth,
                maskHeight = maskHeight,
                brushStrokes = currentStrokes,
            )
            confidenceMask = updatedMask
            val preview = segmentationHelper.buildStickerBitmap(
                original = orig,
                confidenceMask = updatedMask,
                maskWidth = maskWidth,
                maskHeight = maskHeight,
            )
            val current = _uiState.value
            if (current is EditorUiState.SegmentationReady) {
                withContext(Dispatchers.Main) {
                    if (generation == previewGeneration) _uiState.value = current.copy(previewBitmap = preview)
                }
            }
        }
    }

    private fun cancelPreviewWork() {
        previewGeneration++
        previewJob?.cancel()
        committedRenderJob?.cancel()
        previewJob = null
        committedRenderJob = null
    }

    override fun onCleared() {
        cancelPreviewWork()
        super.onCleared()
    }

    private suspend fun buildPreview(): Bitmap = withContext(Dispatchers.Default) {
        val mask = confidenceMask ?: return@withContext originalBitmap!!
        segmentationHelper.buildStickerBitmap(
            original = originalBitmap!!,
            confidenceMask = mask,
            maskWidth = maskWidth,
            maskHeight = maskHeight,
        )
    }

    private suspend fun buildMaskOverlay(): Bitmap = withContext(Dispatchers.Default) {
        // Build a semi-transparent overlay showing included (green) / excluded (red) areas
        val mask = confidenceMask ?: return@withContext Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val overlay = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(maskWidth * maskHeight)
        for (i in pixels.indices) {
            pixels[i] = if (mask[i] >= 0.5f)
                android.graphics.Color.argb(100, 0, 200, 100)
            else
                android.graphics.Color.argb(100, 200, 0, 50)
        }
        overlay.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)
        overlay
    }
}
