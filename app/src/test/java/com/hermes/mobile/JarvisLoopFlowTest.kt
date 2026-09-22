package com.hermes.mobile

import com.hermes.mobile.data.JarvisLoopController
import com.hermes.mobile.data.JarvisLoopLogic
import com.hermes.mobile.data.JarvisLoopLogic.Phase
import com.hermes.mobile.data.PlayerPort
import com.hermes.mobile.data.RecorderPort
import com.hermes.mobile.data.VoiceApiException
import com.hermes.mobile.data.VoiceHealth
import com.hermes.mobile.data.VoiceSpeakLogic
import com.hermes.mobile.data.VoiceTransport
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
 * JARVIS-2 (tur24) — [JarvisLoopController] akış sözleşmesi (JVM, sahte portlar,
 *手动 VAD tick'i + sahte saat — geçişler deterministik ilerler):
 *
 *  1. Tek tur: kayıt → VAD kesmesi → STT → Gönder → yanıt → sentez+çalma →
 *     çalma bitince OTOMATİK yeniden dinleme (görev 1'in kapı bekçisi).
 *  2. "kapat" komutu: STT sonrası GÖNDERİLMEZ, döngü Off'a döner.
 *  3. Boş/ağ STT hatası 2'de tolere, 3'te kibar kapatma (sonsuz döngü yok).
 *  4. Motor düşüş sırası: tercih kapalıysa /health'den ilk açık motor;
 *     sentez hatasında zincirde ilerleme.
 *  5. 60 sn sözleşme tavanı: konuşma sürse bile tur kesilir.
 *  6. Çalma sırasında kullanıcı kapatırsa player durdurulur (yarım ses yok).
 */
class JarvisLoopFlowTest {

    private class FakeRec(private var last: File? = null) : RecorderPort {
        val started = AtomicInteger()
        @Volatile var amp = 0
        @Volatile var cancelled = 0
        override fun start(target: File): Boolean {
            started.incrementAndGet()
            target.parentFile?.mkdirs()
            target.writeBytes(byteArrayOf(1, 2, 3))
            last = target
            return true
        }
        override fun stop(): RecorderPort.Recorded? =
            last?.let { RecorderPort.Recorded(it, "audio/mp4a-latm") }
        override fun cancel() { cancelled++ }
        override fun amplitude(): Int = amp
    }

    private class FakePlayer : PlayerPort {
        val plays = AtomicInteger()
        @Volatile var stoppedCount = 0
        @Volatile var pending: (() -> Unit)? = null
        override fun play(
            file: File,
            onDone: () -> Unit,
            onError: (String) -> Unit,
            frames: List<Float>?,
            onLevel: (Float) -> Unit,
        ): Boolean {
            plays.incrementAndGet()
            pending = onDone   // çağrı senkron: çalmayı sürücü bitirir
            return true
        }
        override fun stop() {
            stoppedCount++
            pending = null
        }
    }

    private class FakeCloud(
        var sttResult: String = "hava nasıl",
        @Volatile var sttFail: Boolean = false,
    ) : VoiceTransport {
        val sttCalls = AtomicInteger()
        val synthIds = java.util.Collections.synchronizedList(mutableListOf<String>())
        @Volatile var synthFailCount = 0
        override suspend fun health(): VoiceHealth = VoiceHealth(ok = true)
        override suspend fun transcribe(audio: ByteArray, fileName: String, mime: String): String {
            sttCalls.incrementAndGet()
            if (sttFail) throw VoiceApiException("şebeke kopuk")
            return sttResult
        }
        override suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine): ByteArray {
            synchronized(synthIds) {
                synthIds.add(engine.id)
                if (synthIds.size <= synthFailCount) throw VoiceApiException("motor çöktü")
            }
            return byteArrayOf(82, 73, 70, 70)
        }
        override val working: String? get() = "http://test:8174"
    }

    private lateinit var cache: File
    private lateinit var scope: CoroutineScope

    /** Sahte saat: her okumada +1000 ms (VAD eşikleri 1000'lik adımla çözülür). */
    private val clock = AtomicLong(1_000L)
    private fun tick(): Long = clock.getAndAdd(1_000L)

    @Before
    fun setUp() {
        cache = File(System.getProperty("java.io.tmpdir"), "t24-loop-${System.nanoTime()}")
            .apply { mkdirs() }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel()
        cache.deleteRecursively()
    }

    private fun controller(
        rec: RecorderPort,
        player: PlayerPort,
        cloud: VoiceTransport,
        engines: Map<String, String>,
        sent: MutableList<String>,
        notices: MutableList<String>,
    ) = JarvisLoopController(
        transport = { cloud },
        recorder = rec,
        player = player,
        cacheDir = { cache },
        scope = scope,
        healthEngines = { engines },
        now = { tick() },
        lang = { tr, _ -> tr },
        diag = {},
    ).apply {
        onSend = { synchronized(sent) { sent.add(it) } }
        onNotice = { synchronized(notices) { notices.add(it) } }
    }

    private fun waitUntil(timeoutMs: Long = 4_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return
            Thread.sleep(20)
        }
        assertTrue("zaman aşımı — beklenti karşılanmadı", cond())
    }

    /** Konuşma + sessizlik: 1 sesli + en fazla 3 sessiz tick, her biri +1000 ms
     *  → 1300 ms sessizlik VAD'ı keser (CutSilence). */
    private fun speakThenSilence(ctrl: JarvisLoopController, rec: FakeRec) {
        rec.amp = 5_000
        ctrl.tick()
        rec.amp = 0
        repeat(3) {
            val c = ctrl.tick()
            if (c != JarvisLoopLogic.VadCut.Continue) return
        }
    }

    private val allEngines = JarvisLoopLogic.LOOP_FALLBACK.associateWith { "acik" }

    // ── 1) Tam tur ───────────────────────────────────────────────────

    @Test
    fun `tur yanit oynatilinca otomatik dinlemeye doner`() {
        val rec = FakeRec()
        val player = FakePlayer()
        val cloud = FakeCloud()
        val sent = mutableListOf<String>()
        val ctrl = controller(rec, player, cloud, allEngines, sent, mutableListOf())

        ctrl.start(manualVad = true)
        assertEquals(Phase.Listening, ctrl.state.value.phase)

        speakThenSilence(ctrl, rec)
        waitUntil { ctrl.state.value.phase == Phase.WaitReply }
        assertEquals(1, cloud.sttCalls.get())
        waitUntil { synchronized(sent) { sent.size } == 1 }
        assertEquals(listOf("hava nasıl"), synchronized(sent) { sent.toList() })

        ctrl.onAgentReply("21 derece")
        waitUntil { player.plays.get() == 1 }
        waitUntil { ctrl.state.value.phase == Phase.Speaking }
        waitUntil { synchronized(cloud.synthIds) { cloud.synthIds.size } == 1 }
        assertEquals("chatterbox", cloud.synthIds[0])

        // Oynatma bitti → OTOMATİK dinleme + yeni kayıt (görev 1).
        player.pending?.invoke()
        waitUntil { ctrl.state.value.phase == Phase.Listening }
        waitUntil { rec.started.get() == 2 }
        ctrl.stop("test")
        assertEquals(Phase.Off, ctrl.state.value.phase)
    }

    // ── 2) Kapat komutu ──────────────────────────────────────────────

    @Test
    fun `kapat komutu donguyu kapatir gonderi YAPILMAZ`() {
        val cloud = FakeCloud(sttResult = "kapat")
        val sent = mutableListOf<String>()
        val rec = FakeRec()
        val ctrl = controller(rec, FakePlayer(), cloud, allEngines, sent, mutableListOf())
        ctrl.start(manualVad = true)
        speakThenSilence(ctrl, rec)
        waitUntil { ctrl.state.value.phase == Phase.Off }
        Thread.sleep(150)   // geciken gönderme fırsatı — olmamalı
        assertTrue("komut ajana gitmemeli", synchronized(sent) { sent.isEmpty() })
    }

    // ── 3) Hata politikası ───────────────────────────────────────────

    @Test
    fun `ucuncu stt hatasi donguyu kapatir`() {
        val cloud = FakeCloud(sttFail = true)
        val notices = mutableListOf<String>()
        val rec = FakeRec()
        val ctrl = controller(rec, FakePlayer(), cloud, allEngines, mutableListOf(), notices)
        ctrl.start(manualVad = true)

        repeat(2) {
            speakThenSilence(ctrl, rec)
            waitUntil { ctrl.state.value.failures == it + 1 }
            waitUntil { ctrl.state.value.phase == Phase.Listening }
        }
        speakThenSilence(ctrl, rec)
        waitUntil { ctrl.state.value.phase == Phase.Off }
        waitUntil { synchronized(notices) { notices.isNotEmpty() } }
    }

    @Test
    fun `bos stt de hata sayilir`() {
        val cloud = FakeCloud(sttResult = "   ")
        val rec = FakeRec()
        val ctrl = controller(rec, FakePlayer(), cloud, allEngines, mutableListOf(), mutableListOf())
        ctrl.start(manualVad = true)
        speakThenSilence(ctrl, rec)
        waitUntil { ctrl.state.value.failures == 1 }
        // Döngüde kaldı (1 hata tolerans), 60sn'de değil — 1 hatayOff değil
        assertEquals(Phase.Listening, ctrl.state.value.phase)
        ctrl.stop("test")
    }

    // ── 4) Motor düşüşü ──────────────────────────────────────────────

    @Test
    fun `kapali tercih ilk acik motorla acilir`() {
        val cloud = FakeCloud()
        val engines = mapOf(
            "chatterbox" to "kapalı", "kadin" to "acik",
            "pocket-kadin" to "acik", "pocket-erkek" to "acik",
        )
        val rec = FakeRec()
        val ctrl = controller(rec, FakePlayer(), cloud, engines, mutableListOf(), mutableListOf())
        ctrl.start(manualVad = true)
        speakThenSilence(ctrl, rec)
        waitUntil { ctrl.state.value.phase == Phase.WaitReply }
        ctrl.onAgentReply("yanıt")
        waitUntil { synchronized(cloud.synthIds) { cloud.synthIds.isNotEmpty() } }
        assertEquals("kadin", cloud.synthIds[0])
        ctrl.stop("test")
    }

    @Test
    fun `sentez hatasinda zincirde ilerlenir`() {
        val cloud = FakeCloud().apply { synthFailCount = 2 }   // ilk iki deneme patlar
        val rec = FakeRec()
        val ctrl = controller(rec, FakePlayer(), cloud, allEngines, mutableListOf(), mutableListOf())
        ctrl.start(manualVad = true)
        speakThenSilence(ctrl, rec)
        waitUntil { ctrl.state.value.phase == Phase.WaitReply }
        ctrl.onAgentReply("yanıt")
        waitUntil { synchronized(cloud.synthIds) { cloud.synthIds.size } >= 3 }
        val ids = synchronized(cloud.synthIds) { cloud.synthIds.toList() }
        assertEquals(listOf("chatterbox", "kadin", "pocket-kadin"), ids.take(3))
        ctrl.stop("test")
    }

    @Test
    fun `tum motorlar kapaliysa dongu kibar kapanir`() {
        val cloud = FakeCloud()
        val notices = mutableListOf<String>()
        val engines = JarvisLoopLogic.LOOP_FALLBACK.associateWith { "kapalı" }
        val rec = FakeRec()
        val ctrl = controller(rec, FakePlayer(), cloud, engines, mutableListOf(), notices)
        ctrl.start(manualVad = true)
        speakThenSilence(ctrl, rec)
        waitUntil { ctrl.state.value.phase == Phase.WaitReply }
        ctrl.onAgentReply("yanıt")
        waitUntil { ctrl.state.value.phase == Phase.Off }
        waitUntil { synchronized(notices) { notices.any { it.contains("Ses motoru") } } }
    }

    // ── 5) 60 sn tavanı ──────────────────────────────────────────────

    @Test
    fun `konusma surse de 60 sn tavani keser`() {
        val cloud = FakeCloud()
        val rec = FakeRec().apply { amp = 9_000 }
        val ctrl = controller(rec, FakePlayer(), cloud, allEngines, mutableListOf(), mutableListOf())
        ctrl.start(manualVad = true)
        // Saat 1000'er ms ilerliyor: 61 sesli örnek ≈ 61 sn — hiç kesmezse tavan keser.
        for (i in 0..64) {
            ctrl.tick()
            if (ctrl.state.value.phase != Phase.Listening) break
        }
        waitUntil { ctrl.state.value.phase == Phase.WaitReply }
        ctrl.stop("test")
    }

    // ── 6) Yarıda kapatma güvenlikleri ───────────────────────────────

    @Test
    fun `speak sirasinda kapatirsan player durur`() {
        val player = FakePlayer()
        val rec = FakeRec()
        val ctrl = controller(rec, player, FakeCloud(), allEngines, mutableListOf(), mutableListOf())
        ctrl.start(manualVad = true)
        speakThenSilence(ctrl, rec)
        waitUntil { ctrl.state.value.phase == Phase.WaitReply }
        ctrl.onAgentReply("yanıt")
        waitUntil { ctrl.state.value.phase == Phase.Speaking }
        ctrl.stop("kullanici")
        assertEquals(1, player.stoppedCount)
        assertEquals(Phase.Off, ctrl.state.value.phase)
    }

    @Test
    fun `stt sirasinda kapatirsan gonderi YAPILMAZ`() {
        // transcribe yavaşsa: STT uçağı havada while döngü Off'a düşerse gönderme
        // damarı kesilir — Off kontrolü sendeden ÖNCE gelir (kod sözleşmesi).
        val cloud = object : VoiceTransport {
            @Volatile var release = false
            val sentSeen = java.util.concurrent.CountDownLatch(1)
            override suspend fun health(): VoiceHealth = VoiceHealth(ok = true)
            override suspend fun transcribe(audio: ByteArray, fileName: String, mime: String): String {
                release = true
                while (!Thread.currentThread().isInterrupted) {
                    if (Thread.currentThread().isInterrupted) break
                    Thread.sleep(10)
                }
                return "gecikmiş metin"
            }
            override suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine): ByteArray =
                ByteArray(1)
            override val working: String? get() = null
        }
        val sent = mutableListOf<String>()
        val rec = FakeRec()
        val ctrl = controller(rec, FakePlayer(), cloud, allEngines, sent, mutableListOf())
        ctrl.start(manualVad = true)
        speakThenSilence(ctrl, rec)
        waitUntil(2_000) { cloud.release }
        ctrl.stop("kullanici")
        Thread.sleep(200)
        assertTrue("Off sonrası gönderme olmamalı", synchronized(sent) { sent.isEmpty() })
        assertEquals(Phase.Off, ctrl.state.value.phase)
    }

    @Test
    fun `toggle acip kapatir`() {
        val cloud = FakeCloud()
        val ctrl = controller(FakeRec(), FakePlayer(), cloud, allEngines, mutableListOf(), mutableListOf())
        ctrl.toggle()
        // manual VAD'sız start() gerçek zamanlı tick kullanır — yalnız Off'a dönüşü test et:
        waitUntil { ctrl.state.value.phase == Phase.Listening }
        ctrl.toggle()
        assertEquals(Phase.Off, ctrl.state.value.phase)
    }

    @Test
    fun `waitReply bos yanit kibar notice uretir dongu kalir`() {
        val notices = mutableListOf<String>()
        val rec = FakeRec()
        val ctrl = controller(rec, FakePlayer(), FakeCloud(), allEngines, mutableListOf(), notices)
        ctrl.start(manualVad = true)
        speakThenSilence(ctrl, rec)
        waitUntil { ctrl.state.value.phase == Phase.WaitReply }
        val before = ctrl.state.value.failures
        ctrl.onAgentReply("")
        waitUntil { synchronized(notices) { notices.any { it.contains("boş") } } }
        assertEquals(Phase.WaitReply, ctrl.state.value.phase)   // tur iptal, döngü sürüyor
        assertEquals(before, ctrl.state.value.failures)
        ctrl.stop("test")
    }

    @Test
    fun `beklenmeyen fazda yanit yok sayilir`() {
        val ctrl = controller(FakeRec(), FakePlayer(), FakeCloud(), allEngines, mutableListOf(), mutableListOf())
        val before = ctrl.state.value.phase
        ctrl.onAgentReply("kimse sormadı")
        assertEquals(before, ctrl.state.value.phase)
    }
}
