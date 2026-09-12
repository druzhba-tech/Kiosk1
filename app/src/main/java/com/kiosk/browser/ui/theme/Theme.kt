package com.kiosk.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonGreen,
    tertiary = NeonOrange,
    background = CyberBlack,
    surface = CyberSurface,
    surfaceVariant = CyberCard,
    onPrimary = CyberBlack,
    onSecondary = CyberBlack,
    onBackground = TextWhite,
    onSurface = TextWhite,
    error = NeonRed
)

@Composable
fun KioskBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
