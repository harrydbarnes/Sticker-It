package com.stickerit.app.ui.gallery

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stickerit.app.data.model.GalleryUiState
import com.stickerit.app.data.model.Sticker
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun StickerGalleryScreen(
    onBack: () -> Unit,
    onEditSticker: (Long) -> Unit,
    viewModel: StickerGalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Observe snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Sticker to confirm deletion
    var deleteTarget by remember { mutableStateOf<Sticker?>(null) }
    var renameTarget by remember { mutableStateOf<Sticker?>(null) }

    Scaffold(
        topBar = {
            GalleryTopBar(
                onBack = onBack,
                isGboardInstalled = viewModel.isGboardInstalled,
                onAddToGboard = viewModel::addPackToGboard,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val state = uiState) {
            is GalleryUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }

            is GalleryUiState.Empty -> {
                EmptyGallery(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onCreateSticker = onBack,
                )
            }

            is GalleryUiState.Ready -> {
                StickerGrid(
                    stickers = state.stickers,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onDelete = { deleteTarget = it },
                    onRename = { renameTarget = it },
                    onShare = { sticker ->
                        val intent = viewModel.buildShareIntent(sticker)
                        context.startActivity(Intent.createChooser(intent, "Share sticker").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onReorder = viewModel::reorderStickers,
                )
            }
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { sticker ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Delete Sticker?") },
            text = { Text("\"${sticker.name}\" will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSticker(sticker)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }

    // Rename dialog
    renameTarget?.let { sticker ->
        RenameDialog(
            currentName = sticker.name,
            onConfirm = { newName ->
                viewModel.renameSticker(sticker, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    onBack: () -> Unit,
    isGboardInstalled: Boolean,
    onAddToGboard: () -> Unit,
) {
    TopAppBar(
        title = { Text("My Stickers") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back")
            }
        },
        actions = {
            if (isGboardInstalled) {
                Button(
                    onClick = onAddToGboard,
                    modifier = Modifier.padding(end = 8.dp),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Keyboard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Add to GBoard", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerGrid(
    stickers: List<Sticker>,
    modifier: Modifier = Modifier,
    onDelete: (Sticker) -> Unit,
    onRename: (Sticker) -> Unit,
    onShare: (Sticker) -> Unit,
    onReorder: (List<Sticker>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var selectedSticker by remember { mutableStateOf<Sticker?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = stickers,
            key = { it.id },
        ) { sticker ->
            StickerItem(
                sticker = sticker,
                isSelected = selectedSticker?.id == sticker.id,
                modifier = Modifier.animateItem(),
                onClick = {
                    selectedSticker = if (selectedSticker?.id == sticker.id) null else sticker
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedSticker = sticker
                },
                onDelete = { onDelete(sticker) },
                onRename = { onRename(sticker) },
                onShare = { onShare(sticker) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerItem(
    sticker: Sticker,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1f,
        label = "itemScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 1.dp,
        label = "itemElevation",
    )

    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.scale(scale),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        onLongClick()
                        showMenu = true
                    }
                )
                .then(
                    if (isSelected) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.large,
                    ) else Modifier
                ),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Checkerboard for transparent areas
                CheckerboardTile(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large),
                )

                // Sticker image
                AsyncImage(
                    model = File(sticker.filePath),
                    contentDescription = sticker.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit,
                )

                // GBoard badge
                if (sticker.addedToGboard) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Added to GBoard",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        // Sticker name
        Text(
            text = sticker.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                .align(Alignment.BottomCenter)
                .offset(y = 20.dp),
            textAlign = TextAlign.Center,
        )

        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = MaterialTheme.shapes.large,
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { showMenu = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                onClick = { showMenu = false; onShare() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { showMenu = false; onDelete() },
            )
        }
    }
}

@Composable
private fun EmptyGallery(
    modifier: Modifier = Modifier,
    onCreateSticker: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.SentimentDissatisfied,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No stickers yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Go back and create your first sticker!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreateSticker) {
            Text("Create Sticker")
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Sticker") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.ifBlank { currentName }) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/** Mini checkerboard tile used in grid items */
@Composable
private fun CheckerboardTile(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = 12.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val light = Color(0xFFF5F5F5)
        val dark = Color(0xFFE0E0E0)
        for (row in 0..rows) {
            for (col in 0..cols) {
                drawRect(
                    color = if ((row + col) % 2 == 0) light else dark,
                    topLeft = androidx.compose.ui.geometry.Offset(col * cell, row * cell),
                    size = androidx.compose.ui.geometry.Size(cell, cell),
                )
            }
        }
    }
}
