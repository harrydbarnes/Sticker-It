package com.stickerit.app.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stickerit.app.R
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.model.StickerPackEntity
import com.stickerit.app.data.model.StickerPackItemEntity
import java.io.File

/**
 * Pack management stays in a dialog so the gallery selection remains intact
 * while the user decides where to publish it.
 */
@Composable
fun StickerPackManagerDialog(
    packs: List<StickerPackEntity>,
    selectedPackId: String?,
    packItems: List<StickerPackItemEntity>,
    stickers: List<Sticker>,
    onDismiss: () -> Unit,
    onSelectPack: (String) -> Unit,
    onCreatePack: (String) -> Unit,
    onRenamePack: (String, String) -> Unit,
    onDeletePack: (String) -> Unit,
    onPickTrayImage: (String) -> Unit,
    onReorderItems: (List<Long>) -> Unit,
    onUpdateMetadata: (String, Long, String, String) -> Unit,
    onConfirm: (String) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<StickerPackEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<StickerPackEntity?>(null) }
    var metadataTarget by remember { mutableStateOf<StickerPackItemEntity?>(null) }
    val selectedPack = packs.firstOrNull { it.id == selectedPackId }
    val stickersById = remember(stickers) { stickers.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whatsapp_pack_manager_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.whatsapp_pack_manager_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (packs.isEmpty()) {
                    Text(
                        stringResource(R.string.no_packs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    packs.forEach { pack ->
                        PackRow(
                            pack = pack,
                            selected = pack.id == selectedPack?.id,
                            selectedCount = if (pack.id == selectedPack?.id) packItems.size else null,
                            onSelect = { onSelectPack(pack.id) },
                            onRename = { renameTarget = pack },
                            onDelete = { deleteTarget = pack },
                            onPickTrayImage = { onPickTrayImage(pack.id) },
                        )
                    }
                }
                TextButton(onClick = { showCreateDialog = true }) {
                    Text(stringResource(R.string.create_pack))
                }

                selectedPack?.let { pack ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(pack.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.pack_selection_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onPickTrayImage(pack.id) }) {
                        Text(stringResource(R.string.choose_tray_image))
                    }
                    Text(
                        stringResource(R.string.tray_image_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.included_stickers),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (packItems.isEmpty()) {
                        Text(
                            stringResource(R.string.pack_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        packItems.forEachIndexed { index, item ->
                            PackStickerRow(
                                item = item,
                                sticker = stickersById[item.stickerId],
                                canMoveUp = index > 0,
                                canMoveDown = index < packItems.lastIndex,
                                onMoveUp = {
                                    onReorderItems(moveItem(packItems, index, -1))
                                },
                                onMoveDown = {
                                    onReorderItems(moveItem(packItems, index, 1))
                                },
                                onEditMetadata = { metadataTarget = item },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedPack != null,
                onClick = { selectedPack?.let { onConfirm(it.id) } },
            ) {
                Text(stringResource(R.string.add_selected_to_pack))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showCreateDialog) {
        PackNameDialog(
            title = stringResource(R.string.create_pack),
            initialName = stringResource(R.string.new_pack),
            confirmLabel = stringResource(R.string.create_pack),
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreatePack(name)
                showCreateDialog = false
            },
        )
    }
    renameTarget?.let { pack ->
        PackNameDialog(
            title = stringResource(R.string.rename_pack),
            initialName = pack.name,
            confirmLabel = stringResource(R.string.save),
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRenamePack(pack.id, name)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { pack ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_pack_title)) },
            text = { Text(stringResource(R.string.delete_pack_message, pack.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePack(pack.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    metadataTarget?.let { item ->
        StickerMetadataDialog(
            item = item,
            stickerName = stickersById[item.stickerId]?.name.orEmpty(),
            onDismiss = { metadataTarget = null },
            onSave = { emojis, accessibilityText ->
                selectedPack?.let { pack ->
                    onUpdateMetadata(pack.id, item.stickerId, emojis, accessibilityText)
                }
                metadataTarget = null
            },
        )
    }
}

@Composable
private fun PackRow(
    pack: StickerPackEntity,
    selected: Boolean,
    selectedCount: Int?,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPickTrayImage: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(modifier = Modifier.weight(1f)) {
                    Text(pack.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    selectedCount?.let {
                        Text(
                            stringResource(R.string.pack_sticker_count, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.rename_pack))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
            TextButton(onClick = onPickTrayImage, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.choose_tray_image))
            }
        }
    }
}

@Composable
private fun PackStickerRow(
    item: StickerPackItemEntity,
    sticker: Sticker?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEditMetadata: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = sticker?.let { File(it.filePath) },
            contentDescription = sticker?.name,
            modifier = Modifier.size(44.dp),
            contentScale = ContentScale.Fit,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                sticker?.name ?: "Sticker",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                item.emojis,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
        }
        TextButton(onClick = onEditMetadata) {
            Text(stringResource(R.string.edit_metadata))
        }
    }
}

private fun moveItem(items: List<StickerPackItemEntity>, index: Int, delta: Int): List<Long> {
    val target = index + delta
    if (index !in items.indices || target !in items.indices) return items.map { it.stickerId }
    val ids = items.map { it.stickerId }.toMutableList()
    val moving = ids[index]
    ids[index] = ids[target]
    ids[target] = moving
    return ids
}

@Composable
private fun PackNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.pack_name)) },
                singleLine = true,
                isError = name.isBlank(),
                supportingText = if (name.isBlank()) {
                    { Text(stringResource(R.string.pack_name_required)) }
                } else null,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun StickerMetadataDialog(
    item: StickerPackItemEntity,
    stickerName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var emojis by remember(item) { mutableStateOf(item.emojis) }
    var accessibilityText by remember(item) {
        mutableStateOf(item.accessibilityText.ifBlank { stickerName })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sticker_metadata)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = emojis,
                    onValueChange = { emojis = it },
                    label = { Text(stringResource(R.string.emoji_keywords)) },
                    placeholder = { Text(stringResource(R.string.emoji_keywords_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = accessibilityText,
                    onValueChange = { accessibilityText = it.take(120) },
                    label = { Text(stringResource(R.string.accessibility_description)) },
                    placeholder = { Text(stringResource(R.string.accessibility_description_hint)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(emojis, accessibilityText) }) {
                Text(stringResource(R.string.save_metadata))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
