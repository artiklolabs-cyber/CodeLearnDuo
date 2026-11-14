package com.codelearn.duo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Pal {
    val bg = Color(0xFF0D0F14)
    val card = Color(0xFF171A21)
    val elevated = Color(0xFF232730)
    val editorBg = Color(0xFF12151B)
    val green = Color(0xFF22C55E)
    val blue = Color(0xFF3B82F6)
    val amber = Color(0xFFF59E0B)
    val red = Color(0xFFEF4444)
    val purple = Color(0xFFA855F7)
    val text = Color(0xFFF3F4F6)
    val muted = Color(0xFF9CA3AF)
    val dim = Color(0xFF4B5563)
}

@Composable
fun CodeLearnTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Pal.green,
        onPrimary = Color.Black,
        secondary = Pal.blue,
        tertiary = Pal.purple,
        background = Pal.bg,
        onBackground = Pal.text,
        surface = Pal.card,
        onSurface = Pal.text,
        surfaceVariant = Pal.elevated,
        onSurfaceVariant = Pal.muted,
        outline = Pal.elevated,
        error = Pal.red
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
