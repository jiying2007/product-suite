package com.junchen.jingdu

import android.content.Intent
import android.net.Uri
import android.os.Build

internal sealed interface IncomingImport {
    data class UriSource(val uri: Uri) : IncomingImport
    data class SharedText(val text: String, val displayName: String) : IncomingImport
}

/**
 * Accepts Android VIEW/SEND contracts without treating arbitrary Intent extras as trusted metadata.
 * Shared text is bounded before it reaches private immutable import storage.
 */
internal object IncomingImportParser {
    const val MAX_SHARED_TEXT_CHARS = 512 * 1024

    fun parse(intent: Intent?): IncomingImport? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data?.let { IncomingImport.UriSource(it) }
        Intent.ACTION_SEND -> {
            incomingStream(intent)?.let { IncomingImport.UriSource(it) }
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                    ?.toString()
                    ?.take(MAX_SHARED_TEXT_CHARS)
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        IncomingImport.SharedText(
                            text = it,
                            displayName = TextMetadataSanitizer.displayName(
                                intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString(),
                                fallback = "TXT",
                            ),
                        )
                    }
        }
        else -> null
    }

    private fun incomingStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
