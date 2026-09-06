package com.junchen.jingdu

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class ImportPreview(
    val uri: Uri,
    val name: String,
    val encoding: String,
    val text: String,
    val sampledBytes: Int,
)

/**
 * First-readable stage for a new TXT. Only a bounded prefix is read and decoded; full private-copy
 * import/normalization continues independently afterwards. This stage never writes the source.
 */
internal class ProgressiveImport(context: Context) {
    private val app = context.applicationContext

    fun prepare(uri: Uri): ImportPreview {
        val bytes = ByteArray(MAX_PREVIEW_BYTES)
        var total = 0
        BufferedInputStream(app.contentResolver.openInputStream(uri) ?: error("cannot open selected file")).use { input ->
            while (total < bytes.size) {
                val count = input.read(bytes, total, bytes.size - total)
                if (count < 0) break
                total += count
            }
        }
        val sample = if (total == bytes.size) bytes else bytes.copyOf(total)
        val encoding = NativeCore.detectEncoding(sample, total == MAX_PREVIEW_BYTES)
        val charset = Charset.forName(encoding)
        val decoded = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(ByteBuffer.wrap(sample))
            .toString()
            .removePrefix("\uFEFF")
        val preview = codePointPrefix(decoded, MAX_PREVIEW_CHARS)
        return ImportPreview(uri, displayName(uri), encoding, preview, total)
    }

    private fun displayName(uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0) ?: "book.txt" else uri.lastPathSegment ?: "book.txt"
        } catch (_: Throwable) {
            uri.lastPathSegment ?: "book.txt"
        } finally {
            cursor?.close()
        }
    }

    private fun codePointPrefix(text: String, maximum: Int): String {
        if (text.codePointCount(0, text.length) <= maximum) return text
        val end = text.offsetByCodePoints(0, maximum)
        return text.substring(0, end)
    }

    companion object {
        const val MAX_PREVIEW_BYTES = 512 * 1024
        const val MAX_PREVIEW_CHARS = 12_000
    }
}
