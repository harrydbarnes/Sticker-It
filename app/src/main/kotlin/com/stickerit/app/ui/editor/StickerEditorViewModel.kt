package com.stickerit.app.ui.editor

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerit.app.data.model.BrushMode
import com.stickerit.app.data.model.EditorBrushState
import com.stickerit.app.data.model.EditorUiState
import com.stickerit.app.data.model.FinishBackgroundType
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.repository.EditorSettingsRepository
import com.stickerit.app.data.repository.StickerRepository
import com.stickerit.app.domain.BrushStroke
import com.stickerit.app.domain.ImageSegmentationHelper
import com.stickerit.app.domain.StickerFinishRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.stickerit.app.data.model.FinishRecipe
import javax.inject.Inject

@HiltViewModel
class StickerEditorViewModel @Inject constructor(
    private val repository: StickerRepository,
    private val segmentationHelper: ImageSegmentationHelper,
    private val settingsRepository: EditorSettingsRepository,
) : ViewModel() {

    companion object {
        private const val TAP_DISTANCE_THRESHOLD_NORM = 0.01
        private const val CLOSED_OUTLINE_DISTANCE_THRESHOLD_NORM = 0.04
    }

    // ---------- public state ----------

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _brushState = MutableStateFlow(EditorBrushState())
    val brushState: StateFlow<EditorBrushState> = _brushState.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _stickerName = MutableStateFlow("My Sticker")
    val stickerName: StateFlow<String> = _stickerName.asStateFlow()

    private val _finishRecipe = MutableStateFlow(FinishRecipe())
    val finishRecipe: StateFlow<FinishRecipe> = _finishRecipe.asStateFlow()

    val zoomAssistEnabled: StateFlow<Boolean> = settingsRepository.zoomAssistEnabled

    // ---------- internal state ----------

    private var originalBitmap: Bitmap? = null
    private var baseConfidenceMask: FloatArray? = null
    private var confidenceMask: FloatArray? = null
    private var maskWidth: Int = 0
    private var maskHeight: Int = 0
    private var subjects: List<com.google.mlkit.vision.segmentation.subject.Subject> = emptyList()
    private var editingSticker: Sticker? = null
    private var backgroundBitmap: Bitmap? = null

    // Accumulated brush strokes for undo support
    private val brushStrokes = mutableListOf<BrushStroke>()
    private val redoStrokes = mutableListOf<BrushStroke>()

    // Current active stroke being drawn
    private var activeStrokePoints = mutableListOf<PointF>()
    private var previewJob: Job? = null
    private var committedRenderJob: Job? = null
    private var previewGeneration = 0L
    private var finishGeneration = 0L

    // ---------- public actions ----------

    fun loadAndSegment(uri: Uri) {
        beginNewSession()
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
                val finishedPreview = withContext(Dispatchers.Default) {
                    buildFinishedPreview(preview)
                }
                val selectionDimBitmap = withContext(Dispatchers.Default) {
                    segmentationHelper.buildSelectionDimBitmap(
                        confidenceMask = result.confidenceMask,
                        maskWidth = result.maskWidth,
                        maskHeight = result.maskHeight,
                    )
                }
                _uiState.value = EditorUiState.SegmentationReady(
                    originalBitmap = result.original,
                    previewBitmap = preview,
                    selectionDimBitmap = selectionDimBitmap,
                    finishedPreviewBitmap = finishedPreview,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _uiState.value = EditorUiState.Error(
                    "This image is too large to process. Please choose a smaller photo."
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

    /** Reopen a saved sticker using its persisted source and current mask. */
    fun loadExisting(stickerId: Long) {
        beginNewSession()
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            try {
                val sticker = repository.getSticker(stickerId)
                    ?: error("Sticker no longer exists")
                val editable = repository.loadEditableSticker(sticker)
                    ?: error("This sticker can no longer be edited")

                originalBitmap = editable.originalBitmap
                baseConfidenceMask = editable.confidenceMask.copyOf()
                confidenceMask = editable.confidenceMask.copyOf()
                maskWidth = editable.maskWidth
                maskHeight = editable.maskHeight
                subjects = emptyList()
                editingSticker = sticker
                _finishRecipe.value = editable.finishRecipe
                backgroundBitmap = editable.backgroundBitmap
                _stickerName.value = sticker.name

                val preview = buildPreview()
                val finishedPreview = withContext(Dispatchers.Default) {
                    buildFinishedPreview(preview)
                }
                val selectionDimBitmap = withContext(Dispatchers.Default) {
                    segmentationHelper.buildSelectionDimBitmap(
                        confidenceMask = confidenceMask!!,
                        maskWidth = maskWidth,
                        maskHeight = maskHeight,
                    )
                }
                _uiState.value = EditorUiState.SegmentationReady(
                    originalBitmap = editable.originalBitmap,
                    previewBitmap = preview,
                    selectionDimBitmap = selectionDimBitmap,
                    finishedPreviewBitmap = finishedPreview,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _uiState.value = EditorUiState.Error(
                    "This sticker is too large to reopen. Please try a smaller image."
                )
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(e.message ?: "Could not open sticker")
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
                    redoStrokes.clear()
                    updateHistoryState()
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
                fillEnclosed = isClosedOutline(activeStrokePoints),
            )
            brushStrokes.add(stroke)
            redoStrokes.clear()
            updateHistoryState()
            activeStrokePoints.clear()
            cancelPreviewWork()
            recomputeMaskFromStrokes()
        }
    }

    fun undoLastStroke() {
        if (brushStrokes.isNotEmpty()) {
            cancelPreviewWork()
            redoStrokes.add(brushStrokes.removeLast())
            updateHistoryState()
            recomputeMaskFromStrokes()
        }
    }

    fun redoLastStroke() {
        if (redoStrokes.isNotEmpty()) {
            cancelPreviewWork()
            brushStrokes.add(redoStrokes.removeLast())
            updateHistoryState()
            recomputeMaskFromStrokes()
        }
    }

    fun resetEdits() {
        cancelPreviewWork()
        brushStrokes.clear()
        redoStrokes.clear()
        activeStrokePoints.clear()
        val base = baseConfidenceMask ?: return
        originalBitmap ?: return
        updateHistoryState()

        viewModelScope.launch {
            confidenceMask = base.copyOf()
            val current = _uiState.value
            if (current is EditorUiState.SegmentationReady) {
                val selectionDimBitmap = withContext(Dispatchers.Default) {
                    segmentationHelper.buildSelectionDimBitmap(
                        confidenceMask = base,
                        maskWidth = maskWidth,
                        maskHeight = maskHeight,
                    )
                }
                val preview = buildPreview()
                val finishedPreview = withContext(Dispatchers.Default) {
                    buildFinishedPreview(preview)
                }
                publishRenderedState(
                    current = current,
                    preview = preview,
                    selectionDim = selectionDimBitmap,
                    finishedPreview = finishedPreview,
                )
            }
        }
    }

    fun setStickerName(name: String) {
        _stickerName.value = name
    }

    fun setFinishRecipe(recipe: FinishRecipe) {
        _finishRecipe.value = recipe.copy(
            outlineWidth = recipe.outlineWidth.coerceIn(0f, 28f),
            scale = recipe.scale.coerceIn(0.55f, 1.35f),
            offsetX = recipe.offsetX.coerceIn(-0.45f, 0.45f),
            offsetY = recipe.offsetY.coerceIn(-0.45f, 0.45f),
        )
        finishGeneration++
        val current = _uiState.value as? EditorUiState.SegmentationReady ?: return
        val finishedPreview = buildFinishedPreview(current.previewBitmap)
        val oldFinishedPreview = current.finishedPreviewBitmap
        _uiState.value = current.copy(finishedPreviewBitmap = finishedPreview)
        if (oldFinishedPreview !== current.previewBitmap &&
            oldFinishedPreview !== finishedPreview &&
            !oldFinishedPreview.isRecycled
        ) {
            oldFinishedPreview.recycle()
        }
    }

    fun setBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            val path = repository.persistBackgroundImage(uri) ?: return@launch
            val bitmap = repository.loadBitmapFromPath(path) ?: return@launch
            backgroundBitmap = bitmap
            setFinishRecipe(
                _finishRecipe.value.copy(
                    backgroundType = FinishBackgroundType.IMAGE,
                    backgroundImagePath = path,
                )
            )
        }
    }

    fun clearBackgroundImage() {
        backgroundBitmap = null
        setFinishRecipe(
            _finishRecipe.value.copy(
                backgroundType = FinishBackgroundType.TRANSPARENT,
                backgroundImagePath = null,
            )
        )
    }

    fun saveSticker() {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
        viewModelScope.launch {
            committedRenderJob?.join()
            _uiState.value = EditorUiState.Loading
            try {
                val mask = confidenceMask ?: error("No mask available")
                val original = originalBitmap ?: error("No source image available")
                val recipe = _finishRecipe.value
                val background = backgroundBitmap
                val stickerBitmap = withContext(Dispatchers.Default) {
                    segmentationHelper.buildStickerBitmap(
                        original = original,
                        confidenceMask = mask,
                        maskWidth = maskWidth,
                        maskHeight = maskHeight,
                    )
                }
                val finishedBitmap = withContext(Dispatchers.Default) {
                    StickerFinishRenderer.render(
                        cutout = stickerBitmap,
                        recipe = recipe,
                        backgroundBitmap = background,
                    )
                }
                try {
                    val existing = editingSticker
                    if (existing == null) {
                        repository.saveSticker(
                            bitmap = finishedBitmap,
                            name = _stickerName.value,
                            originalBitmap = original,
                            confidenceMask = mask.copyOf(),
                            maskWidth = maskWidth,
                            maskHeight = maskHeight,
                            finishRecipe = recipe,
                        )
                    } else {
                        repository.updateSticker(
                            sticker = existing,
                            bitmap = finishedBitmap,
                            originalBitmap = original,
                            confidenceMask = mask.copyOf(),
                            maskWidth = maskWidth,
                            maskHeight = maskHeight,
                            name = _stickerName.value,
                            finishRecipe = recipe,
                        )
                    }
                } finally {
                    finishedBitmap.recycle()
                    stickerBitmap.recycle()
                }
                _uiState.value = EditorUiState.Saved
            } catch (e: OutOfMemoryError) {
                _uiState.value = EditorUiState.Error(
                    "There is not enough memory to save this sticker. Please try a smaller image."
                )
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
            fillEnclosed = isClosedOutline(activeStrokePoints),
        )
        val allStrokes = currentStrokes + currentStroke

        val generation = ++previewGeneration
        val finishVersion = finishGeneration
        val recipe = _finishRecipe.value
        val background = backgroundBitmap
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
            val selectionDimBitmap = segmentationHelper.buildSelectionDimBitmap(
                confidenceMask = updatedMask,
                maskWidth = maskWidth,
                maskHeight = maskHeight,
            )
            val finishedPreview = StickerFinishRenderer.render(preview, recipe, background)

            val current = _uiState.value
            if (current is EditorUiState.SegmentationReady) {
                withContext(Dispatchers.Main) {
                    if (generation == previewGeneration) {
                        val currentFinishedPreview = if (finishVersion == finishGeneration) {
                            finishedPreview
                        } else {
                            buildFinishedPreview(preview).also { finishedPreview.recycle() }
                        }
                        publishRenderedState(
                            current = current,
                            preview = preview,
                            selectionDim = selectionDimBitmap,
                            finishedPreview = currentFinishedPreview,
                        )
                    } else {
                        preview.recycle()
                        selectionDimBitmap.recycle()
                        finishedPreview.recycle()
                    }
                }
            } else {
                preview.recycle()
                selectionDimBitmap.recycle()
                finishedPreview.recycle()
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
            val finishVersion = finishGeneration
            val recipe = _finishRecipe.value
            val background = backgroundBitmap
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
            val selectionDimBitmap = segmentationHelper.buildSelectionDimBitmap(
                confidenceMask = updatedMask,
                maskWidth = maskWidth,
                maskHeight = maskHeight,
            )
            val finishedPreview = StickerFinishRenderer.render(preview, recipe, background)
            val current = _uiState.value
            if (current is EditorUiState.SegmentationReady) {
                withContext(Dispatchers.Main) {
                    if (generation == previewGeneration) {
                        val currentFinishedPreview = if (finishVersion == finishGeneration) {
                            finishedPreview
                        } else {
                            buildFinishedPreview(preview).also { finishedPreview.recycle() }
                        }
                        publishRenderedState(
                            current = current,
                            preview = preview,
                            selectionDim = selectionDimBitmap,
                            finishedPreview = currentFinishedPreview,
                        )
                    } else {
                        preview.recycle()
                        selectionDimBitmap.recycle()
                        finishedPreview.recycle()
                    }
                }
            } else {
                preview.recycle()
                selectionDimBitmap.recycle()
                finishedPreview.recycle()
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

    private fun beginNewSession() {
        cancelPreviewWork()
        brushStrokes.clear()
        redoStrokes.clear()
        activeStrokePoints.clear()
        editingSticker = null
        _finishRecipe.value = FinishRecipe()
        finishGeneration++
        backgroundBitmap = null
        _stickerName.value = "My Sticker"
        updateHistoryState()
    }

    private fun updateHistoryState() {
        _canUndo.value = brushStrokes.isNotEmpty()
        _canRedo.value = redoStrokes.isNotEmpty()
    }

    private fun isClosedOutline(points: List<PointF>): Boolean {
        if (points.size < 3) return false
        val first = points.first()
        val last = points.last()
        val distance = kotlin.math.hypot(
            (first.x - last.x).toDouble(),
            (first.y - last.y).toDouble(),
        )
        return distance <= CLOSED_OUTLINE_DISTANCE_THRESHOLD_NORM
    }

    override fun onCleared() {
        cancelPreviewWork()
        super.onCleared()
    }

    private fun publishRenderedState(
        current: EditorUiState.SegmentationReady,
        preview: Bitmap,
        selectionDim: Bitmap,
        finishedPreview: Bitmap,
    ) {
        _uiState.value = current.copy(
            previewBitmap = preview,
            selectionDimBitmap = selectionDim,
            finishedPreviewBitmap = finishedPreview,
        )
        listOf(current.previewBitmap, current.selectionDimBitmap, current.finishedPreviewBitmap)
            .distinct()
            .forEach { bitmap ->
                if (bitmap !== preview && bitmap !== selectionDim && bitmap !== finishedPreview &&
                    !bitmap.isRecycled
                ) {
                    bitmap.recycle()
                }
            }
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

    private fun buildFinishedPreview(cutout: Bitmap): Bitmap = StickerFinishRenderer.render(
        cutout = cutout,
        recipe = _finishRecipe.value,
        backgroundBitmap = backgroundBitmap,
    )

}
