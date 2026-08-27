package com.stickerit.app.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stickerit.app.data.model.BrushMode
import com.stickerit.app.data.model.EditorUiState
import com.stickerit.app.data.model.FinishRecipe
import com.stickerit.app.R
import com.stickerit.app.ui.components.BrushMagnifier
import com.stickerit.app.ui.components.BrushOverlay

@Composable
fun StickerEditorScreen(
    imageUri: Uri? = null,
    stickerId: Long? = null,
    onStickerSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: StickerEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val brushState by viewModel.brushState.collectAsStateWithLifecycle()
    val stickerName by viewModel.stickerName.collectAsStateWithLifecycle()
    val zoomAssistEnabled by viewModel.zoomAssistEnabled.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val finishRecipe by viewModel.finishRecipe.collectAsStateWithLifecycle()

    var showNameDialog by remember { mutableStateOf(false) }
    var showPreviewMode by remember { mutableStateOf(false) }
    var showFinishStudio by remember { mutableStateOf(false) }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.setBackgroundImage(uri)
    }

    LaunchedEffect(imageUri, stickerId) {
        when {
            stickerId != null -> viewModel.loadExisting(stickerId)
            imageUri != null -> viewModel.loadAndSegment(imageUri)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is EditorUiState.Saved) onStickerSaved()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            EditorTopBar(
                title = stringResource(if (stickerId == null) R.string.create_sticker_title else R.string.edit_sticker_title),
                onBack = onBack,
                onUndo = viewModel::undoLastStroke,
                onRedo = viewModel::redoLastStroke,
                onReset = viewModel::resetEdits,
                canEdit = uiState is EditorUiState.SegmentationReady,
                canUndo = canUndo,
                canRedo = canRedo,
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState is EditorUiState.SegmentationReady,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                EditorBottomBar(
                    brushMode = brushState.mode,
                    brushRadius = brushState.radius,
                    showPreview = showPreviewMode,
                    finishStudio = showFinishStudio,
                    finishRecipe = finishRecipe,
                    onBrushModeChange = viewModel::setBrushMode,
                    onBrushRadiusChange = viewModel::setBrushRadius,
                    onTogglePreview = {
                        showFinishStudio = false
                        showPreviewMode = !showPreviewMode
                    },
                    onOpenFinishStudio = {
                        showPreviewMode = true
                        showFinishStudio = true
                    },
                    onCloseFinishStudio = {
                        showFinishStudio = false
                        showPreviewMode = false
                    },
                    onFinishRecipeChange = viewModel::setFinishRecipe,
                    onPickBackground = {
                        backgroundPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSave = { showNameDialog = true },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                is EditorUiState.Idle, is EditorUiState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeCap = StrokeCap.Round)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.detecting_subject),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is EditorUiState.SegmentationReady -> {
                    EditorCanvas(
                        state = state,
                        showPreview = showPreviewMode || showFinishStudio,
                        brushMode = brushState.mode,
                        brushRadius = brushState.radius,
                        zoomAssistEnabled = zoomAssistEnabled,
                        onDragStart = viewModel::onBrushDragStart,
                        onDrag = viewModel::onBrushDrag,
                        onDragEnd = viewModel::onBrushDragEnd,
                    )
                }

                is EditorUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            when {
                                stickerId != null -> viewModel.loadExisting(stickerId)
                                imageUri != null -> viewModel.loadAndSegment(imageUri)
                            }
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    // Name dialog before saving
    if (showNameDialog) {
        StickerNameDialog(
            currentName = stickerName,
            onNameChange = viewModel::setStickerName,
            onConfirm = {
                showNameDialog = false
                viewModel.saveSticker()
            },
            onDismiss = { showNameDialog = false },
        )
    }
}

@Composable
private fun EditorCanvas(
    state: EditorUiState.SegmentationReady,
    showPreview: Boolean,
    brushMode: BrushMode,
    brushRadius: Float,
    zoomAssistEnabled: Boolean,
    onDragStart: (Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var brushCursorPosition by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (showPreview)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.surface
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Checkerboard background for transparency preview
        if (showPreview) {
            CheckerboardBackground(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.4f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Editing shows the source image at full strength so the user can
            // see exactly where the brush is being placed. Preview mode shows
            // the generated sticker on its own; layering both images here made
            // the subject appear doubled.
            if (!showPreview) {
                Image(
                    bitmap = state.originalBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.original_image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            state.originalBitmap.width.toFloat() / state.originalBitmap.height,
                            matchHeightConstraintsFirst = false,
                        ),
                    contentScale = ContentScale.Fit,
                )
                Image(
                    bitmap = state.selectionDimBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            state.originalBitmap.width.toFloat() / state.originalBitmap.height,
                            matchHeightConstraintsFirst = false,
                        ),
                    contentScale = ContentScale.Fit,
                )
                SelectionLegend(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                )
            } else {
                Image(
                    bitmap = state.finishedPreviewBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.sticker_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit,
                )
            }

            // Brush overlay (only when not in preview mode)
            if (!showPreview) {
                BrushOverlay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            state.originalBitmap.width.toFloat() / state.originalBitmap.height,
                        ),
                    brushMode = brushMode,
                    brushRadius = brushRadius,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onCursorPositionChange = { brushCursorPosition = it },
                )
                if (zoomAssistEnabled) {
                    BrushMagnifier(
                        originalBitmap = state.originalBitmap,
                        selectionDimBitmap = state.selectionDimBitmap,
                        position = brushCursorPosition,
                        brushMode = brushMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                state.originalBitmap.width.toFloat() / state.originalBitmap.height,
                            ),
                    )
                }
            }

        }
    }
}

@Composable
private fun SelectionLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(Color(0xFF00C864), CircleShape),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = stringResource(R.string.selection_legend),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    title: String,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    canEdit: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.back))
            }
        },
        actions = {
            AnimatedVisibility(visible = canEdit) {
                Row {
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.undo))
                    }
                    IconButton(onClick = onRedo, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.redo))
                    }
                    IconButton(onClick = onReset, enabled = canUndo) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.reset))
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun EditorBottomBar(
    brushMode: BrushMode,
    brushRadius: Float,
    showPreview: Boolean,
    finishStudio: Boolean,
    finishRecipe: FinishRecipe,
    onBrushModeChange: (BrushMode) -> Unit,
    onBrushRadiusChange: (Float) -> Unit,
    onTogglePreview: () -> Unit,
    onOpenFinishStudio: () -> Unit,
    onCloseFinishStudio: () -> Unit,
    onFinishRecipeChange: (FinishRecipe) -> Unit,
    onPickBackground: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        if (finishStudio) {
            FinishStudioPanel(
                recipe = finishRecipe,
                onRecipeChange = onFinishRecipeChange,
                onPickBackground = onPickBackground,
                onClearBackground = viewModel::clearBackgroundImage,
                onBackToBrush = onCloseFinishStudio,
                onSave = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.brush_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Include / Exclude toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BrushModeButton(
                        label = stringResource(R.string.include),
                        icon = Icons.Outlined.Add,
                        selected = brushMode == BrushMode.INCLUDE,
                        colour = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { onBrushModeChange(BrushMode.INCLUDE) },
                    )
                    BrushModeButton(
                        label = stringResource(R.string.exclude),
                        icon = Icons.Outlined.Remove,
                        selected = brushMode == BrushMode.EXCLUDE,
                        colour = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        onClick = { onBrushModeChange(BrushMode.EXCLUDE) },
                    )
                }

                // Brush size slider
                Column {
                    Text(
                        text = stringResource(R.string.brush_size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = brushRadius,
                        onValueChange = onBrushRadiusChange,
                        valueRange = 4f..60f,
                        steps = 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = stringResource(
                                    R.string.brush_size_accessibility,
                                    brushRadius.toInt(),
                                )
                            },
                    )
                }

                if (showPreview) {
                    OutlinedButton(
                        onClick = onOpenFinishStudio,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.finish_sticker_action))
                    }
                }

                // Preview toggle + Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onTogglePreview,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(
                            imageVector = if (showPreview) Icons.Outlined.Edit else Icons.Outlined.Preview,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (showPreview) R.string.edit else R.string.preview))
                    }

                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.save_sticker))
                    }
                }
            }
        }
    }
}

@Composable
private fun BrushModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    colour: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColour = if (selected)
        colour.copy(alpha = 0.15f)
    else
        MaterialTheme.colorScheme.surfaceVariant

    val borderColour = if (selected) colour else Color.Transparent

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .semantics {
                role = Role.RadioButton
                stateDescription = stringResource(
                    if (selected) R.string.selected else R.string.not_selected,
                )
            }
            .border(
                width = 2.dp,
                color = borderColour,
                shape = MaterialTheme.shapes.large,
            ),
        shape = MaterialTheme.shapes.large,
        color = containerColour,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colour else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colour else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StickerNameDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.name_your_sticker)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.sticker_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onNameChange(text.ifBlank { "My Sticker" })
                onConfirm()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/** Simple checkerboard canvas for transparent preview backgrounds */
@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cellSize = 20.dp.toPx()
        val cols = (size.width / cellSize).toInt() + 1
        val rows = (size.height / cellSize).toInt() + 1
        val light = Color(0xFFE0E0E0)
        val dark = Color(0xFFBDBDBD)
        for (row in 0..rows) {
            for (col in 0..cols) {
                drawRect(
                    color = if ((row + col) % 2 == 0) light else dark,
                    topLeft = androidx.compose.ui.geometry.Offset(col * cellSize, row * cellSize),
                    size = androidx.compose.ui.geometry.Size(cellSize, cellSize),
                )
            }
        }
    }
}
