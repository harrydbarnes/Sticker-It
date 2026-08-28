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
import java.util.Collections
import java.util.IdentityHashMap
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

    val zoomAssistEnabled: StateFlow<Boolean> = settingsRepository.zoomAssistEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = false,
    )

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
    private var finishPreviewJob: Job? = null
    private var previewGeneration = 0L
    private var finishGeneration = 0L

    /**
     * Bitmaps are native resources. Render jobs retain their input bitmaps so a
     * state/session transition cannot recycle a bitmap while a worker is still
     * reading it. Releases are deferred until the last worker lets go.
     */
    private val bitmapLifecycleLock = Any()
    private val bitmapUseCounts = IdentityHashMap<Bitmap, Int>()
    private val deferredBitmapReleases = Collections.newSetFromMap(
        IdentityHashMap<Bitmap, Boolean>(),
    )

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
                releaseSessionBitmaps(_uiState.value)
                _uiState.value = EditorUiState.Error(
                    "This image is too large to process. Please choose a smaller photo."
                )
            } catch (e: Exception) {
                releaseSessionBitmaps(_uiState.value)
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
                releaseSessionBitmaps(_uiState.value)
                _uiState.value = EditorUiState.Error(
                    "This sticker is too large to reopen. Please try a smaller image."
                )
            } catch (e: Exception) {
                releaseSessionBitmaps(_uiState.value)
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
            redoStrokes.add(brushStrokes.removeAt(brushStrokes.lastIndex))
            updateHistoryState()
            recomputeMaskFromStrokes()
        }
    }

    fun redoLastStroke() {
        if (redoStrokes.isNotEmpty()) {
            cancelPreviewWork()
            brushStrokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
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
        val original = originalBitmap ?: return
        val generation = ++previewGeneration
        val finishVersion = finishGeneration
        val recipe = _finishRecipe.value
        val background = backgroundBitmap
        retainBitmap(original)
        retainBitmap(background)
        updateHistoryState()

        val job = viewModelScope.launch(Dispatchers.Default) {
            var preview: Bitmap? = null
            var selectionDimBitmap: Bitmap? = null
            var finishedPreview: Bitmap? = null
            try {
                val resetMask = base.copyOf()
                selectionDimBitmap = segmentationHelper.buildSelectionDimBitmap(
                    confidenceMask = resetMask,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                )
                preview = segmentationHelper.buildStickerBitmap(
                    original = original,
                    confidenceMask = resetMask,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                )
                finishedPreview = StickerFinishRenderer.render(preview!!, recipe, background)

                withContext(Dispatchers.Main.immediate) {
                    if (generation != previewGeneration) return@withContext
                    if (_uiState.value !is EditorUiState.SegmentationReady) return@withContext

                    confidenceMask = resetMask
                    val renderedPreview = preview ?: return@withContext
                    val renderedSelection = selectionDimBitmap ?: return@withContext
                    val renderedFinishedPreview = finishedPreview
                    if (finishVersion == finishGeneration && renderedFinishedPreview != null) {
                        publishRenderedState(
                            preview = renderedPreview,
                            selectionDim = renderedSelection,
                            finishedPreview = renderedFinishedPreview,
                        )
                        preview = null
                        selectionDimBitmap = null
                        finishedPreview = null
                    } else {
                        publishPreviewState(
                            preview = renderedPreview,
                            selectionDim = renderedSelection,
                        )
                        preview = null
                        selectionDimBitmap = null
                        recycleBitmapWhenSafe(renderedFinishedPreview)
                        finishedPreview = null
                        scheduleFinishPreview(
                            preview = renderedPreview,
                            expectedPreviewGeneration = generation,
                        )
                    }
                }
            } finally {
                recycleBitmapWhenSafe(finishedPreview)
                recycleBitmapWhenSafe(selectionDimBitmap)
                recycleBitmapWhenSafe(preview)
            }
        }
        releaseBitmapUsesOnCompletion(job, background, original)
        committedRenderJob = job
    }

    fun setStickerName(name: String) {
        _stickerName.value = name
    }

    fun setFinishRecipe(recipe: FinishRecipe) {
        val normalizedRecipe = recipe.copy(
            outlineWidth = recipe.outlineWidth.coerceIn(0f, 28f),
            scale = recipe.scale.coerceIn(0.55f, 1.35f),
            offsetX = recipe.offsetX.coerceIn(-0.45f, 0.45f),
            offsetY = recipe.offsetY.coerceIn(-0.45f, 0.45f),
        )
        _finishRecipe.value = normalizedRecipe
        finishGeneration++
        val current = _uiState.value as? EditorUiState.SegmentationReady ?: return
        scheduleFinishPreview(
            preview = current.previewBitmap,
            expectedPreviewGeneration = previewGeneration,
        )
    }

    fun setBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            val path = repository.persistBackgroundImage(uri) ?: return@launch
            val bitmap = repository.loadBitmapFromPath(path) ?: return@launch
            val previousBackground = backgroundBitmap
            backgroundBitmap = bitmap
            recycleBitmapWhenSafe(previousBackground)
            setFinishRecipe(
                _finishRecipe.value.copy(
                    backgroundType = FinishBackgroundType.IMAGE,
                    backgroundImagePath = path,
                )
            )
        }
    }

    fun clearBackgroundImage() {
        val previousBackground = backgroundBitmap
        backgroundBitmap = null
        recycleBitmapWhenSafe(previousBackground)
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
        val original = originalBitmap
        val background = backgroundBitmap
        val recipe = _finishRecipe.value
        retainBitmap(original)
        retainBitmap(background)
        val job = viewModelScope.launch {
            committedRenderJob?.join()
            _uiState.value = EditorUiState.Loading
            try {
                val mask = confidenceMask ?: error("No mask available")
                val source = original ?: error("No source image available")
                var stickerBitmap: Bitmap? = null
                var finishedBitmap: Bitmap? = null
                try {
                    stickerBitmap = withContext(Dispatchers.Default) {
                        segmentationHelper.buildStickerBitmap(
                            original = source,
                            confidenceMask = mask,
                            maskWidth = maskWidth,
                            maskHeight = maskHeight,
                        )
                    }
                    finishedBitmap = withContext(Dispatchers.Default) {
                        StickerFinishRenderer.render(
                            cutout = stickerBitmap!!,
                            recipe = recipe,
                            backgroundBitmap = background,
                        )
                    }
                    val existing = editingSticker
                    if (existing == null) {
                        repository.saveSticker(
                            bitmap = finishedBitmap!!,
                            name = _stickerName.value,
                            originalBitmap = source,
                            confidenceMask = mask.copyOf(),
                            maskWidth = maskWidth,
                            maskHeight = maskHeight,
                            finishRecipe = recipe,
                        )
                    } else {
                        repository.updateSticker(
                            sticker = existing,
                            bitmap = finishedBitmap!!,
                            originalBitmap = source,
                            confidenceMask = mask.copyOf(),
                            maskWidth = maskWidth,
                            maskHeight = maskHeight,
                            name = _stickerName.value,
                            finishRecipe = recipe,
                        )
                    }
                } finally {
                    recycleBitmapWhenSafe(finishedBitmap)
                    recycleBitmapWhenSafe(stickerBitmap)
                }
                _uiState.value = EditorUiState.Saved
            } catch (e: OutOfMemoryError) {
                _uiState.value = EditorUiState.Error(
                    "There is not enough memory to save this sticker. Please try a smaller image."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(e.message ?: "Save failed")
            }
        }
        releaseBitmapUsesOnCompletion(job, background, original)
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
        val original = originalBitmap
        retainBitmap(original)
        retainBitmap(background)
        previewJob?.cancel()
        val job = viewModelScope.launch(Dispatchers.Default) {
            var preview: Bitmap? = null
            var selectionDimBitmap: Bitmap? = null
            var finishedPreview: Bitmap? = null
            try {
                // Conflate pointer events to one preview per visual frame instead of rendering
                // every raw move event. The final stroke remains precise on drag end.
                delay(16)
                val base = baseConfidenceMask ?: return@launch
                val source = original ?: return@launch
                val updatedMask = segmentationHelper.applyBrushStrokes(
                    confidenceMask = base,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                    brushStrokes = allStrokes,
                )

                preview = segmentationHelper.buildStickerBitmap(
                    original = source,
                    confidenceMask = updatedMask,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                )
                selectionDimBitmap = segmentationHelper.buildSelectionDimBitmap(
                    confidenceMask = updatedMask,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                )
                finishedPreview = StickerFinishRenderer.render(preview!!, recipe, background)

                withContext(Dispatchers.Main.immediate) {
                    if (generation != previewGeneration) return@withContext
                    if (_uiState.value !is EditorUiState.SegmentationReady) return@withContext

                    val renderedPreview = preview ?: return@withContext
                    val renderedSelection = selectionDimBitmap ?: return@withContext
                    val renderedFinishedPreview = finishedPreview
                    if (finishVersion == finishGeneration && renderedFinishedPreview != null) {
                        publishRenderedState(
                            preview = renderedPreview,
                            selectionDim = renderedSelection,
                            finishedPreview = renderedFinishedPreview,
                        )
                        preview = null
                        selectionDimBitmap = null
                        finishedPreview = null
                    } else {
                        publishPreviewState(
                            preview = renderedPreview,
                            selectionDim = renderedSelection,
                        )
                        preview = null
                        selectionDimBitmap = null
                        recycleBitmapWhenSafe(renderedFinishedPreview)
                        finishedPreview = null
                        scheduleFinishPreview(
                            preview = renderedPreview,
                            expectedPreviewGeneration = generation,
                        )
                    }
                }
            } finally {
                recycleBitmapWhenSafe(finishedPreview)
                recycleBitmapWhenSafe(selectionDimBitmap)
                recycleBitmapWhenSafe(preview)
            }
        }
        releaseBitmapUsesOnCompletion(job, background, original)
        previewJob = job
    }

    private fun recomputeMaskFromStrokes() {
        val currentStrokes = brushStrokes.toList()
        val generation = ++previewGeneration
        committedRenderJob?.cancel()
        val original = originalBitmap
        val background = backgroundBitmap
        val finishVersion = finishGeneration
        val recipe = _finishRecipe.value
        retainBitmap(original)
        retainBitmap(background)
        val job = viewModelScope.launch(Dispatchers.Default) {
            var preview: Bitmap? = null
            var selectionDimBitmap: Bitmap? = null
            var finishedPreview: Bitmap? = null
            try {
                val base = baseConfidenceMask ?: return@launch
                val source = original ?: return@launch
                val updatedMask = segmentationHelper.applyBrushStrokes(
                    confidenceMask = base,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                    brushStrokes = currentStrokes,
                )
                preview = segmentationHelper.buildStickerBitmap(
                    original = source,
                    confidenceMask = updatedMask,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                )
                selectionDimBitmap = segmentationHelper.buildSelectionDimBitmap(
                    confidenceMask = updatedMask,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                )
                finishedPreview = StickerFinishRenderer.render(preview!!, recipe, background)

                withContext(Dispatchers.Main.immediate) {
                    if (generation != previewGeneration) return@withContext
                    if (_uiState.value !is EditorUiState.SegmentationReady) return@withContext

                    confidenceMask = updatedMask
                    val renderedPreview = preview ?: return@withContext
                    val renderedSelection = selectionDimBitmap ?: return@withContext
                    val renderedFinishedPreview = finishedPreview
                    if (finishVersion == finishGeneration && renderedFinishedPreview != null) {
                        publishRenderedState(
                            preview = renderedPreview,
                            selectionDim = renderedSelection,
                            finishedPreview = renderedFinishedPreview,
                        )
                        preview = null
                        selectionDimBitmap = null
                        finishedPreview = null
                    } else {
                        publishPreviewState(
                            preview = renderedPreview,
                            selectionDim = renderedSelection,
                        )
                        preview = null
                        selectionDimBitmap = null
                        recycleBitmapWhenSafe(renderedFinishedPreview)
                        finishedPreview = null
                        scheduleFinishPreview(
                            preview = renderedPreview,
                            expectedPreviewGeneration = generation,
                        )
                    }
                }
            } finally {
                recycleBitmapWhenSafe(finishedPreview)
                recycleBitmapWhenSafe(selectionDimBitmap)
                recycleBitmapWhenSafe(preview)
            }
        }
        releaseBitmapUsesOnCompletion(job, background, original)
        committedRenderJob = job
    }

    private fun cancelPreviewWork() {
        previewGeneration++
        previewJob?.cancel()
        committedRenderJob?.cancel()
        finishPreviewJob?.cancel()
        previewJob = null
        committedRenderJob = null
        finishPreviewJob = null
    }

    private fun beginNewSession() {
        cancelPreviewWork()
        val previousState = _uiState.value
        _uiState.value = EditorUiState.Idle
        releaseSessionBitmaps(previousState)
        brushStrokes.clear()
        redoStrokes.clear()
        activeStrokePoints.clear()
        editingSticker = null
        _finishRecipe.value = FinishRecipe()
        finishGeneration++
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
        val previousState = _uiState.value
        _uiState.value = EditorUiState.Idle
        releaseSessionBitmaps(previousState)
        super.onCleared()
    }

    private fun publishRenderedState(
        preview: Bitmap,
        selectionDim: Bitmap,
        finishedPreview: Bitmap,
    ) {
        val current = _uiState.value as? EditorUiState.SegmentationReady
        if (current == null) {
            recycleBitmapWhenSafe(preview)
            recycleBitmapWhenSafe(selectionDim)
            recycleBitmapWhenSafe(finishedPreview)
            return
        }

        _uiState.value = current.copy(
            previewBitmap = preview,
            selectionDimBitmap = selectionDim,
            finishedPreviewBitmap = finishedPreview,
        )
        recycleReplacedBitmap(
            current.previewBitmap,
            preview,
            current.originalBitmap,
            current.finishedPreviewBitmap,
        )
        recycleReplacedBitmap(current.selectionDimBitmap, selectionDim)
        recycleReplacedBitmap(current.finishedPreviewBitmap, finishedPreview, preview)
    }

    /** Publish a new cut-out while the finishing preview is rendered separately. */
    private fun publishPreviewState(
        preview: Bitmap,
        selectionDim: Bitmap,
    ) {
        val current = _uiState.value as? EditorUiState.SegmentationReady
        if (current == null) {
            recycleBitmapWhenSafe(preview)
            recycleBitmapWhenSafe(selectionDim)
            return
        }

        _uiState.value = current.copy(
            previewBitmap = preview,
            selectionDimBitmap = selectionDim,
        )
        recycleReplacedBitmap(
            current.previewBitmap,
            preview,
            current.originalBitmap,
            current.finishedPreviewBitmap,
        )
        recycleReplacedBitmap(current.selectionDimBitmap, selectionDim)
    }

    /**
     * Render only the finishing recipe off the main thread. The preview bitmap
     * is retained for the lifetime of the job so a concurrent brush update can
     * safely replace and defer its release.
     */
    private fun scheduleFinishPreview(
        preview: Bitmap,
        expectedPreviewGeneration: Long,
    ) {
        finishPreviewJob?.cancel()
        val renderGeneration = finishGeneration
        val recipe = _finishRecipe.value
        val background = backgroundBitmap
        retainBitmap(preview)
        retainBitmap(background)

        val job = viewModelScope.launch(Dispatchers.Default) {
            var rendered: Bitmap? = null
            try {
                // Coalesce rapid slider changes into one render for the latest recipe.
                delay(16)
                rendered = StickerFinishRenderer.render(
                    cutout = preview,
                    recipe = recipe,
                    backgroundBitmap = background,
                )
                withContext(Dispatchers.Main.immediate) {
                    val current = _uiState.value as? EditorUiState.SegmentationReady
                    val candidate = rendered ?: return@withContext
                    if (
                        renderGeneration == finishGeneration &&
                        expectedPreviewGeneration == previewGeneration &&
                        current?.previewBitmap === preview
                    ) {
                        _uiState.value = current.copy(finishedPreviewBitmap = candidate)
                        recycleReplacedBitmap(current.finishedPreviewBitmap, candidate, preview)
                        rendered = null
                    }
                }
            } finally {
                recycleBitmapWhenSafe(rendered)
            }
        }
        releaseBitmapUsesOnCompletion(job, background, preview)
        finishPreviewJob = job
    }

    private fun recycleReplacedBitmap(bitmap: Bitmap?, vararg keep: Bitmap?) {
        if (bitmap == null || keep.any { it === bitmap }) return
        recycleBitmapWhenSafe(bitmap)
    }

    private fun releaseBitmapUsesOnCompletion(job: Job, vararg bitmaps: Bitmap?) {
        job.invokeOnCompletion {
            bitmaps.forEach(::releaseBitmapUse)
        }
    }

    private fun releaseSessionBitmaps(previousState: EditorUiState) {
        val bitmaps = Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())
        originalBitmap?.let(bitmaps::add)
        backgroundBitmap?.let(bitmaps::add)
        if (previousState is EditorUiState.SegmentationReady) {
            bitmaps.add(previousState.originalBitmap)
            bitmaps.add(previousState.previewBitmap)
            bitmaps.add(previousState.selectionDimBitmap)
            bitmaps.add(previousState.finishedPreviewBitmap)
        }
        originalBitmap = null
        backgroundBitmap = null
        bitmaps.forEach(::recycleBitmapWhenSafe)
    }

    private fun retainBitmap(bitmap: Bitmap?) {
        if (bitmap == null) return
        synchronized(bitmapLifecycleLock) {
            if (!bitmap.isRecycled) {
                bitmapUseCounts[bitmap] = (bitmapUseCounts[bitmap] ?: 0) + 1
            }
        }
    }

    private fun releaseBitmapUse(bitmap: Bitmap?) {
        if (bitmap == null) return
        synchronized(bitmapLifecycleLock) {
            val count = bitmapUseCounts[bitmap] ?: return
            if (count > 1) {
                bitmapUseCounts[bitmap] = count - 1
            } else {
                bitmapUseCounts.remove(bitmap)
                if (deferredBitmapReleases.remove(bitmap) && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun recycleBitmapWhenSafe(bitmap: Bitmap?) {
        if (bitmap == null) return
        synchronized(bitmapLifecycleLock) {
            if (bitmap.isRecycled) return
            if ((bitmapUseCounts[bitmap] ?: 0) > 0) {
                deferredBitmapReleases.add(bitmap)
            } else {
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

    private fun buildFinishedPreview(
        cutout: Bitmap,
        recipe: FinishRecipe = _finishRecipe.value,
        background: Bitmap? = backgroundBitmap,
    ): Bitmap = StickerFinishRenderer.render(
        cutout = cutout,
        recipe = recipe,
        backgroundBitmap = background,
    )

}
