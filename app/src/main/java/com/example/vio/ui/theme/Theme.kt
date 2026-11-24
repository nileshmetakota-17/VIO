package com.example.vio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A2108),
    secondary = Moss,
    onSecondary = Color(0xFF13210F),
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    surfaceVariant = InkHigh,
    onSurfaceVariant = Mute,
    error = Danger,
    onError = Color(0xFF2A0E0C),
    outline = Line,
)

private val Corners = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun VIOTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Typography,
        shapes = Corners,
        content = content,
    )
}
