package com.stickerit.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Brand colours — vibrant, expressive palette
val BrandPrimary = Color(0xFF6750A4)
val BrandSecondary = Color(0xFF625B71)
val BrandTertiary = Color(0xFF7D5260)
val BrandPrimaryContainer = Color(0xFFEADDFF)
val BrandSurface = Color(0xFFFFFBFE)
val BrandSurfaceVariant = Color(0xFFE7E0EC)

val BrandPrimaryDark = Color(0xFFD0BCFF)
val BrandSecondaryDark = Color(0xFFCCC2DC)
val BrandTertiaryDark = Color(0xFFEFB8C8)
val BrandPrimaryContainerDark = Color(0xFF4F378B)
val BrandSurfaceDark = Color(0xFF1C1B1F)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    tertiary = BrandTertiary,
    primaryContainer = BrandPrimaryContainer,
    surface = BrandSurface,
    surfaceVariant = BrandSurfaceVariant,
    onPrimary = Color.White,
    onPrimaryContainer = Color(0xFF21005D),
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    secondary = BrandSecondaryDark,
    tertiary = BrandTertiaryDark,
    primaryContainer = BrandPrimaryContainerDark,
    surface = BrandSurfaceDark,
    onPrimary = Color(0xFF381E72),
    onPrimaryContainer = Color(0xFFEADDFF),
)

@Composable
fun StickerItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic colour (Material You) on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StickerItTypography,
        shapes = StickerItShapes,
        content = content,
    )
}
