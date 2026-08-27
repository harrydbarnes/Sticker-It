package com.stickerit.app.ui

import android.net.Uri
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
import com.stickerit.app.ui.gallery.StickerGalleryScreen
import com.stickerit.app.ui.home.HomeScreen
import java.net.URLDecoder
import java.net.URLEncoder

private sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Gallery : Route("gallery")
    data object Editor : Route("editor/{encodedUri}") {
        fun build(uri: Uri): String = "editor/${URLEncoder.encode(uri.toString(), "UTF-8")}"
    }
}

private val transitionSpec = tween<Float>(durationMillis = 340, easing = EaseInOutCubic)

@Composable
fun StickerItNavHost(
    sharedImageUris: List<Uri>,
    onSharedUrisConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    LaunchedEffect(sharedImageUris) {
        if (sharedImageUris.isNotEmpty()) {
            navController.navigate(Route.Editor.build(sharedImageUris.first()))
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
                onPickImage = { uri -> navController.navigate(Route.Editor.build(uri)) },
                onOpenGallery = { navController.navigate(Route.Gallery.path) },
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
            )
        }
    }
}
