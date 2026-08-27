package com.stickerit.app.ui.batch

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stickerit.app.R
import com.stickerit.app.data.model.BatchImportItem
import com.stickerit.app.data.model.BatchImportUiState
import com.stickerit.app.data.model.BatchItemStatus

@Composable
fun BatchImportScreen(
    uriStrings: List<String>,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: BatchImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uriStrings) {
        viewModel.initialize(uriStrings)
    }

    BatchImportContent(
        state = uiState,
        onBack = onBack,
        onStart = viewModel::start,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onFinished = onFinished,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportContent(
    state: BatchImportUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRetry: (Int) -> Unit,
    onFinished: () -> Unit,
) {
    val progressText = stringResource(R.string.batch_ready_count, state.completedCount, state.items.size) +
        if (state.failedCount == 0) "" else " • ${stringResource(R.string.batch_failed_count, state.failedCount)}"

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            BatchActionBar(
                state = state,
                onStart = onStart,
                onCancel = onCancel,
                onFinished = onFinished,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (state.isRunning) {
                            stringResource(R.string.batch_running_message, state.items.size)
                        } else {
                            stringResource(R.string.batch_review_message)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.batch_edit_later_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.items.isNotEmpty()) {
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            itemsIndexed(state.items, key = { _, item -> item.uriString }) { index, item ->
                BatchImportItemCard(
                    item = item,
                    onRetry = { onRetry(index) },
                )
            }
        }
    }
}

@Composable
private fun BatchActionBar(
    state: BatchImportUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onFinished: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            } else if (state.isFinished) {
                Button(
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.batch_view_library))
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = state.pendingCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.pendingCount == 1) stringResource(R.string.batch_create_one)
                        else stringResource(R.string.batch_create_many, state.pendingCount),
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchImportItemCard(
    item: BatchImportItem,
    onRetry: () -> Unit,
) {
    val statusText = when (item.status) {
        BatchItemStatus.QUEUED -> stringResource(R.string.batch_waiting)
        BatchItemStatus.PROCESSING -> stringResource(R.string.batch_creating)
        BatchItemStatus.COMPLETE -> stringResource(R.string.batch_ready_to_edit)
        BatchItemStatus.FAILED -> item.errorMessage ?: stringResource(R.string.batch_create_error)
        BatchItemStatus.CANCELLED -> stringResource(R.string.batch_cancelled)
    }
    val stateDescription = when (item.status) {
        BatchItemStatus.COMPLETE -> stringResource(R.string.batch_complete)
        BatchItemStatus.PROCESSING -> stringResource(R.string.batch_in_progress)
        BatchItemStatus.FAILED -> stringResource(R.string.batch_failed)
        BatchItemStatus.CANCELLED -> stringResource(R.string.batch_cancelled)
        BatchItemStatus.QUEUED -> stringResource(R.string.batch_waiting)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${item.displayName}, $statusText"
                this.stateDescription = stateDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = Uri.parse(item.uriString),
                contentDescription = item.displayName,
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == BatchItemStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            when (item.status) {
                BatchItemStatus.PROCESSING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                BatchItemStatus.COMPLETE -> Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(R.string.batch_complete),
                    tint = MaterialTheme.colorScheme.primary,
                )
                BatchItemStatus.FAILED, BatchItemStatus.CANCELLED -> {
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(
                            if (item.status == BatchItemStatus.FAILED) Icons.Outlined.ErrorOutline else Icons.Outlined.HourglassEmpty,
                            contentDescription = stateDescription,
                            tint = if (item.status == BatchItemStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
                BatchItemStatus.QUEUED -> Icon(
                    Icons.Outlined.HourglassEmpty,
                    contentDescription = stringResource(R.string.batch_waiting),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
