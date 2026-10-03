package com.junchen.jingdu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val bridge = TtsPlaybackBridge(context)
        val observed = AtomicReference<TtsPlaybackBroadcastState?>()
        val stateArrived = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = bridge.parseState(intent) ?: return
                observed.set(state)
                if (state.reason?.startsWith("tts error") == true ||
                    (state.active && state.offset >= 0L && state.nextOffset > state.offset)
                ) {
                    stateArrived.countDown()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(TtsPlaybackService.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            val started = bridge.start(
                source = source,
                bookId = book.id,
                title = "TTS smoke",
                offset = 0L,
                settings = ReaderSettings(),
            )
            assertTrue("TTS MediaSessionService dispatch should be accepted", started)
            assertTrue(
                "TTS service must queue real source text instead of crashing or terminating with an engine error",
                stateArrived.await(15, TimeUnit.SECONDS),
            )
            val state = observed.get() ?: error("TTS state missing after service startup")
            assertFalse("TTS smoke must not terminate with a system engine error", state.reason?.startsWith("tts error") == true)
            assertTrue("published TTS offset must stay in source coordinates", state.offset >= 0L)
            assertTrue("TTS smoke must publish a real queued next source offset", state.nextOffset > state.offset)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { context.stopService(Intent(context, TtsPlaybackService::class.java)) }
        }
    }

    @Test fun missingSavedEngineFallsBackToSystemDefaultAndQueuesSpeech() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val card = ReaderInstrumentationFixture.book(context)
        val repository = BookRepository(context)
        val book = repository.list().first { it.id == card.id }
        val source = repository.normalizedFile(book)
        val bridge = TtsPlaybackBridge(context)
        val store = TtsEngineStore(context)
        val previousEngine = store.load()
        val observed = AtomicReference<TtsPlaybackBroadcastState?>()
        val stateArrived = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = bridge.parseState(intent) ?: return
                observed.set(state)
                if (state.reason?.startsWith("tts error") == true ||
                    (state.active && state.offset >= 0L && state.nextOffset > state.offset)
                ) {
                    stateArrived.countDown()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(TtsPlaybackService.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.stopService(Intent(context, TtsPlaybackService::class.java))
            store.save("com.junchen.jingdu.missing.tts.engine")
            val started = bridge.start(
                source = source,
                bookId = book.id,
                title = "TTS fallback smoke",
                offset = 0L,
                settings = ReaderSettings(),
            )
            assertTrue("fallback smoke service dispatch should be accepted", started)
            assertTrue(
                "a missing saved TTS engine must fall back and queue speech",
                stateArrived.await(18, TimeUnit.SECONDS),
            )
            val state = observed.get() ?: error("TTS fallback state missing")
            assertFalse("fallback must not expose a terminal engine error", state.reason?.startsWith("tts error") == true)
            assertTrue("fallback must queue a real source chunk", state.nextOffset > state.offset)
            assertEquals("invalid saved engine must be cleared after fallback", "", store.load())
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { context.stopService(Intent(context, TtsPlaybackService::class.java)) }
            store.save(previousEngine)
        }
    }

}
