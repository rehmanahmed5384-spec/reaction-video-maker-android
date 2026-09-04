package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StudioDarkColorScheme = darkColorScheme(
    primary = StudioPrimary,
    onPrimary = StudioOnPrimary,
    primaryContainer = StudioPrimaryContainer,
    onPrimaryContainer = StudioOnPrimaryContainer,
    secondary = StudioSecondary,
    onSecondary = StudioOnSecondary,
    secondaryContainer = StudioSecondaryContainer,
    onSecondaryContainer = StudioOnSecondaryContainer,
    tertiary = StudioTertiary,
    onTertiary = StudioOnTertiary,
    background = StudioBackground,
    onBackground = StudioOnBackground,
    surface = StudioSurface,
    onSurface = StudioOnSurface,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = StudioOnSurfaceVariant,
    outline = StudioOutline,
    error = StudioError
)

@Composable
fun ReactionStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) = ReactionStudioTheme(content = content)
