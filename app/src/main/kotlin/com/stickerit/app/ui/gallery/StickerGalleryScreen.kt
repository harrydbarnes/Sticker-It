package com.stickerit.app.ui.gallery

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stickerit.app.data.model.GalleryUiState
import com.stickerit.app.data.model.Sticker
import java.io.File

@Composable
fun StickerGalleryScreen(onBack: () -> Unit, viewModel: StickerGalleryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var deleteTarget by remember { mutableStateOf<Sticker?>(null) }
    var renameTarget by remember { mutableStateOf<Sticker?>(null) }

    LaunchedEffect(Unit) { viewModel.snackbarMessage.collect(snackbarHostState::showSnackbar) }
    Scaffold(
        topBar = { GalleryTopBar(onBack, selectedIds.size, onClear = { selectedIds = emptySet() }, onAddToWhatsApp = {
            val stickers = (uiState as? GalleryUiState.Ready)?.stickers.orEmpty().filter { it.id in selectedIds }
            viewModel.addOrUpdateWhatsAppPack(stickers)
        }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val state = uiState) {
            GalleryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            GalleryUiState.Empty -> EmptyGallery(Modifier.fillMaxSize().padding(padding), onBack)
            is GalleryUiState.Ready -> StickerGrid(
                stickers = state.stickers,
                selectedIds = selectedIds,
                modifier = Modifier.fillMaxSize().padding(padding),
                onToggle = { id -> selectedIds = selectedIds.let { if (id in it) it - id else it + id } },
                onSelectAll = { selectedIds = state.stickers.take(30).mapTo(linkedSetOf()) { it.id } },
                onShare = { sticker -> context.startActivity(Intent.createChooser(viewModel.buildShareIntent(sticker), "Share sticker")) },
                onDelete = { deleteTarget = it },
                onRename = { renameTarget = it },
            )
        }
    }
    deleteTarget?.let { sticker ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete sticker?") },
            text = { Text("${sticker.name} will be permanently removed from your library.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteSticker(sticker); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
    renameTarget?.let { sticker -> RenameStickerDialog(
        sticker = sticker,
        onDismiss = { renameTarget = null },
        onRename = { name -> viewModel.renameSticker(sticker, name); renameTarget = null },
    ) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(onBack: () -> Unit, selectionCount: Int, onClear: () -> Unit, onAddToWhatsApp: () -> Unit) {
    TopAppBar(
        title = { Text(if (selectionCount == 0) "My stickers" else "$selectionCount selected") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
        actions = {
            if (selectionCount > 0) {
                TextButton(onClick = onClear) { Text("Clear") }
                FilledTonalButton(
                    onClick = onAddToWhatsApp,
                    enabled = selectionCount in 3..30,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("WhatsApp") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun StickerGrid(stickers: List<Sticker>, selectedIds: Set<Long>, modifier: Modifier, onToggle: (Long) -> Unit, onSelectAll: () -> Unit, onShare: (Sticker) -> Unit, onDelete: (Sticker) -> Unit, onRename: (Sticker) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), modifier = modifier, contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Choose 3–30 stickers, then add or update your WhatsApp pack.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSelectAll, modifier = Modifier.padding(top = 2.dp)) { Text("Select first 30") }
            }
        }
        items(stickers, key = { it.id }) { sticker -> StickerTile(sticker, sticker.id in selectedIds, { onToggle(sticker.id) }, { onShare(sticker) }, { onDelete(sticker) }, { onRename(sticker) }) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(sticker: Sticker, selected: Boolean, onToggle: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column {
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).combinedClickable(onClick = onToggle, onLongClick = { menuExpanded = true }),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Checkerboard(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large))
                AsyncImage(File(sticker.filePath), sticker.name, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                if (selected) Icon(Icons.Outlined.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Outlined.MoreVert, "More options") }
                DropdownMenu(menuExpanded, { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menuExpanded = false; onShare() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
        Text(sticker.name, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

@Composable
private fun RenameStickerDialog(sticker: Sticker, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember(sticker.id) { mutableStateOf(sticker.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename sticker") },
        text = { androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(name.trim().ifBlank { sticker.name }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyGallery(modifier: Modifier, onBack: () -> Unit) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(Icons.Outlined.StickyNote2, null, Modifier.size(72.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp)); Text("Your sticker library is empty", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp)); Text("Create a cut-out, then build a WhatsApp pack from your favourites.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 32.dp))
    Spacer(Modifier.height(24.dp)); FilledTonalButton(onClick = onBack) { Text("Create a sticker") }
}

@Composable
private fun Checkerboard(modifier: Modifier) = Canvas(modifier) {
    val cell = 12.dp.toPx(); val light = Color(0xFFF1F1F1); val dark = Color(0xFFD8D8D8)
    repeat((size.height / cell).toInt() + 1) { row -> repeat((size.width / cell).toInt() + 1) { column -> drawRect(if ((row + column) % 2 == 0) light else dark, androidx.compose.ui.geometry.Offset(column * cell, row * cell), androidx.compose.ui.geometry.Size(cell, cell)) } }
}
