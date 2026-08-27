package com.stickerit.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stickerit.app.R

@Composable
fun HomeScreen(
    onPickImage: (Uri) -> Unit,
    onOpenGallery: () -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let(onPickImage)
    }

    var fabPressed by remember { mutableStateOf(false) }
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.92f else 1f,
        animationSpec = tween(120, easing = EaseOutBack),
        label = "fabScale"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {

                // Use the full brand artwork here; the launcher variant omits the decorative dashes.
                Image(
                    painter = painterResource(R.drawable.ic_launcher_art),
                    contentDescription = "Sticker It app icon",
                    modifier = Modifier.size(120.dp),
                    contentScale = ContentScale.Fit,
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Sticker It",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Turn any photo into a sticker\nfor your library and WhatsApp",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(56.dp))

                // Primary CTA
                FilledTonalButton(
                    onClick = {
                        fabPressed = true
                        imagePicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .scale(fabScale),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Pick a Photo",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gallery button
                OutlinedButton(
                    onClick = onOpenGallery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Collections,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "My Stickers",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

            }
        }
    }

    LaunchedEffect(fabPressed) {
        if (fabPressed) {
            kotlinx.coroutines.delay(150)
            fabPressed = false
        }
    }
}
