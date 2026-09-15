package com.hermes.mobile

import com.hermes.mobile.data.PlayerPort
import com.hermes.mobile.data.RecorderPort
import com.hermes.mobile.data.VoiceHealth
import com.hermes.mobile.data.VoiceMessageController
import com.hermes.mobile.data.VoiceRecordLogic
import com.hermes.mobile.data.VoiceSpeakLogic
import com.hermes.mobile.data.VoiceStatusLogic
import com.hermes.mobile.data.VoiceTransport
import com.hermes.mobile.data.VoiceApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Tur-11 — uçtan uca akış (Android'siz): **kayıt → metin** ve
 * **metin → ses → çalma**.
 *
 * Kayıt dosyası gerçek bir dosyadan gelir (emülatörde asset'ten yüklenen
 * `kayit.ogg`), taşıyıcı sahte: akış mantığı gerçek ağ olmadan sınanır.
 * Adres/HTTP katmanı `VoiceApiClientTest` içinde gerçek soketle koşar.
 */
class VoiceMessageFlowTest {

    private class FakeTransport : VoiceTransport {
        var transcribeCalls = 0
        var synthCalls = 0
        var lastFileName: String? = null
        var lastMime: String? = null
        var lastTranscribed: String? = null
        var lastEngine: VoiceSpeakLogic.Engine? = null
        var lastSynthesized: String? = null
        var fail: Exception? = null
        /** Doluysa sentez bu kapı açılana kadar bekler (ısıtma sürerken test). */
        var synthGate: CompletableDeferred<Unit>? = null
        var healthResult: VoiceHealth =
            VoiceHealth(ok = true, stt = "acik", engines = mapOf("kahya" to "acik"))
        var healthFail: Exception? = null

        override suspend fun health(): VoiceHealth {
            healthFail?.let { throw it }
            return healthResult
        }

        override suspend fun transcribe(audio: ByteArray, fileName: String, mime: String): String {
            transcribeCalls++
            lastFileName = fileName
            lastMime = mime
            lastTranscribed = audio.size.toString()
            fail?.let { throw it }
            return "bu bir sesli mesaj denemesidir"
        }

        override suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine): ByteArray {
            synthCalls++
            lastEngine = engine
            lastSynthesized = text
            synthGate?.await()
            fail?.let { throw it }
            return ByteArray(64) { 7 }
        }

        override val working: String = "http://192.168.1.101:8174"
    }

    private class FakeRecorder : RecorderPort {
        var started = 0
        var stopped = 0
        var cancelled = 0
        var target: File? = null

        override fun start(target: File): Boolean {
            started++
            this.target = target
            target.writeBytes(ByteArray(32) { 3 })
            return true
        }

        override fun stop(): RecorderPort.Recorded? {
            stopped++
            val f = target ?: return null
            return RecorderPort.Recorded(f, "audio/ogg")
        }

        override fun cancel() {
            cancelled++
        }
    }

    private class FakePlayer : PlayerPort {
        var plays = 0
        var stops = 0
        var lastFile: File? = null
        var onDone: (() -> Unit)? = null
        var onError: ((String) -> Unit)? = null

        override fun play(file: File, onDone: () -> Unit, onError: (String) -> Unit): Boolean {
            plays++
            lastFile = file
            this.onDone = onDone
            this.onError = onError
            return true
        }

        override fun stop() {
            stops++
        }
    }

    private lateinit var dir: File
    private lateinit var cache: File
    private lateinit var scope: CoroutineScope
    private val clock = AtomicLong(1_700_000_000_000)
    private val transport = FakeTransport()
    private val recorder = FakeRecorder()
    private val player = FakePlayer()

    private val notices = mutableListOf<String>()
    private val transcripts = mutableListOf<String>()

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "tur11-${System.nanoTime()}").apply { mkdirs() }
        cache = File(dir, "cache").apply { mkdirs() }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun controller(transportProvider: () -> VoiceTransport? = { transport }) =
        VoiceMessageController(
            transport = transportProvider,
            cacheDir = { cache },
            scope = scope,
            recorder = recorder,
            player = player,
            now = { clock.get() },
            lang = { tr, _ -> tr },
        ).also {
            it.onNotice = { msg -> notices += msg }
            it.onTranscript = { text -> transcripts += text }
        }

    private fun waitUntil(timeoutMs: Long = 4_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return
            Thread.sleep(20)
        }
        assertTrue("zaman aşımı: beklenen durum oluşmadı", cond())
    }

    // ── Kayıt → metin ─────────────────────────────────────────────────

    @Test
    fun `bas-konus kayit baslatir ve birakinca metne cevirir`() {
        val c = controller()
        c.holdStart()
        assertTrue(c.state.value.record.recording)
        assertEquals(1, recorder.started)

        clock.addAndGet(3_000)
        c.holdRelease()

        waitUntil { transcripts.isNotEmpty() }
        assertEquals(1, transport.transcribeCalls)
        assertEquals("bu bir sesli mesaj denemesidir", transcripts.first())
        assertEquals("audio/ogg", transport.lastMime)
        assertTrue(transport.lastFileName!!.endsWith(".ogg"))
        assertEquals(VoiceRecordLogic.Phase.Idle, c.state.value.record.phase)
        assertEquals(1, recorder.stopped)
    }

    @Test
    fun `kisa basis yukleme uretmez`() {
        val c = controller()
        c.holdStart()
        clock.addAndGet(300)
        c.holdRelease()
        Thread.sleep(200)
        assertEquals(0, transport.transcribeCalls)
        assertEquals(1, recorder.cancelled)
        assertEquals(VoiceRecordLogic.Phase.Idle, c.state.value.record.phase)
    }

    @Test
    fun `60 sn tavani dolunca kayit kendiliginden yuklenir`() {
        val c = controller()
        c.holdStart()
        assertTrue(c.state.value.record.recording)
        // Parmak basılı: saat 60 sn'yi aştı → ilk tik kaydı kapatıp yüklemeli.
        clock.addAndGet(61_000)
        waitUntil { transport.transcribeCalls == 1 }
        assertEquals(1, recorder.stopped)
        assertEquals("bu bir sesli mesaj denemesidir", transcripts.first())
    }

    @Test
    fun `kayit dosyasi olusmazsa hata bildirilir`() {
        val empty = object : RecorderPort {
            override fun start(target: File) = true
            override fun stop(): RecorderPort.Recorded? = null
            override fun cancel() = Unit
        }
        val c = VoiceMessageController(
            transport = { transport },
            cacheDir = { cache },
            scope = scope,
            recorder = empty,
            player = player,
            now = { clock.get() },
            lang = { tr, _ -> tr },
        ).also { it.onNotice = { msg -> notices += msg } }
        c.holdStart()
        clock.addAndGet(2_000)
        c.holdRelease()
        waitUntil { notices.isNotEmpty() }
        assertTrue(notices.first().contains("Kayıt dosyası"))
        assertEquals(VoiceRecordLogic.Phase.Failed, c.state.value.record.phase)
    }

    @Test
    fun `mikrofon acilmazsa kayit hata verir`() {
        val broken = object : RecorderPort {
            override fun start(target: File) = false
            override fun stop(): RecorderPort.Recorded? = null
            override fun cancel() = Unit
        }
        val c = VoiceMessageController(
            transport = { transport },
            cacheDir = { cache },
            scope = scope,
            recorder = broken,
            player = player,
            now = { clock.get() },
            lang = { tr, _ -> tr },
        ).also { it.onNotice = { msg -> notices += msg } }
        c.holdStart()
        assertTrue(notices.first().contains("Mikrofon"))
        assertEquals(VoiceRecordLogic.Phase.Failed, c.state.value.record.phase)
    }

    @Test
    fun `yukleme hatasi sohbete bildirilir`() {
        val c = controller()
        transport.fail = VoiceApiException("Ses ucu isteği reddetti (HTTP 403)", 403, "/transcribe")
        c.holdStart()
        clock.addAndGet(2_000)
        c.holdRelease()
        waitUntil { notices.isNotEmpty() }
        assertTrue(notices.first().contains("403"))
        assertEquals(VoiceRecordLogic.Phase.Failed, c.state.value.record.phase)
    }

    @Test
    fun `asset kaydi dogrudan metne cevrilir`() = runBlocking {
        val c = controller()
        val asset = File(dir, "kayit.ogg").apply { writeBytes(ByteArray(1_024) { 1 }) }
        val text = c.transcribeFile(asset.readBytes(), "kayit.ogg", "audio/ogg")
        assertEquals("bu bir sesli mesaj denemesidir", text)
        assertEquals("1024", transport.lastTranscribed)
    }

    @Test
    fun `sunucu yoksa metne cevirmede acik hata`() = runBlocking {
        val c = controller(transportProvider = { null })
        val e = runCatching { c.transcribeFile(ByteArray(8), "kayit.ogg") }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e!!.message!!.contains("Sunucu bağlı değil"))
    }

    @Test
    fun `calisan adres hatirlanir`() {
        val c = controller()
        var base: String? = null
        c.onWorkingBase = { base = it }
        c.holdStart()
        clock.addAndGet(2_000)
        c.holdRelease()
        waitUntil { transcripts.isNotEmpty() }
        assertEquals("http://192.168.1.101:8174", base)
    }

    // ── Metin → ses → çalma ───────────────────────────────────────────

    @Test
    fun `mesaji seslendirir ve onbellege yazar`() {
        val c = controller()
        c.speak("a-1", "**Merhaba** dünya")
        waitUntil { player.plays == 1 }
        assertEquals(1, transport.synthCalls)
        assertEquals("Merhaba dünya", transport.lastSynthesized)
        assertEquals(VoiceSpeakLogic.Engine.KAHYA, transport.lastEngine)
        assertTrue(player.lastFile!!.exists())
        assertTrue(player.lastFile!!.name.endsWith(".ogg"))
        assertEquals(VoiceSpeakLogic.Phase.Playing, c.state.value.speak.phase)
        assertEquals("a-1", c.state.value.speak.key)
    }

    @Test
    fun `ayarlanan motor sentezlenir`() {
        val c = controller()
        c.engine = VoiceSpeakLogic.Engine.KADIN
        c.speak("a-1", "kadın ses denemesi")
        waitUntil { player.plays == 1 }
        assertEquals(VoiceSpeakLogic.Engine.KADIN, transport.lastEngine)
        assertTrue(player.lastFile!!.name.contains("kadin"))
    }

    @Test
    fun `ayni balona ikinci dokunus durdurur`() {
        val c = controller()
        c.speak("a-1", "merhaba dünya")
        waitUntil { player.plays == 1 }
        c.speak("a-1", "merhaba dünya")
        assertEquals(1, player.stops)
        assertEquals(VoiceSpeakLogic.Phase.Idle, c.state.value.speak.phase)
    }

    @Test
    fun `ikinci calma indirme yapmaz - onbellekten gelir`() {
        val c = controller()
        c.speak("a-1", "aynı metin")
        waitUntil { player.plays == 1 }
        c.stopSpeaking()
        c.speak("a-1", "aynı metin")
        waitUntil { player.plays == 2 }
        assertEquals(1, transport.synthCalls)
    }

    @Test
    fun `indirme hatasi durum satirina ve bildirime duser`() {
        val c = controller()
        transport.fail = VoiceApiException("Ses ucuna ulaşılamıyor", -1, "/synthesize")
        c.speak("a-1", "merhaba")
        waitUntil { notices.isNotEmpty() }
        assertTrue(notices.first().contains("ulaşılamıyor"))
        assertEquals(VoiceSpeakLogic.Phase.Idle, c.state.value.speak.phase)
        assertEquals("Ses ucuna ulaşılamıyor", c.state.value.speak.message)
    }

    @Test
    fun `bos yanit hata sayilir`() {
        val empty = object : VoiceTransport {
            override suspend fun health() = VoiceHealth()
            override suspend fun transcribe(audio: ByteArray, fileName: String, mime: String) = ""
            override suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine) = ByteArray(0)
            override val working: String? = null
        }
        val c = controller(transportProvider = { empty })
        c.speak("a-1", "merhaba")
        waitUntil { notices.isNotEmpty() }
        assertTrue(notices.first().contains("boş yanıt"))
    }

    @Test
    fun `okunacak metin yoksa hic istek atilmaz`() {
        val c = controller()
        c.speak("a-1", "   \n  ")
        Thread.sleep(150)
        assertEquals(0, transport.synthCalls)
        assertTrue(notices.first().contains("Okunacak metin yok"))
    }

    @Test
    fun `calma bitince durum temizlenir`() {
        val c = controller()
        c.speak("a-1", "merhaba")
        waitUntil { player.plays == 1 }
        player.onDone?.invoke()
        assertEquals(VoiceSpeakLogic.Phase.Idle, c.state.value.speak.phase)
    }

    @Test
    fun `calma hatasi bildirilir`() {
        val c = controller()
        c.speak("a-1", "merhaba")
        waitUntil { player.plays == 1 }
        player.onError?.invoke("Ses çalınamadı (kod 1/0)")
        assertTrue(notices.last().contains("çalınamadı"))
        assertEquals(VoiceSpeakLogic.Phase.Idle, c.state.value.speak.phase)
    }

    @Test
    fun `sunucu yoksa seslendirme acik hata verir`() {
        val c = controller(transportProvider = { null })
        c.speak("a-1", "merhaba")
        waitUntil { notices.isNotEmpty() }
        assertTrue(notices.first().contains("Sunucu bağlı değil"))
    }

    @Test
    fun `selftest sentezi dosyaya yazar`() = runBlocking {
        val c = controller()
        val f = c.synthesizeToFile("öz test metni")
        assertTrue(f.exists())
        assertEquals(64L, f.length())
    }

    @Test
    fun `varsayilanlar sozlesmeye uygun`() {
        val c = controller()
        assertTrue(!c.autoSend)                                   // otomatik gönder KAPALI
        assertEquals(VoiceSpeakLogic.Engine.KAHYA, c.engine)       // varsayılan motor kahya
        assertEquals(VoiceRecordLogic.Phase.Idle, c.state.value.record.phase)
        assertNull(c.state.value.speak.message)
    }

    // ── Canlı durum + ısıtma (tur-12) ─────────────────────────────────

    @Test
    fun `probe health durumunu yapilandirilmis doner`() = runBlocking {
        transport.healthResult = VoiceHealth(
            ok = true,
            stt = "acik",
            engines = mapOf("kahya" to "kapali", "kadin" to "acik"),
        )
        val p = controller().probe()
        assertTrue(p.ok)
        assertEquals("http://192.168.1.101:8174", p.base)
        assertNull(p.error)
        val tr: (String, String) -> String = { tr, _ -> tr }
        assertEquals(
            "Metinleştirme: açık · Kahya: kapalı · Kadın: açık · Chatterbox: bilinmiyor",
            VoiceStatusLogic.line(VoiceStatusLogic.chips(p.health!!, tr), tr),
        )
    }

    @Test
    fun `probe hata firlatmaz hata satirini doldurur`() = runBlocking {
        transport.healthFail = VoiceApiException("ses ucuna ulaşılamıyor", -1, "/health")
        val p = controller().probe()
        assertFalse(p.ok)
        assertTrue(p.error!!.contains("ulaşılamıyor"))
        val p2 = controller(transportProvider = { null }).probe()
        assertFalse(p2.ok)
        assertTrue(p2.error!!.contains("Sunucu bağlı değil"))
    }

    @Test
    fun `isitma sabit cumleyi sentezler ve hazir olur`() {
        val c = controller()
        c.warmEngine(VoiceSpeakLogic.Engine.KADIN)
        assertTrue("ıstma hemen 'ısıtılıyor' durumuna geçmeli", c.warm.value.busy)
        waitUntil { c.warm.value.ready }
        assertEquals(1, transport.synthCalls)
        assertEquals(VoiceStatusLogic.WARM_SENTENCE, transport.lastSynthesized)
        assertEquals(VoiceSpeakLogic.Engine.KADIN, transport.lastEngine)
        assertEquals("kadin", c.warm.value.engineId)
        assertTrue(VoiceStatusLogic.warmReadyFor(c.warm.value, VoiceSpeakLogic.Engine.KADIN))
    }

    @Test
    fun `isitma surerken ikinci tik ikinci istek uretmez`() {
        val gate = CompletableDeferred<Unit>()
        transport.synthGate = gate
        val c = controller()
        c.warmEngine()
        waitUntil { transport.synthCalls == 1 }
        c.warmEngine()
        c.warmEngine()
        Thread.sleep(200)
        assertEquals("çift tık koruması: tek istek", 1, transport.synthCalls)
        assertTrue(c.warm.value.busy)
        gate.complete(Unit)
        waitUntil { c.warm.value.ready }
        assertEquals(1, transport.synthCalls)
        transport.synthGate = null
    }

    @Test
    fun `isitma hatasi duruma ve bildirime duser`() {
        transport.fail = VoiceApiException("motor yüklenemedi", 500, "/synthesize")
        val c = controller()
        c.warmEngine()
        waitUntil { c.warm.value.phase == VoiceStatusLogic.WarmPhase.Failed }
        assertTrue(c.warm.value.message!!.contains("motor yüklenemedi"))
        assertTrue(notices.any { it.contains("motor yüklenemedi") })
        // Hata sonrası yeniden denenebilir (düğme "Yeniden dene").
        transport.fail = null
        c.warmEngine()
        assertTrue(c.warm.value.busy)
        waitUntil { c.warm.value.ready }
    }

    @Test
    fun `motor degisince hazir isareti sifirlanir`() {
        val c = controller()
        c.warmEngine(VoiceSpeakLogic.Engine.KAHYA)
        waitUntil { c.warm.value.ready }
        c.resetWarm(VoiceSpeakLogic.Engine.KADIN)
        assertEquals(VoiceStatusLogic.WarmPhase.Idle, c.warm.value.phase)
        assertEquals("Isıt", VoiceStatusLogic.warmLabel(c.warm.value, VoiceSpeakLogic.Engine.KADIN) { tr, _ -> tr })
    }
}
