package com.stickerit.app.ui.gallery

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stickerit.app.R
import com.stickerit.app.data.model.GalleryUiState
import com.stickerit.app.data.model.Sticker
import kotlinx.coroutines.flow.flowOf
import java.io.File

@Composable
fun StickerGalleryScreen(
    onBack: () -> Unit,
    onEdit: (Sticker) -> Unit,
    viewModel: StickerGalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var deleteTarget by remember { mutableStateOf<Sticker?>(null) }
    var renameTarget by remember { mutableStateOf<Sticker?>(null) }
    var showPackManager by remember { mutableStateOf(false) }
    var selectedPackId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCreatedPackId by remember { mutableStateOf<String?>(null) }
    var trayImagePackId by remember { mutableStateOf<String?>(null) }
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val packItemsFlow = remember(selectedPackId) {
        selectedPackId?.let(viewModel::packItems) ?: flowOf(emptyList())
    }
    val packItems by packItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val trayImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        val packId = trayImagePackId
        trayImagePackId = null
        if (uri != null && packId != null) viewModel.setPackTrayImage(packId, uri)
    }

    LaunchedEffect(Unit) { viewModel.snackbarMessage.collect(snackbarHostState::showSnackbar) }
    LaunchedEffect(Unit) {
        viewModel.createdPack.collect { createdId ->
            pendingCreatedPackId = createdId
            selectedPackId = createdId
            showPackManager = true
        }
    }
    LaunchedEffect(packs) {
        when {
            pendingCreatedPackId != null && packs.any { it.id == pendingCreatedPackId } -> {
                pendingCreatedPackId = null
            }
            pendingCreatedPackId == null &&
                (selectedPackId == null || packs.none { it.id == selectedPackId }) -> {
                selectedPackId = packs.firstOrNull()?.id
            }
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { GalleryTopBar(onBack, selectedIds.size, onClear = { selectedIds = emptySet() }, onAddToWhatsApp = {
            showPackManager = true
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
                onEdit = onEdit,
                onDelete = { deleteTarget = it },
                onRename = { renameTarget = it },
            )
        }
    }
    if (showPackManager) {
        StickerPackManagerDialog(
            packs = packs,
            selectedPackId = selectedPackId,
            packItems = packItems,
            stickers = (uiState as? GalleryUiState.Ready)?.stickers.orEmpty(),
            onDismiss = { showPackManager = false },
            onSelectPack = { selectedPackId = it },
            onCreatePack = viewModel::createPack,
            onRenamePack = viewModel::renamePack,
            onDeletePack = viewModel::deletePack,
            onPickTrayImage = { packId ->
                trayImagePackId = packId
                trayImagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onReorderItems = { ids -> selectedPackId?.let { viewModel.reorderPackItems(it, ids) } },
            onUpdateMetadata = viewModel::updatePackItemMetadata,
            onConfirm = { packId ->
                val stickers = (uiState as? GalleryUiState.Ready)?.stickers.orEmpty()
                    .filter { it.id in selectedIds }
                viewModel.addOrUpdateWhatsAppPack(packId, stickers)
                showPackManager = false
            },
        )
    }
    deleteTarget?.let { sticker ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_sticker_title)) },
            text = { Text(stringResource(R.string.delete_sticker_message, sticker.name)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteSticker(sticker); deleteTarget = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
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
            title = {
                Text(
                    if (selectionCount == 0) stringResource(R.string.gallery_title)
                    else stringResource(R.string.selected_count, selectionCount),
                )
            },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, stringResource(R.string.back)) } },
        actions = {
            if (selectionCount > 0) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
                FilledTonalButton(
                    onClick = onAddToWhatsApp,
                    enabled = selectionCount in 3..30,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(stringResource(R.string.whatsapp)) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun StickerGrid(stickers: List<Sticker>, selectedIds: Set<Long>, modifier: Modifier, onToggle: (Long) -> Unit, onSelectAll: () -> Unit, onShare: (Sticker) -> Unit, onEdit: (Sticker) -> Unit, onDelete: (Sticker) -> Unit, onRename: (Sticker) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), modifier = modifier, contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column {
                Text(stringResource(R.string.whatsapp_selection_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSelectAll, modifier = Modifier.padding(top = 2.dp)) { Text(stringResource(R.string.select_first_30)) }
            }
        }
        items(stickers, key = { it.id }) { sticker -> StickerTile(sticker, sticker.id in selectedIds, { onToggle(sticker.id) }, { onShare(sticker) }, { onEdit(sticker) }, { onDelete(sticker) }, { onRename(sticker) }) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(sticker: Sticker, selected: Boolean, onToggle: () -> Unit, onShare: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectionDescription = stringResource(
        if (selected) R.string.selected else R.string.not_selected,
    )
    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(onClick = onToggle, onLongClick = { menuExpanded = true })
                .semantics {
                    contentDescription = sticker.name
                    stateDescription = selectionDescription
                },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Checkerboard(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large))
                AsyncImage(File(sticker.filePath), sticker.name, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                if (selected) Icon(Icons.Outlined.CheckCircle, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(menuExpanded, { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menuExpanded = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menuExpanded = false; onDelete() })
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
        title = { Text(stringResource(R.string.rename_sticker_title)) },
        text = { androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(name.trim().ifBlank { sticker.name }) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EmptyGallery(modifier: Modifier, onBack: () -> Unit) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(Icons.Outlined.StickyNote2, null, Modifier.size(72.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.empty_library_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.empty_library_message), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 32.dp))
    Spacer(Modifier.height(24.dp)); FilledTonalButton(onClick = onBack) { Text(stringResource(R.string.create_a_sticker)) }
}

@Composable
private fun Checkerboard(modifier: Modifier) = Canvas(modifier) {
    val cell = 12.dp.toPx(); val light = Color(0xFFF1F1F1); val dark = Color(0xFFD8D8D8)
    repeat((size.height / cell).toInt() + 1) { row -> repeat((size.width / cell).toInt() + 1) { column -> drawRect(if ((row + column) % 2 == 0) light else dark, androidx.compose.ui.geometry.Offset(column * cell, row * cell), androidx.compose.ui.geometry.Size(cell, cell)) } }
}
