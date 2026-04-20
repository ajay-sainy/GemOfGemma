package com.gemofgemma.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = GemPrimaryLight,
    onPrimary = GemOnPrimaryLight,
    primaryContainer = GemPrimaryContainerLight,
    onPrimaryContainer = GemOnPrimaryContainerLight,
    secondary = GemSecondaryLight,
    onSecondary = GemOnSecondaryLight,
    secondaryContainer = GemSecondaryContainerLight,
    onSecondaryContainer = GemOnSecondaryContainerLight,
    tertiary = GemTertiaryLight,
    onTertiary = GemOnTertiaryLight,
    tertiaryContainer = GemTertiaryContainerLight,
    onTertiaryContainer = GemOnTertiaryContainerLight,
    background = GemBackgroundLight,
    onBackground = GemOnBackgroundLight,
    surface = GemSurfaceLight,
    onSurface = GemOnSurfaceLight,
    surfaceVariant = GemSurfaceVariantLight,
    onSurfaceVariant = GemOnSurfaceVariantLight,
    outline = GemOutlineLight,
    surfaceContainer = GemSurfaceContainerLight,
    surfaceContainerHigh = GemSurfaceContainerHighLight,
    error = GemErrorLight,
    onError = GemOnErrorLight
)

private val DarkColorScheme = darkColorScheme(
    primary = GemPrimaryDark,
    onPrimary = GemOnPrimaryDark,
    primaryContainer = GemPrimaryContainerDark,
    onPrimaryContainer = GemOnPrimaryContainerDark,
    secondary = GemSecondaryDark,
    onSecondary = GemOnSecondaryDark,
    secondaryContainer = GemSecondaryContainerDark,
    onSecondaryContainer = GemOnSecondaryContainerDark,
    tertiary = GemTertiaryDark,
    onTertiary = GemOnTertiaryDark,
    tertiaryContainer = GemTertiaryContainerDark,
    onTertiaryContainer = GemOnTertiaryContainerDark,
    background = GemBackgroundDark,
    onBackground = GemOnBackgroundDark,
    surface = GemSurfaceDark,
    onSurface = GemOnSurfaceDark,
    surfaceVariant = GemSurfaceVariantDark,
    onSurfaceVariant = GemOnSurfaceVariantDark,
    outline = GemOutlineDark,
    surfaceContainer = GemSurfaceContainerDark,
    surfaceContainerHigh = GemSurfaceContainerHighDark,
    error = GemErrorDark,
    onError = GemOnErrorDark
)

@Composable
fun GemOfGemmaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GemTypography,
        shapes = GemShapes,
        content = content
    )
}
