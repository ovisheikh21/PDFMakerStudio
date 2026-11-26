package com.pdfmakerstudio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = StudioCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEE4E2),
    onPrimaryContainer = StudioCrimsonDark,
    secondary = StudioCobalt,
    onSecondary = Color.White,
    tertiary = StudioEmerald,
    background = SlateCanvas,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSecondary,
    outline = OutlineHairline,
    error = DangerRed,
    errorContainer = DangerRedContainer
)

@Composable
fun PDFMakerStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
