package com.stickerit.app.ui

import android.net.Uri
import android.util.Base64
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stickerit.app.ui.editor.StickerEditorScreen
import com.stickerit.app.ui.batch.BatchImportScreen
import com.stickerit.app.ui.gallery.StickerGalleryScreen
import com.stickerit.app.ui.home.HomeScreen
import com.stickerit.app.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

private sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Gallery : Route("gallery")
    data object Settings : Route("settings")
    data object Editor : Route("editor/{encodedUri}") {
        fun build(uri: Uri): String = "editor/${URLEncoder.encode(uri.toString(), "UTF-8")}"
    }
    data object ExistingEditor : Route("editor-sticker/{stickerId}") {
        fun build(stickerId: Long): String = "editor-sticker/$stickerId"
    }
    data object Batch : Route("batch/{encodedUris}") {
        fun build(uris: List<Uri>): String = "batch/${encodeBatchUris(uris)}"
    }
}

private val transitionSpec = tween<Float>(durationMillis = 340, easing = EaseInOutCubic)
private const val MAX_BATCH_IMAGES = 30

@Composable
fun StickerItNavHost(
    sharedImageUris: List<Uri>,
    onSharedUrisConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val openUris: (List<Uri>) -> Unit = { uris ->
        val normalizedUris = uris.distinct().take(MAX_BATCH_IMAGES)
        when (normalizedUris.size) {
            0 -> Unit
            1 -> navController.navigate(Route.Editor.build(normalizedUris.first()))
            else -> navController.navigate(Route.Batch.build(normalizedUris))
        }
    }

    LaunchedEffect(sharedImageUris) {
        if (sharedImageUris.isNotEmpty()) {
            openUris(sharedImageUris)
            onSharedUrisConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Home.path,
        enterTransition = {
            fadeIn(transitionSpec) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(340, easing = EaseInOutCubic),
            )
        },
        exitTransition = {
            fadeOut(transitionSpec) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(340, easing = EaseInOutCubic),
            )
        },
        popEnterTransition = {
            fadeIn(transitionSpec) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(340, easing = EaseInOutCubic),
            )
        },
        popExitTransition = {
            fadeOut(transitionSpec) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(340, easing = EaseInOutCubic),
            )
        },
    ) {
        composable(Route.Home.path) {
            HomeScreen(
                onPickImages = openUris,
                onOpenGallery = { navController.navigate(Route.Gallery.path) },
                onOpenSettings = { navController.navigate(Route.Settings.path) },
            )
        }

        composable(
            route = Route.Editor.path,
            arguments = listOf(navArgument("encodedUri") { type = NavType.StringType }),
        ) { back ->
            val uri = Uri.parse(URLDecoder.decode(back.arguments?.getString("encodedUri") ?: "", "UTF-8"))
            StickerEditorScreen(
                imageUri = uri,
                onStickerSaved = {
                    navController.navigate(Route.Gallery.path) {
                        popUpTo(Route.Home.path)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.Gallery.path) {
            StickerGalleryScreen(
                onBack = { navController.popBackStack() },
                onEdit = { sticker -> navController.navigate(Route.ExistingEditor.build(sticker.id)) },
            )
        }

        composable(
            route = Route.ExistingEditor.path,
            arguments = listOf(navArgument("stickerId") { type = NavType.LongType }),
        ) { back ->
            val stickerId = back.arguments?.getLong("stickerId") ?: 0L
            if (stickerId > 0L) {
                StickerEditorScreen(
                    stickerId = stickerId,
                    onStickerSaved = {
                        navController.navigate(Route.Gallery.path) {
                            popUpTo(Route.Home.path)
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Route.Batch.path,
            arguments = listOf(navArgument("encodedUris") { type = NavType.StringType }),
        ) { back ->
            val uris = decodeBatchUris(back.arguments?.getString("encodedUris").orEmpty())
            BatchImportScreen(
                uriStrings = uris.map(Uri::toString),
                onBack = { navController.popBackStack() },
                onFinished = {
                    navController.navigate(Route.Gallery.path) {
                        popUpTo(Route.Home.path)
                    }
                },
            )
        }

        composable(Route.Settings.path) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun encodeBatchUris(uris: List<Uri>): String = Base64.encodeToString(
    uris.joinToString("\n") { it.toString() }.encodeToByteArray(),
    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
)

private fun decodeBatchUris(encoded: String): List<Uri> = runCatching {
    Base64.decode(encoded, Base64.URL_SAFE)
        .decodeToString()
        .split('\n')
        .filter(String::isNotBlank)
        .map(Uri::parse)
}.getOrDefault(emptyList())
