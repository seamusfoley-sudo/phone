package ie.spnvoip.phone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF1565C0)
val CallGreen = Color(0xFF2E9E5B)
val HangRed = Color(0xFFD93A3A)
val Amber = Color(0xFFE0A100)

private val Light = lightColorScheme(
    primary = Brand,
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFE9EEF5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    background = Color(0xFF0F1318),
    surface = Color(0xFF0F1318),
    surfaceVariant = Color(0xFF1E252E),
)

@Composable
fun SpnvoipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
