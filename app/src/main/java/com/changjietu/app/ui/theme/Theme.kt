package com.changjietu.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BluePrimary = Color(0xFF1565C0)
private val BlueSecondary = Color(0xFF0277BD)
private val Danger = Color(0xFFC62828)
private val Warning = Color(0xFFEF6C00)

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
    error = Danger,
    tertiary = Warning,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF81D4FA),
    error = Color(0xFFEF9A9A),
    tertiary = Color(0xFFFFB74D),
)

@Composable
fun ChangjietuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
