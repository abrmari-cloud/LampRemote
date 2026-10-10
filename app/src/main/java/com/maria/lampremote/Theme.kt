package com.maria.lampremote

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Twilight room + warm lamp light. One accent only: the warm light the app exists to produce.
val Twilight = Color(0xFF171A24)
val Panel = Color(0xFF212533)
val PanelHigh = Color(0xFF2C3143)
val Hairline = Color(0xFF363B4E)
val Amber = Color(0xFFFFB357)
val AmberTint = Color(0xFF3A2E20)
val OnAmber = Color(0xFF2B1B06)
val Ink = Color(0xFFEEE8DC)
val Muted = Color(0xFF9095A6)
val Coral = Color(0xFFFF8A7A)

private val LampColors = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = AmberTint,
    onPrimaryContainer = Amber,
    secondaryContainer = AmberTint,
    onSecondaryContainer = Amber,
    background = Twilight,
    onBackground = Ink,
    surface = Twilight,
    onSurface = Ink,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Twilight,
    surfaceContainerLow = Panel,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = PanelHigh,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Coral,
    onError = Color(0xFF3B0C05),
)

@Composable
fun LampTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LampColors, content = content)
}
