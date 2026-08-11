package com.example.applemaps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Apple Maps theme — colors come from the Compose [AppleColorScheme] generated from apl.css tokens
 * (AppleColors.kt), NOT XML. `LocalAppleColors` exposes the full token set to any composable.
 */
val LocalAppleColors = staticCompositionLocalOf { AppleLightColors }

@Composable
fun AppleMapsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) AppleDarkColors else AppleLightColors
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.borderBrandAccent,
        background = colors.fillTertiary,
        onBackground = colors.glyphDefault,
        surface = colors.fillPrimary,
        onSurface = colors.glyphDefault,
        surfaceVariant = colors.fillSecondary,
        onSurfaceVariant = colors.glyphMuted,
    )
    CompositionLocalProvider(LocalAppleColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
