package com.junchen.posestudio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF315BDB),
    secondary = Color(0xFF52637D),
    background = Color(0xFFF7F7F5),
    surface = Color(0xFFFFFFFF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    secondary = Color(0xFFBAC6E1),
    background = Color(0xFF111318),
    surface = Color(0xFF191B20),
)

@Composable
fun PoseStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
