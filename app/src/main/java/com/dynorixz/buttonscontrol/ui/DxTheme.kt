package com.dynorixz.buttonscontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

val DxDark = Color(0xFF080A12)
val DxSurface = Color(0xFF101426)
val DxPurple = Color(0xFF8A68FF)
val DxBlue = Color(0xFF4F7CFF)
val DxLavender = Color(0xFFB9AEFF)
val DxText = Color(0xFFF5F3FF)
@Composable fun DxTheme(content: @Composable () -> Unit) {
    val brand = ColorScheme(primary = DxPurple, onPrimary = DxText, primaryContainer = Color(0xFF302553),
        onPrimaryContainer = DxLavender, secondary = DxBlue, onSecondary = DxText, secondaryContainer = Color(0xFF192B56),
        onSecondaryContainer = DxText, background = DxDark, onBackground = DxText,
        surfaceContainer = DxSurface, onSurface = DxText, onSurfaceVariant = DxLavender)
    MaterialTheme(colorScheme = dynamicColorScheme(LocalContext.current) ?: brand, content = content)
}
