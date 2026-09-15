package com.hermes.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Sohbette sesli mesaj katmanının beyni: **kayıt → metin** ve
 * **metin → ses → çalma** akışlarının tek sahibi.
 *
 * Ayarlardan gelen tercihler:
 *  - [autoSend] — metin doğrudan gönderilsin mi (varsayılan **KAPALI**; kapalıyken
 *    metin sohbet girdisine yazılır, gönderimi kullanıcı yapar)
 *  - [engine] — `kahya | chatterbox | kadin` (varsayılan kahya)
 *
 * Android'e bağlı DEĞİL: kayıt/çalma [RecorderPort]/[PlayerPort] arayüzlerinden
 * enjekte edilir, taşıyıcı [VoiceTransport]. Bu yüzden akışın tamamı JVM
 * testinde sahte portlarla koşar (`VoiceMessageFlowTest`) — kayıt dosyası
 * testte asset'ten gelir.
 *
 * Kayıt 60 sn'de KENDİLİĞİNDEN durur ve yükleme başlar ([VoiceRecordLogic]):
 * sözleşme "<=60 sn" diyor, kullanıcı parmağını basılı tutmayı unutsa bile.
 */
class VoiceMessageController(
    /** Aktif taşıyıcı — profil bağlanınca değişir (null = sunucu yok). */
    private val transport: () -> VoiceTransport?,
    /** İndirilen seslerin yazılacağı dizin (uygulama cache'i). */
    private val cacheDir: () -> File,
    private val scope: CoroutineScope,
    private val recorder: RecorderPort = RecorderPort.Noop,
    private val player: PlayerPort = PlayerPort.Noop,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val diag: (String) -> Unit = {},
    private val lang: (String, String) -> String = { tr, _ -> tr },
) {

    data class UiState(
        val record: VoiceRecordLogic.State = VoiceRecordLogic.State(),
        val speak: VoiceSpeakLogic.State = VoiceSpeakLogic.State(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Ayarlar: "otomatik gönder" — varsayılan KAPALI. */
    @Volatile
    var autoSend: Boolean = false

    /** Ayarlar: ses motoru — varsayılan kahya. */
    @Volatile
    var engine: VoiceSpeakLogic.Engine = VoiceSpeakLogic.Engine.DEFAULT

    /** Metin hazır olduğunda çağrılır (gönder ya da girdiye yaz). */
    var onTranscript: (String) -> Unit = {}

    /** Hata/uyarı satırı (sohbet akışına bildirim olarak düşer). */
    var onNotice: (String) -> Unit = {}

    /** Çalışan ses ucu adresi hatırlandı — ayarlara yazılır. */
    var onWorkingBase: (String) -> Unit = {}

    private var tickJob: Job? = null
    private var speakJob: Job? = null

    // ── Kayıt ─────────────────────────────────────────────────────────

    /** Mikrofon düğmesine basıldı. */
    fun holdStart() {
        val prev = _state.value.record
        val next = VoiceRecordLogic.start(prev, now())
        if (next.phase != VoiceRecordLogic.Phase.Recording || next === prev) {
            _state.value = _state.value.copy(record = next)
            return
        }
        val target = File(cacheDir(), VoiceRecordLogic.fileName(now()))
        target.parentFile?.mkdirs()
        if (!recorder.start(target)) {
            _state.value = _state.value.copy(
                record = VoiceRecordLogic.failed(
                    next,
                    lang(
                        "Mikrofon açılamadı — kayıt iznini denetle",
                        "Microphone could not be opened — check the recording permission",
                    ),
                ),
            )
            onNotice(_state.value.record.message.orEmpty())
            return
        }
        _state.value = _state.value.copy(record = next)
        diag("kayit basladi ${target.name}")
        startTicker()
    }

    /** Mikrofon düğmesi bırakıldı — 0,8 sn'den kısa basış kazara sayılır. */
    fun holdRelease() {
        val cur = _state.value.record
        if (cur.phase != VoiceRecordLogic.Phase.Recording) {
            // Kayıt yokken bırakma: hiçbir şey yapılmaz (kaydırıp çıkma).
            return
        }
        val elapsed = (now() - cur.startedAtMs).coerceAtLeast(0L)
        val (next, action) = VoiceRecordLogic.release(cur, elapsed)
        _state.value = _state.value.copy(record = next)
        when (action) {
            VoiceRecordLogic.ReleaseAction.Discard -> {
                tickJob?.cancel()
                recorder.cancel()
                diag("kayit cok kisa (${elapsed} ms) - atildi")
            }
            VoiceRecordLogic.ReleaseAction.Upload -> uploadRecording()
            VoiceRecordLogic.ReleaseAction.Ignore -> Unit
        }
    }

    /** Kaydı iptal et (parmağı kaydırıp çıkma). */
    fun cancelRecording() {
        tickJob?.cancel()
        recorder.cancel()
        _state.value = _state.value.copy(record = VoiceRecordLogic.cancelled())
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val cur = _state.value.record
                if (cur.phase != VoiceRecordLogic.Phase.Recording) return@launch
                val elapsed = (now() - cur.startedAtMs).coerceAtLeast(0L)
                _state.value = _state.value.copy(record = VoiceRecordLogic.tick(cur, elapsed))
                if (VoiceRecordLogic.autoStopReached(cur, elapsed)) {
                    // Sözleşme tavanı: parmak basılı olsa da kaydı kapat ve yükle.
                    diag("kayit 60 sn tavanina ulasti - kendiliginden durduruldu")
                    holdRelease()
                    return@launch
                }
            }
        }
    }

    private fun uploadRecording() {
        val recorded = recorder.stop()
            ?: run {
                val msg = lang("Kayıt dosyası oluşmadı", "The recording file was not created")
                _state.value = _state.value.copy(
                    record = VoiceRecordLogic.failed(_state.value.record, msg),
                )
                onNotice(msg)
                return
            }
        diag("kayit bitti ${recorded.file.name} (${recorded.file.length()} bayt)")
        scope.launch {
            val text = runCatching { transcribeFile(recorded.file.readBytes(), recorded.file.name, recorded.mime) }
                .getOrElse { e ->
                    val msg = e.message ?: lang("Metne çevrilemedi", "Transcription failed")
                    _state.value = _state.value.copy(
                        record = VoiceRecordLogic.failed(_state.value.record, msg),
                    )
                    onNotice(msg)
                    return@launch
                }
            _state.value = _state.value.copy(record = VoiceRecordLogic.transcribed(_state.value.record))
            rememberWorking()
            if (text.isBlank()) {
                val msg = lang("Ses anlaşılamadı", "Could not make out the audio")
                _state.value = _state.value.copy(
                    record = VoiceRecordLogic.failed(_state.value.record, msg),
                )
                onNotice(msg)
                return@launch
            }
            onTranscript(text)
        }
    }

    /**
     * Yükleme → metin. Testler ve emülatör öz-testi doğrudan bunu çağırır
     * (kayıt dosyası asset'ten gelir).
     */
    suspend fun transcribeFile(audio: ByteArray, fileName: String, mime: String = "audio/ogg"): String {
        val t = requireTransport()
        _state.value = _state.value.copy(
            record = VoiceRecordLogic.State(VoiceRecordLogic.Phase.Transcribing),
        )
        return t.transcribe(audio, fileName, mime)
    }

    // ── Sesli okuma ───────────────────────────────────────────────────

    /**
     * Asistan balonunu seslendirir; aynı balona ikinci dokunuş DURDURUR.
     *
     * İndirme önbellekten gelirse çalma anında başlar; gelmezse önce indirilir
     * ve bekleme satırı gösterilir (soğuk motor uyarısı
     * [VoiceSpeakLogic.COLD_HINT_AFTER_MS] sonra).
     */
    fun speak(key: String, markdown: String) {
        if (VoiceSpeakLogic.togglesOff(_state.value.speak, key)) {
            stopSpeaking()
            return
        }
        val text = VoiceSpeakLogic.prepare(markdown)
        if (text == null) {
            onNotice(lang("Okunacak metin yok", "Nothing to read out"))
            return
        }
        speakJob?.cancel()
        _state.value = _state.value.copy(speak = VoiceSpeakLogic.start(key))
        speakJob = scope.launch {
            val tick = launch {
                while (isActive) {
                    delay(TICK_MS)
                    val st = _state.value.speak
                    if (st.phase != VoiceSpeakLogic.Phase.Downloading) return@launch
                    _state.value = _state.value.copy(speak = VoiceSpeakLogic.waiting(st, st.waitingMs + TICK_MS))
                }
            }
            val file = runCatching { ensureAudio(key, text) }.getOrElse { e ->
                tick.cancel()
                val msg = e.message ?: lang("Ses indirilemedi", "Could not download audio")
                _state.value = _state.value.copy(speak = VoiceSpeakLogic.failed(msg))
                onNotice(msg)
                return@launch
            }
            tick.cancel()
            rememberWorking()
            playFile(key, file)
        }
    }

    /** Zaten dosya varsa indirmez; yoksa sentezleyip önbelleğe yazar. */
    suspend fun ensureAudio(key: String, text: String): File {
        val dir = File(cacheDir(), "sesli").apply { mkdirs() }
        val target = File(dir, VoiceSpeakLogic.cacheName(text, engine))
        if (target.exists() && target.length() > 0) {
            diag("ses onbellekten: ${target.name}")
            return target
        }
        val t = requireTransport()
        val started = now()
        val bytes = t.synthesize(text, engine)
        if (bytes.isEmpty()) {
            throw VoiceApiException(lang("Ses ucu boş yanıt döndü", "The voice endpoint returned an empty body"))
        }
        target.writeBytes(bytes)
        diag("sentez ${engine.id} · ${bytes.size} bayt · ${now() - started} ms · ${target.name}")
        return target
    }

    private fun playFile(key: String, file: File) {
        val ok = player.play(file, onDone = {
            if (_state.value.speak.key == key) {
                _state.value = _state.value.copy(speak = VoiceSpeakLogic.done())
            }
        }, onError = { msg ->
            _state.value = _state.value.copy(speak = VoiceSpeakLogic.failed(msg))
            onNotice(msg)
        })
        _state.value = _state.value.copy(
            speak = if (ok) VoiceSpeakLogic.started(key, cached = true)
            else VoiceSpeakLogic.failed(lang("Ses çalınamadı", "Could not play the audio")),
        )
    }

    fun stopSpeaking() {
        speakJob?.cancel()
        player.stop()
        _state.value = _state.value.copy(speak = VoiceSpeakLogic.done())
    }

    /** Metni sentezler ve dosyayı döndürür (emülatör öz-testi). */
    suspend fun synthesizeToFile(text: String): File = ensureAudio("selftest", text)

    /** `GET /health` — Ayarlar'daki "şimdi dene". */
    suspend fun healthLine(): String {
        val t = requireTransport()
        val h = t.health()
        rememberWorking()
        return "${VoiceApiEndpoints.healthLine(h, lang)} · ${t.working ?: "?"}"
    }

    fun clearMessages() {
        _state.value = _state.value.copy(
            record = VoiceRecordLogic.State(),
            speak = _state.value.speak.copy(message = null),
        )
    }

    private fun requireTransport(): VoiceTransport =
        transport() ?: throw VoiceApiException(
            lang(
                "Sunucu bağlı değil — ses ucu için önce sunucu ekle",
                "No server connected — add a server before using the voice endpoint",
            ),
        )

    private fun rememberWorking() {
        transport()?.working?.takeIf { it.isNotBlank() }?.let(onWorkingBase)
    }

    private companion object {
        const val TICK_MS = 120L
    }
}

/**
 * Kayıt portu — Android [android.media.MediaRecorder] sarmalayıcısı üretimde,
 * testte sahte.
 */
interface RecorderPort {

    data class Recorded(val file: File, val mime: String)

    /** Kaydı başlatır; mikrofon açılamazsa `false`. */
    fun start(target: File): Boolean

    /** Kaydı bitirir ve dosyayı döndürür (yoksa null). */
    fun stop(): Recorded?

    fun cancel()

    object Noop : RecorderPort {
        override fun start(target: File): Boolean = false
        override fun stop(): Recorded? = null
        override fun cancel() = Unit
    }
}

/** Çalma portu — üretimde [android.media.MediaPlayer]. */
interface PlayerPort {
    fun play(file: File, onDone: () -> Unit, onError: (String) -> Unit): Boolean
    fun stop()

    object Noop : PlayerPort {
        override fun play(file: File, onDone: () -> Unit, onError: (String) -> Unit): Boolean = false
        override fun stop() = Unit
    }
}
