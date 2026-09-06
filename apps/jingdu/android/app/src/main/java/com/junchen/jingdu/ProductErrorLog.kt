package com.junchen.jingdu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException

enum class ProductErrorCode {
    IMPORT_SOURCE_UNAVAILABLE,
    IMPORT_IO,
    REDECODE_IO,
    PRIVATE_PUBLISH_FAILED,
    UNSUPPORTED_ENCODING,
    BILLING_UNAVAILABLE,
    BILLING_LAUNCH_FAILED,
    BILLING_PRODUCT_QUERY_FAILED,
    BILLING_OWNERSHIP_QUERY_FAILED,
    BILLING_ACK_FAILED,
    BILLING_UPDATE_FAILED,
    INTERNAL_OPERATION_FAILED,
}

data class ProductErrorEvent(
    val code: ProductErrorCode,
    val operation: String,
    val timestampMs: Long,
)

/**
 * Bounded local diagnostic history. It deliberately stores only stable codes, operation names and
 * timestamps: never exception messages, paths, URIs, book names/text, search queries or purchase
 * tokens. The history leaves the device only when the user explicitly exports the privacy audit.
 */
internal class ProductErrorLog(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun record(code: ProductErrorCode, operation: String, timestampMs: Long = System.currentTimeMillis()) {
        val safeOperation = operation.filter { it.isLetterOrDigit() || it in "-_." }.take(48).ifBlank { "unknown" }
        val events = recent().toMutableList()
        events += ProductErrorEvent(code, safeOperation, timestampMs.coerceAtLeast(0L))
        val bounded = events.takeLast(MAX_EVENTS)
        val array = JSONArray()
        bounded.forEach { event ->
            array.put(
                JSONObject()
                    .put("code", event.code.name)
                    .put("operation", event.operation)
                    .put("timestampMs", event.timestampMs),
            )
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    @Synchronized
    fun recent(): List<ProductErrorEvent> = runCatching {
        val raw = preferences.getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val code = runCatching { ProductErrorCode.valueOf(item.optString("code")) }.getOrNull() ?: continue
                val operation = item.optString("operation").take(48)
                val timestamp = item.optLong("timestampMs").coerceAtLeast(0L)
                add(ProductErrorEvent(code, operation, timestamp))
            }
        }.takeLast(MAX_EVENTS)
    }.getOrDefault(emptyList())

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    companion object {
        const val MAX_EVENTS = 24
        private const val PREFS = "jingdu.product-errors.v1"
        private const val KEY = "events"
    }
}

internal object ProductErrorClassifier {
    fun importFailure(error: Throwable): ProductErrorCode = when (error) {
        is FileNotFoundException -> ProductErrorCode.IMPORT_SOURCE_UNAVAILABLE
        is IOException -> when {
            error.message == "private publish failed" -> ProductErrorCode.PRIVATE_PUBLISH_FAILED
            error.message?.startsWith("unsupported encoding:") == true -> ProductErrorCode.UNSUPPORTED_ENCODING
            else -> ProductErrorCode.IMPORT_IO
        }
        else -> ProductErrorCode.INTERNAL_OPERATION_FAILED
    }

    fun redecodeFailure(error: Throwable): ProductErrorCode = when (error) {
        is IOException -> when {
            error.message == "private publish failed" -> ProductErrorCode.PRIVATE_PUBLISH_FAILED
            error.message?.startsWith("unsupported encoding:") == true -> ProductErrorCode.UNSUPPORTED_ENCODING
            else -> ProductErrorCode.REDECODE_IO
        }
        else -> ProductErrorCode.INTERNAL_OPERATION_FAILED
    }
}
