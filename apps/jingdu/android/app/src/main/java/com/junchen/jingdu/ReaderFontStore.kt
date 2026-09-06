package com.junchen.jingdu

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal class ReaderFontStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "reader-fonts").apply { mkdirs() }

    fun import(uri: Uri, context: Context): String {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_FONT_BYTES) error("font file too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("font unavailable")
        if (bytes.size < 1024) error("invalid font")
        val id = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(32)
        val target = File(root, "$id.font")
        if (!target.isFile) FileOutputStream(target).use { output -> output.write(bytes); output.fd.sync() }
        runCatching { Typeface.Builder(target).build() }.getOrElse {
            target.delete(); throw IllegalArgumentException("unsupported font", it)
        }
        prune(id)
        return id
    }

    fun file(id: String): File? = id.takeIf { it.matches(Regex("[0-9a-f]{32}")) }
        ?.let { File(root, "$it.font") }?.takeIf(File::isFile)

    fun delete(id: String) { file(id)?.delete() }

    private fun prune(keep: String) {
        root.listFiles().orEmpty().filter { it.isFile && it.name != "$keep.font" }
            .sortedByDescending(File::lastModified).drop(MAX_RETAINED_FONTS - 1).forEach(File::delete)
    }

    private companion object {
        const val MAX_FONT_BYTES = 16 * 1024 * 1024
        const val MAX_RETAINED_FONTS = 8
    }
}

@Composable
internal fun rememberReaderFontFamily(context: Context, settings: ReaderSettings): FontFamily {
    val id = settings.customFontId
    return remember(settings.typeface, id) {
        when (settings.typeface) {
            ReaderTypeface.SERIF -> FontFamily.Serif
            ReaderTypeface.MONOSPACE -> FontFamily.Monospace
            ReaderTypeface.CUSTOM -> ReaderFontStore(context).file(id)?.let { file ->
                runCatching { FontFamily(Font(file, FontWeight.Normal)) }.getOrNull()
            } ?: FontFamily.SansSerif
            ReaderTypeface.SYSTEM -> FontFamily.SansSerif
        }
    }
}
