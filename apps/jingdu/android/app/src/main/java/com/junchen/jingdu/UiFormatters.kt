package com.junchen.jingdu

import androidx.compose.ui.graphics.Color
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

internal fun stripTxt(name: String): String = if (name.lowercase(Locale.ROOT).endsWith(".txt")) name.dropLast(4) else name

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun formatTouched(value: Long, unread: String = "—"): String {
    if (value <= 0) return unread
    return DateFormat.getDateInstance(DateFormat.SHORT).format(Date(value))
}

internal fun coverColor(id: String): Color {
    val hash = id.take(8).toLongOrNull(16) ?: id.hashCode().toLong()
    val palette = listOf(Color(0xFF4E6E5D), Color(0xFF665C80), Color(0xFF7B5948), Color(0xFF3E6575), Color(0xFF6D6044), Color(0xFF76556A))
    return palette[(hash.absoluteValue % palette.size).toInt()]
}
