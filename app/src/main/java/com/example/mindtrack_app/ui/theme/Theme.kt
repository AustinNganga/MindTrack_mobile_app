package com.example.mindtrack_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Color(0xFF331200),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECEBF5),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F9FD),
    surfaceContainer = Color(0xFFF2F1F9),
    surfaceContainerHigh = Color(0xFFECEBF5),
    outline = Color(0xFFC9C8D8),
    outlineVariant = Color(0xFFE3E2EE),
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo10,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo95,
    secondary = Teal80,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Color(0xFF522300),
    tertiaryContainer = Color(0xFF743500),
    onTertiaryContainer = Amber90,
    background = CanvasDark,
    onBackground = Color(0xFFE6E4F2),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E4F2),
    surfaceVariant = Color(0xFF2A2935),
    onSurfaceVariant = Color(0xFFC8C6D8),
    surfaceContainerLowest = Color(0xFF0C0C12),
    surfaceContainerLow = Color(0xFF17171F),
    surfaceContainer = Color(0xFF1F1E29),
    surfaceContainerHigh = Color(0xFF292834),
    outline = Color(0xFF6B6A7C),
    outlineVariant = Color(0xFF34333F),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun Mindtrack_appTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
