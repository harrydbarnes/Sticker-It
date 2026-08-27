package com.stickerit.app.ui.editor

import android.net.Uri
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stickerit.app.data.model.BrushMode
import com.stickerit.app.data.model.EditorUiState
import com.stickerit.app.ui.components.BrushOverlay

@Composable
fun StickerEditorScreen(
    imageUri: Uri,
    onStickerSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: StickerEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val brushState by viewModel.brushState.collectAsStateWithLifecycle()
    val stickerName by viewModel.stickerName.collectAsStateWithLifecycle()

    var showNameDialog by remember { mutableStateOf(false) }
    var showPreviewMode by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        viewModel.loadAndSegment(imageUri)
    }

    LaunchedEffect(uiState) {
        if (uiState is EditorUiState.Saved) onStickerSaved()
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                onBack = onBack,
                onUndo = viewModel::undoLastStroke,
                onReset = viewModel::resetEdits,
                canEdit = uiState is EditorUiState.SegmentationReady,
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
                    onBrushModeChange = viewModel::setBrushMode,
                    onBrushRadiusChange = viewModel::setBrushRadius,
                    onTogglePreview = { showPreviewMode = !showPreviewMode },
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
                            text = "Detecting subject...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is EditorUiState.SegmentationReady -> {
                    EditorCanvas(
                        state = state,
                        showPreview = showPreviewMode,
                        brushMode = brushState.mode,
                        brushRadius = brushState.radius,
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
                        Button(onClick = { viewModel.loadAndSegment(imageUri) }) {
                            Text("Retry")
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
    onDragStart: (Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
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
                    contentDescription = "Original image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            state.originalBitmap.width.toFloat() / state.originalBitmap.height,
                            matchHeightConstraintsFirst = false,
                        ),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Image(
                    bitmap = state.previewBitmap.asImageBitmap(),
                    contentDescription = "Sticker preview",
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
                )
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    canEdit: Boolean,
) {
    TopAppBar(
        title = { Text("Create Sticker") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.Close, contentDescription = "Back")
            }
        },
        actions = {
            AnimatedVisibility(visible = canEdit) {
                Row {
                    IconButton(onClick = onUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = onReset) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset")
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
    onBrushModeChange: (BrushMode) -> Unit,
    onBrushRadiusChange: (Float) -> Unit,
    onTogglePreview: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Brush over an area, or close a loop to fill inside it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Include / Exclude toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrushModeButton(
                    label = "Include",
                    icon = Icons.Outlined.Add,
                    selected = brushMode == BrushMode.INCLUDE,
                    colour = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = { onBrushModeChange(BrushMode.INCLUDE) },
                )
                BrushModeButton(
                    label = "Exclude",
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
                    text = "Brush size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = brushRadius,
                    onValueChange = onBrushRadiusChange,
                    valueRange = 10f..80f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    Text(if (showPreview) "Edit" else "Preview")
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
                    Text("Save Sticker")
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
        title = { Text("Name Your Sticker") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Sticker name") },
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
