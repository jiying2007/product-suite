package com.junchen.jingdu

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProductDiagnosticsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun errorLogIsBoundedAndSanitizesOperationNames() {
        val log = ProductErrorLog(context)
        log.clear()
        repeat(ProductErrorLog.MAX_EVENTS + 7) { index ->
            log.record(ProductErrorCode.IMPORT_IO, "book/import secret:$index", timestampMs = index.toLong())
        }
        val events = log.recent()
        assertEquals(ProductErrorLog.MAX_EVENTS, events.size)
        assertEquals(7L, events.first().timestampMs)
        assertTrue(events.all { it.operation == "bookimportsecret" || it.operation.startsWith("bookimportsecret") })
        assertTrue(events.all { '/' !in it.operation && ':' !in it.operation })
        log.clear()
    }

    @Test
    fun privacyAuditExportContainsCodesButNoBookTextFields() {
        val result = PrivacyAuditResult(
            networkPermissionAbsent = true,
            automaticBackupDisabled = true,
            bookTextUploadCapability = false,
            analyticsSdkPresent = false,
            adsSdkPresent = false,
            folderRoots = 2,
            libraryBooks = 3,
            feedbackKeep = 1,
            feedbackDelete = 2,
            feedbackProtect = 3,
            localeTag = "en-US",
            usablePrivateStorageBytes = 1234,
            memoryClassMb = 256,
            recentErrors = listOf(ProductErrorEvent(ProductErrorCode.BILLING_UNAVAILABLE, "billing.connect", 42)),
        )
        val payload = JSONObject(PrivacyAudit.toJson(context, result))
        assertEquals(3, payload.getInt("schema"))
        assertTrue(payload.getBoolean("automaticBackupDisabled"))
        assertFalse(payload.getBoolean("containsBookText"))
        assertEquals("BILLING_UNAVAILABLE", payload.getJSONArray("recentErrors").getJSONObject(0).getString("code"))
        val policy = payload.getJSONObject("diagnosticPolicy")
        assertFalse(policy.getBoolean("containsPaths"))
        assertFalse(policy.getBoolean("containsUris"))
        assertFalse(policy.getBoolean("containsSearchQueries"))
        assertFalse(policy.getBoolean("containsPurchaseTokens"))
    }

    @Test
    fun repositoryFailureClassifierDistinguishesPublishAndEncoding() {
        assertEquals(
            ProductErrorCode.PRIVATE_PUBLISH_FAILED,
            ProductErrorClassifier.importFailure(IOException("private publish failed")),
        )
        assertEquals(
            ProductErrorCode.UNSUPPORTED_ENCODING,
            ProductErrorClassifier.importFailure(IOException("unsupported encoding: x-test")),
        )
        assertEquals(
            ProductErrorCode.REDECODE_IO,
            ProductErrorClassifier.redecodeFailure(IOException("read failed")),
        )
    }
}
