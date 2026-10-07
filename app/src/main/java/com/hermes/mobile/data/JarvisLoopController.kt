package com.hermes.mobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * JARVIS-2 (tur24) — sürekli sesli sohbet döngüsünün yürütücüsü.
 *
 * [VoiceMessageController]'ın basılı-tut akışından bilinçli olarak AYRI:
 * orada parmak kalkınca kayıt biter; burada parmak kalkmaz — sessizlik
 * keser ([JarvisLoopLogic] saf kararı), yanıt okunur, oynatma bitince
 * kendiliğinden dinlemeye dönülür. İki akış aynı mikrofonu paylaşamaz;
 * döngü açıkken bas-konuş girdisi ViewModel tarafından yutulur.
 *
 * Android'e bağlı DEĞİL: kayıt [RecorderPort] (genlik dahil), sentez+çalma
 * dışarıdan enjekte edilen [speakText] kancası, taşıyıcı [transport] ve
 * `/health` motor haritası [healthEngines] arayüzden gelir — akışın tamamı
 * JVM testinde sahte portlarla koşar (`JarvisLoopFlowTest`).
 *
 * Güvenlik (görev maddesi 4): kayıt YALNIZ [start] ile — o da kullanıcı
 * dokunuşundan çağrılır. Arkaplan kayıt yok, wake word yok. Döngü ne kadar
 * hata görürse görsün [JarvisLoopLogic.MAX_FAILURES] sonrası KENDİNİ
 * kapatır: sonsuz döngü yasak.
 */
class JarvisLoopController(
    /** Aktif taşıyıcı — profil bağlanınca değişir (null = sunucu yok). */
    private val transport: () -> VoiceTransport?,
    /** Döngü açıkken tek mikrofon sahibi biziz. */
    private val recorder: RecorderPort,
    /** Çalma portu — üretimde ChatViewModel'in tek PlayerPort'u (eşzamanlı çalma yok). */
    private val player: PlayerPort,
    private val cacheDir: () -> File,
    private val scope: CoroutineScope,
    /** `/health` motor haritası — üretimde 10 sn önbellekli, testte sahte. */
    private val healthEngines: suspend () -> Map<String, String>,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val lang: (String, String) -> String = { tr, _ -> tr },
    private val diag: (String) -> Unit = {},
) {

    private val _state = MutableStateFlow(JarvisLoopLogic.State())
    val state: StateFlow<JarvisLoopLogic.State> = _state.asStateFlow()

    /** Metin çözüldü — sohbet mesajı olarak gönderilecek (ViewModel: send()). */
    var onSend: (String) -> Unit = {}

    /** Kullanıcıya gösterilecek satır (sohbet bildirimi). */
    var onNotice: (String) -> Unit = {}

    /** Tam ekran altyazısı: role = "user" | "agent". */
    var onCaption: (role: String, text: String) -> Unit = { _, _ -> }

    /** Döngü motoru çözüldü — ViewModel ayardaki geçişi uygular. */
    var onLoopEngine: (engineId: String) -> Unit = {}

    /** Döngü kapandığında çağrılır (neden: "kullanici" | "komut" | "hata"). */
    var onLoopClosed: (reason: String) -> Unit = {}

    private var tickJob: Job? = null
    private val turnJobs = java.util.concurrent.ConcurrentHashMap.newKeySet<Job>()

    private fun launchTurn(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        turnJobs.add(job)
        job.invokeOnCompletion { turnJobs.remove(job) }
        job.start()
        return job
    }

    /** true → ticker hiç başlatılmaz; VAD'ı çağıran `tick()` ile sürer (test yolu). */
    private var manualVad = false

    /** Oynatma seviyesi (0..1) — reaktör level girişi; frames çizgisi yoksa 0 kalır. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** Türetilen faz — reaktör UI'sı bunu izler. */
    val phase: JarvisLoopLogic.Phase get() = _state.value.phase

    // ── Döngü yaşamı ──────────────────────────────────────────────────

    /**
     * TEK dokunuş: döngüyü açar ya da kapatır (toggle). Mikrofon izni
     * çağıran tarafta (Activity) zaten sorulmuştur — burada istenmez.
     */
    fun toggle() {
        if (_state.value.phase == JarvisLoopLogic.Phase.Off) start() else stop("kullanici")
    }

    /** Döngüyü aç: kayıt + VAD tick'i. Zaten açıksa yeniden başlatmaz. */
    fun start() = start(manualVad = false)

    /**
     * @param manualVad true → ticker BAŞLAMAZ; kesmeyi çağıran `vadTick(...)`
     *   ile kendisi sürer (JUnit determinizmi — akış testi).
     */
    fun start(manualVad: Boolean) {
        if (_state.value.phase != JarvisLoopLogic.Phase.Off) return
        this.manualVad = manualVad
        val t0 = now()
        val target = File(cacheDir(), VoiceRecordLogic.fileName(t0))
        target.parentFile?.mkdirs()
        if (!recorder.start(target)) {
            val msg = lang(
                "Mikrofon açılamadı — kayıt iznini denetle",
                "Microphone could not be opened — check the recording permission",
            )
            diag("jarvis-loop: mikrofon ACILAMADI")
            onNotice(msg)
            return
        }
        _state.value = JarvisLoopLogic.listening(t0)
        diag("jarvis-loop: dongu ACILDI vad=${JarvisLoopLogic.VAD_TICK_MS}ms")
        if (!manualVad) startTicker()
    }

    /** Durdur düğmesi / geri hareketi / "kapat" komutu — güvenli kapanış. */
    fun stop(reason: String = "kullanici") {
        turnJobs.toList().forEach { it.cancel() }
        tickJob?.cancel()
        tickJob = null
        when (_state.value.phase) {
            JarvisLoopLogic.Phase.Listening, JarvisLoopLogic.Phase.Stt -> {
                // Yarıda kapatma güvenli: yarım kayıt ATILIR, upload yok.
                recorder.cancel()
            }
            JarvisLoopLogic.Phase.Speaking -> {
                // Oynatma yarıda kesilir; bitiş kancası artık çalışmasın
                // (player.stop() onDone'ı temizler — onPlaybackDone iki kez tetiklenmez).
                player.stop()
            }
            else -> Unit
        }
        _level.value = 0f
        _state.value = JarvisLoopLogic.toOff(_state.value)
        diag("jarvis-loop: dongu kapandi neden=$reason")
        onLoopClosed(reason)
    }

    private fun startTicker() {
        tickJob?.cancel()
        if (manualVad) return   // test sürücüsü: kesme kararı dışarıdan (tick()) verilir
        tickJob = scope.launch {
            while (isActive) {
                delay(JarvisLoopLogic.VAD_TICK_MS)
                // Faz değiştiyse (ör. kullanıcı durdurdu) döngü kendiliğinden iner.
                if (_state.value.phase != JarvisLoopLogic.Phase.Listening) return@launch
                val cut = tick()
                if (cut == JarvisLoopLogic.VadCut.CutSilence || cut == JarvisLoopLogic.VadCut.CutTimeout) {
                    return@launch   // tick() kesmeyi uyguladı
                }
            }
        }
    }

    /**
     * Bir VAD yoklaması: genlik örneğini işler, kesme kararını UYGULAR ve döndürür.
     * Üretim ticker'ı da testler de aynı kapıdan geçer — davranış birebir aynı.
     */
    fun tick(): JarvisLoopLogic.VadCut {
        val s0 = _state.value
        val t = now()
        val s1 = JarvisLoopLogic.vadSample(s0, recorder.amplitude(), t)
        if (s1 !== s0) _state.value = s1
        val cut = JarvisLoopLogic.vadCut(_state.value, t)
        when (cut) {
            JarvisLoopLogic.VadCut.CutSilence, JarvisLoopLogic.VadCut.CutTimeout -> {
                diag("jarvis-loop: VAD kesti ($cut)")
                cutToStt()
            }
            JarvisLoopLogic.VadCut.CutNoVoice -> {
                // Hiç konuşmadı: turu SIFIRLA ve sessizce yeniden dinle.
                // HATA SAYILMAZ — kullanıcı o an suskundur; kapatma yolu
                // Durdur düğmesi ya da "kapat" komutudur.
                diag("jarvis-loop: 6 sn ses yok - tur sifirlandi")
                _state.value = JarvisLoopLogic.listening(now())
            }
            JarvisLoopLogic.VadCut.Continue -> Unit
        }
        return cut
    }

    // ── VAD kesimi → STT ──────────────────────────────────────────────

    /** Kaydı kes, dosyayı `/transcribe`e gönder. */
    private fun cutToStt() {
        val cur = _state.value
        if (cur.phase != JarvisLoopLogic.Phase.Listening) return
        _state.value = JarvisLoopLogic.toStt(cur)
        val recorded = recorder.stop()
        if (recorded == null) {
            // Nadir: dosya oluşmadı → hata sayacı işler, dinlemeye dön.
            sttFailure()
            return
        }
        launchTurn {
            val text = runCatching {
                val t = transport()
                    ?: throw VoiceApiException(
                        lang("Sunucu bağlı değil", "No server connected"),
                    )
                t.transcribe(recorded.file.readBytes(), recorded.file.name, recorded.mime)
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                diag("jarvis-loop: STT hatasi ${e.message?.take(120)}")
                sttFailure()
                return@launchTurn
            }
            currentCoroutineContext().ensureActive()
            if (text.isBlank()) {
                diag("jarvis-loop: STT bos")
                sttFailure()
                return@launchTurn
            }
            // Komut eşiği: "kapat" / "dur" / "bitti" → selam vermeden çık (görev 1).
            if (JarvisLoopLogic.isStopCommand(text)) {
                diag("jarvis-loop: kapat komutu '$text'")
                stop("komut")
                return@launchTurn
            }
            // Başarı: hata sayacı sıfırlanır, yanıt beklemeye geçilir.
            _state.value = JarvisLoopLogic.toWaitReplyOk(_state.value)
            onCaption("user", text)
            onSend(text)
        }
    }

    /**
     * Yeniden dinlemeye geçiş: kayıt cihazı durmuş olduğundan YENİ dosya ile
     * yeniden başlatılır. Başarısızsa döngüyü kapatır (yarım mikrofonla
     * dönmek yok).
     */
    private fun resumeListening(): Boolean {
        val t0 = now()
        val target = File(cacheDir(), VoiceRecordLogic.fileName(t0))
        target.parentFile?.mkdirs()
        if (!recorder.start(target)) {
            onNotice(
                lang(
                    "Mikrofon açılamadı — kayıt iznini denetle",
                    "Microphone could not be opened — check the recording permission",
                ),
            )
            stop("hata")
            return false
        }
        startTicker()
        return true
    }

    /** STT boş / ağ kopması: sayaç işler, eşiği aşarsa kibar kapatma. */
    private fun sttFailure() {
        val (next, closed) = JarvisLoopLogic.onSttFailure(_state.value, now())
        _state.value = next
        if (closed) {
            val msg = lang(
                "Seni duyamadım — Jarvis'i kapatıyorum",
                "Could not hear you — turning Jarvis off",
            )
            diag("jarvis-loop: ${JarvisLoopLogic.MAX_FAILURES + 1}. hata - dongu kapaniyor")
            onNotice(msg)
            stop("hata")
        } else {
            // Deneme hakkı var: [next] zaten Listening'e sıfırladı (sayaç KORUNUR).
            diag("jarvis-loop: hata sonrasi tekrar dinleniyor (n=${next.failures})")
            resumeListening()
        }
    }

    // ── Yanıt geldi → oku → dinle ─────────────────────────────────────

    /**
     * ViewModel, ajan yanıtı tamamlandığında çağırır. Yalnız `WaitReply`
     * fazında işler — normal sohbet okumalarıyla çakışmaz.
     */
    fun onAgentReply(text: String) {
        val cur = _state.value
        if (cur.phase != JarvisLoopLogic.Phase.WaitReply) return
        if (text.isBlank()) {
            // Boş yanıt: tur iptal, DÖNGÜ SÜRER — WaitReply'de kalınır, sayaç
            // işlenmez (uyanık ama sessiz ajan cezası olmaz). Geç gelen gerçek
            // yanıt aynı turda işlenir; kullanıcı isterse Durdur ile çıkar.
            diag("jarvis-loop: ajan yaniti bos - WaitReply'de bekleniyor")
            onNotice(lang(
                "Yanıt boş geldi — yeniden sorabilirsin",
                "Empty reply — you can ask again",
            ))
            return
        }
        launchTurn {
            val engines = runCatching { healthEngines() }.getOrDefault(emptyMap())
            val preferred = currentLoopEngineId
            val resolved = JarvisLoopLogic.resolveLoopEngine(preferred, engines)
            if (resolved == null) {
                val msg = lang(
                    "Ses motoru kapalı — Jarvis'i kapatıyorum",
                    "No voice engine is on — turning Jarvis off",
                )
                diag("jarvis-loop: motor cozulmedi (tercih=$preferred)")
                onNotice(msg)
                stop("hata")
                return@launchTurn
            }
            if (resolved != preferred) onLoopEngine(resolved)
            speakReply(resolved, text, engines, attempt = 0)
        }
    }

    private var currentLoopEngineId: String = JarvisLoopLogic.LOOP_FALLBACK.first()

    /** Başlangıç tercihi (Ayarlar: `jarvisLoopEngine`). */
    fun setPreferredEngine(id: String) {
        currentLoopEngineId = id
    }

    private fun speakReply(
        engineId: String,
        text: String,
        engines: Map<String, String>,
        attempt: Int,
    ) {
        _state.value = JarvisLoopLogic.toSpeaking(_state.value)
        launchTurn {
            val engine = VoiceSpeakLogic.Engine.fromId(engineId)
            val dir = File(cacheDir(), "sesli").apply { mkdirs() }
            val file = runCatching {
                val target = File(dir, VoiceSpeakLogic.cacheName(text, engine, if (engine == VoiceSpeakLogic.Engine.EMA) "wav" else "ogg"))
                if (engine != VoiceSpeakLogic.Engine.EMA && target.exists() && target.length() > 0) {
                    target
                } else {
                    val t = transport()
                        ?: throw VoiceApiException(lang(
                            "Sunucu bağlı değil", "No server connected",
                        ))
                    val bytes = t.synthesize(text, engine)
                    currentCoroutineContext().ensureActive()
                    require(bytes.isNotEmpty()) {
                        lang("Ses ucu boş yanıt döndü", "The voice endpoint returned an empty body")
                    }
                    target.writeBytes(bytes)
                    target
                }
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                speakFallback(engineId, text, engines, attempt, e.message)
                return@launchTurn
            }
            // Çalma — bittiğinde (ya da hata) hook devreye girer.
            var hooked = false
            val started = player.play(
                file,
                onDone = { if (!hooked) { hooked = true; onPlaybackDone() } },
                onError = { msg -> if (!hooked) { hooked = true; speakFallback(engineId, text, engines, attempt, msg) } },
            )
            if (!started && !hooked) {
                hooked = true
                speakFallback(engineId, text, engines, attempt, null)
            }
        }
    }

    /** Oynatma başarıyla bitti → otomatik tekrar dinlemeye geç (görev 1). */
    private fun onPlaybackDone() {
        if (_state.value.phase != JarvisLoopLogic.Phase.Speaking) return
        _state.value = JarvisLoopLogic.onSpeakDone(_state.value, now())
        diag("jarvis-loop: oynatma bitti - dinlemeye donuldu")
        launchTurn { resumeListening() }
    }

    /**
     * Motor hatası: düşüş sırasındaki SONRAKI AÇIK motoru dene; zincir
     * tükenirse hata sayacına yaz (eşik aşılırsa kibar kapatma).
     */
    private fun speakFallback(
        engineId: String,
        text: String,
        engines: Map<String, String>,
        attempt: Int,
        message: String?,
    ) {
        val next = JarvisLoopLogic.nextEngineOn(engineId, engines)
        if (next != null && attempt < JarvisLoopLogic.LOOP_FALLBACK.size) {
            diag("jarvis-loop: motor '$engineId' hatali (${message?.take(80)}) -> '$next'")
            onLoopEngine(next)
            speakReply(next, text, engines, attempt = attempt + 1)
        } else {
            onNotice(message ?: lang("Ses çalınamadı", "Could not play the audio"))
            sttFailure()
        }
    }

    /** ViewModel döngüyü dışarıdan kapatırken player'ı da durdurmalı: kancayı temizle. */
    fun onSpeakInterrupted() {
        turnJobs.toList().forEach { it.cancel() }
        if (_state.value.phase == JarvisLoopLogic.Phase.Speaking) {
            _state.value = JarvisLoopLogic.toOff(_state.value)
        }
        _level.value = 0f
    }
}
