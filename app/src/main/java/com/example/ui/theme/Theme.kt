package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SleekColorScheme =
  darkColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    tertiary = BlueDark,
    background = BlackBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceHighlight,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder,
  )

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = SleekColorScheme,
    typography = Typography,
    content = content
  )
}

