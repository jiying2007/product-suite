package com.junchen.jingdu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TtsPlaybackServiceSmokeTest {
    @Test fun realPlaybackBridgeStartsServiceWithoutProcessCrashAndPublishesState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val card = ReaderInstrumentationFixture.book(context)
        val repository = BookRepository(context)
        val book = repository.list().first { it.id == card.id }
        val source = repository.normalizedFile(book)
        val observed = AtomicReference<TtsPlaybackBroadcastState?>()
        val stateArrived = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = TtsPlaybackBridge(this@TtsPlaybackServiceSmokeTest.context()).parseState(intent) ?: return
                observed.set(state)
                stateArrived.countDown()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(TtsPlaybackService.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            val started = TtsPlaybackBridge(context).start(
                source = source,
                bookId = book.id,
                title = "TTS smoke",
                offset = 0L,
                settings = ReaderSettings(),
            )
            assertTrue("TTS MediaSessionService dispatch should be accepted", started)
            assertTrue(
                "TTS service must publish state instead of crashing during engine/session startup",
                stateArrived.await(10, TimeUnit.SECONDS),
            )
            val state = observed.get() ?: error("TTS state missing after service startup")
            assertTrue("published TTS offset must stay in source coordinates", state.offset >= 0L)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { context.stopService(Intent(context, TtsPlaybackService::class.java)) }
        }
    }

    private fun context(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext
}
