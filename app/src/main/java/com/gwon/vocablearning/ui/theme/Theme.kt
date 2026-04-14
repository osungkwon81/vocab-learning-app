package com.gwon.vocablearning.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Cream,
    secondary = Moss,
    onSecondary = Cream,
    tertiary = Clay,
    background = Cream,
    onBackground = Ink,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Ink,
    error = SoftRed,
)

private val DarkColors = darkColorScheme(
    primary = Mist,
    onPrimary = Ink,
    secondary = Clay,
    onSecondary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = ColorTokens.darkSurface,
    onSurface = Cream,
    surfaceVariant = Slate,
    onSurfaceVariant = Cream,
    error = SoftRed,
)

private object ColorTokens {
    val darkSurface = Ink.copy(alpha = 0.92f)
}

@Composable
fun VocabLearningTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}

