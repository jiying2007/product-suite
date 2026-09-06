package com.junchen.jingdu

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NativePageSizeSmokeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nativeCoreLoadsAndReadsOnCurrentRuntimePageSize() {
        val detected = NativeCore.detectEncoding("第一章\nHello🙂\n".toByteArray(Charsets.UTF_8), truncated = false)
        assertTrue(detected.isNotBlank())

        val file = File(context.cacheDir, "native-page-size-smoke.txt")
        try {
            file.writeText("第一章\nHello🙂\n", Charsets.UTF_8)
            val sha = NativeCore.fileSha256(file)
            assertEquals(64, sha.length)
            assertTrue(sha.all { it in '0'..'9' || it in 'a'..'f' })

            ReaderController(false).use { reader ->
                reader.open(file, 0)
                assertTrue(reader.length() > 0)
                assertTrue(reader.readAt(0, 64).contains("Hello"))
            }
        } finally {
            file.delete()
        }
    }
}
