package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ViceColorScheme =
  darkColorScheme(
    primary = ViceMagenta,
    onPrimary = ViceTextPrimary,
    secondary = ViceCyan,
    onSecondary = ViceMidnight,
    tertiary = ViceGold,
    onTertiary = ViceMidnight,
    background = ViceMidnight,
    onBackground = ViceTextPrimary,
    surface = ViceSurface,
    onSurface = ViceTextPrimary,
    surfaceVariant = ViceSurfaceElevated,
    onSurfaceVariant = ViceTextMuted,
    error = ViceCrimson,
    onError = ViceTextPrimary,
  )

@Composable
fun ViceCoastTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = ViceColorScheme, typography = ViceTypography, content = content)
}
