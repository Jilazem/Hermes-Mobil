package com.hermes.mobile

import com.hermes.mobile.data.LocalSynthPort
import com.hermes.mobile.data.VoiceApiException
import com.hermes.mobile.data.VoiceMessageController
import com.hermes.mobile.data.VoiceSpeakLogic
import com.hermes.mobile.data.VoiceTransport
import com.hermes.mobile.data.VoiceHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Tur-21 — sahte yerel motorla akış sözleşmesi (saf Kotlin, JVM):
 *
 *  1. YEREL motor + hazır model → yerel port çağrılır, bulut taşıyıcıya
 *     HİÇ dokunulmaz (gizlilik: metin buluta gitmez), WAV önbellek adı.
 *  2. YEREL motor + model yok → açık hata; bulut denemesi YOK.
 *  3. Bulut motoru hata verir + model hazırsa → YEREL'e düşülür, bildirim
 *     ya da sessizlik davranışı raporlanır, dosya yerelden gelir.
 *  4. Yerel önbellek → ikinci çağrıda port again çağrılmaz.
 */
class LocalTtsFlowTest {

    private class FakeCloud : VoiceTransport {
        val calls = AtomicInteger()
        var fail: Exception? = null
        override suspend fun health(): VoiceHealth = VoiceHealth(ok = true)
        override suspend fun transcribe(audio: ByteArray, fileName: String, mime: String): String = ""
        override suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine): ByteArray {
            calls.incrementAndGet()
            fail?.let { throw it }
            return ByteArray(32) { (it % 7).toByte() }
        }
        override val working: String? get() = null
    }

    private class FakeLocal(
        var ready: Boolean = true,
        var loadOk: Boolean = true,
    ) : LocalSynthPort {
        val calls = AtomicInteger()
        var lastText: String? = null
        override fun isReady(): Boolean = ready
        override fun ensureLoaded(): Boolean = loadOk
        override fun synthesize(text: String, target: File) {
            calls.incrementAndGet()
            lastText = text
            target.parentFile?.mkdirs()
            // Sahte "RIFF....WAVE" başlığı — 16016 bayt: 44 + 16000/2000*...
            // Sözleşme gereği 44 bayt başlık + 16000 örnek int16 = 16044.
            require(target.absolutePath.endsWith(".wav")) { "yerel çıktı WAV olmalı: ${target.name}" }
            java.io.RandomAccessFile(target, "rw").use { raf ->
                raf.setLength(0)
                raf.write("RIFF".toByteArray())
                raf.writeInt(16_036)
                raf.write("WAVE".toByteArray())
                raf.write("fmt ".toByteArray())
                raf.writeInt(16)
                raf.writeShort(1)   // PCM
                raf.writeShort(1)   // mono
                raf.writeInt(22_050)
                raf.writeInt(44_100)
                raf.writeShort(2)
                raf.writeShort(16)
                raf.write("data".toByteArray())
                raf.writeInt(16_000)
                raf.write(ByteArray(16_000) { (it % 251).toByte() })
            }
        }
    }

    private lateinit var dir: File
    private lateinit var cache: File
    private lateinit var scope: CoroutineScope
    private val clock = AtomicLong(1_700_000_000_000)
    private val notices = mutableListOf<String>()

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "t21-flow-${System.nanoTime()}").apply { mkdirs() }
        cache = File(dir, "cache").apply { mkdirs() }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun controller(cloud: FakeCloud, local: FakeLocal) =
        VoiceMessageController(
            transport = { cloud },
            cacheDir = { cache },
            scope = scope,
            now = { clock.get() },
            lang = { tr, _ -> tr },
            localSynth = local,
        ).also { it.onNotice = { msg -> synchronized(notices) { notices += msg } } }

    private fun waitUntil(timeoutMs: Long = 4_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return
            Thread.sleep(20)
        }
        assertTrue("zaman aşımı", cond())
    }

    @Test
    fun `yerel motor — model hazirsa buluta hic gidilmez`() {
        val cloud = FakeCloud()
        val local = FakeLocal(ready = true)
        val c = controller(cloud, local)
        c.engine = VoiceSpeakLogic.Engine.YEREL
        c.speak("k1", "Merhaba dünya")
        waitUntil { local.calls.get() >= 1 }
        assertEquals("bulut taşıyıcısına dokunulmamalı", 0, cloud.calls.get())
        assertEquals("Merhaba dünya", local.lastText)
    }

    @Test
    fun `yerel motor — model yoksa ACIK hata, bulut denemesi yok`() {
        val cloud = FakeCloud()
        val local = FakeLocal(ready = false)
        val c = controller(cloud, local)
        c.engine = VoiceSpeakLogic.Engine.YEREL
        c.speak("k1", "gizli metin")
        waitUntil { notices.isNotEmpty() }
        assertEquals(0, cloud.calls.get())
        assertEquals(0, local.calls.get())
        assertTrue(notices.first().contains("Yerel ses indirilmemiş"))
    }

    @Test
    fun `bulut motoru hata + model hazir — yerele duser ve oynatir`() {
        val cloud = FakeCloud().apply { fail = VoiceApiException("502 bad gateway", 502, "/synthesize") }
        val local = FakeLocal(ready = true)
        val c = controller(cloud, local)
        c.engine = VoiceSpeakLogic.Engine.KAHYA
        c.speak("k1", "dususe dayanikli")
        waitUntil { local.calls.get() >= 1 }
        assertEquals(1, cloud.calls.get())
    }

    @Test
    fun `yerel onbellek — ikinci sonda port cagrisi yok`() {
        val cloud = FakeCloud()
        val local = FakeLocal(ready = true)
        val c = controller(cloud, local)
        c.engine = VoiceSpeakLogic.Engine.YEREL
        // Doğrudan ensureAudio — oyuncu bağımlılığı olmadan dosya sözleşmesi.
        val f1 = kotlinx.coroutines.runBlocking { c.ensureAudio("k1", "aynı metin") }
        val firstCalls = local.calls.get()
        val f2 = kotlinx.coroutines.runBlocking { c.ensureAudio("k1", "aynı metin") }
        assertEquals(f1.absolutePath, f2.absolutePath)
        assertEquals("önbellekten: port ikinci kez çağrılmamalı", firstCalls, local.calls.get())
        assertTrue(f1.name.contains("yerel"))
        assertTrue(f1.name.endsWith(".wav"))
        assertTrue(f1.length() >= 44)
    }

    @Test
    fun `yerel motor yuklenemezse hata metni acik`() {
        val cloud = FakeCloud()
        val local = FakeLocal(ready = true, loadOk = false)
        val c = controller(cloud, local)
        c.engine = VoiceSpeakLogic.Engine.YEREL
        c.speak("k1", "çalışmazsa")
        waitUntil { notices.isNotEmpty() }
        assertTrue(notices.first().contains("motoru açılamadı"))
    }
}
