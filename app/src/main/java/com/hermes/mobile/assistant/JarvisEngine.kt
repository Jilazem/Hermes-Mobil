package com.hermes.mobile.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.hermes.mobile.data.CrashGuard
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.PhoneIntent
import com.hermes.mobile.data.PhoneTools
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.data.ShizukuBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class JarvisPhase { Idle, Listening, Thinking, Speaking, Error }

data class JarvisState(
    val phase: JarvisPhase = JarvisPhase.Idle,
    /** Duyulan (canlı kısmi ya da son) cümle. */
    val heard: String = "",
    /** Akan yanıt metni. */
    val answer: String = "",
    /** Ajanın o an kullandığı araç (düşünürken gösterilir). */
    val tool: String? = null,
    /** 0..1 — mikrofon ya da konuşma düzeyi (KITT çizgisi). */
    val level: Float = 0f,
    val error: String? = null,
    /** Ek ipucu: "Onay gerekiyor — sohbette aç" gibi. */
    val hint: String? = null,
)

/**
 * Jarvis döngüsü: dinle → karar ver → konuş → (sürekli kipte) yeniden dinle.
 *
 * Tüm çağrılar ANA iş parçacığından (VoiceInteractionSession geri çağrıları).
 * [onHide] paneli kapatır (durdurma sözü ya da uygulama açan komut sonrası).
 */
class JarvisEngine(
    private val context: Context,
    private val voiceFactory: (Context) -> JarvisVoice = { JarvisVoice.create(it) },
    private val settingsProvider: () -> com.hermes.mobile.data.AppSettings = { SettingsStore(context).settings.value },
    private val onHide: () -> Unit,
) {
    private val _state = MutableStateFlow(JarvisState())
    val state: StateFlow<JarvisState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CrashGuard.handler)
    private val settings get() = settingsProvider()
    private val brain = JarvisBrain(context, scope)
    private val tools by lazy { PhoneTools(context, ShizukuBridge()) }
    private var generation = 0L
    private var recognitionGeneration = 0L
    private var voice: JarvisVoice = newVoice()
    private var recognizer: SpeechRecognizer? = null
    private var turn: Job? = null
    private var silentFollowups = 0
    private var focus: AudioFocusRequest? = null
    private var focusGeneration = 0L
    private val audio = context.getSystemService(AudioManager::class.java)

    /** Ekran bağlamı (VoiceInteractionSession.onHandleAssist doldurur). */
    @Volatile var screenText: String = ""
    @Volatile var screenApp: String = ""

    val lastSessionId: String? get() = brain.lastSessionId

    private val owner = "phone-${java.util.UUID.randomUUID()}"
    private var ownsMicrophone = false

    private fun ownMicrophone() {
        if (ownsMicrophone) return
        com.hermes.mobile.data.VoiceSessions.owners.acquire(owner, false, false) { stop() }
        ownsMicrophone = true
        WakeWordControl.resume() // transfer the wake-trigger lease to this engine
    }

    private fun newVoice(): JarvisVoice = voiceFactory(context)

    /** Bind once per turn to an immutable voice identity and generation. */
    private fun bindVoice(then: () -> Unit = { afterSpeaking() }) {
        val v = voice
        val token = generation
        fun current() = token == generation && voice === v
        v.onLevel = { lvl -> main.post { if (current()) _state.update { it.copy(level = lvl) } } }
        v.onError = { message -> main.post { if (current()) fail(message) } }
        v.onDone = { main.post { if (current()) then() } }
    }

    // ── Dışarıdan ──────────────────────────────────────────────────────

    /** Panel açıldı: Google gibi hemen dinlemeye başla. */
    fun start() {
        // "Hey Jarvis" dinleyicisi mikrofonu bıraksın (tanıyıcı kullanacak).
        ownMicrophone()
        interruptAll()
        // Ses stüdyosunda yapılan seçim her açılışta geçerli olsun.
        voice.release()
        voice = newVoice()
        silentFollowups = 0
        _state.value = JarvisState()
        listen(beep = true)
    }

    /** Mikrofon düğmesi: konuşuyor/düşünüyorsa kes ve dinle; dinliyorsa bitir. */
    fun tapMic() {
        when (_state.value.phase) {
            JarvisPhase.Listening -> recognizer?.stopListening()
            JarvisPhase.Speaking, JarvisPhase.Thinking -> {
                interruptAll()
                listen(beep = true)
            }
            else -> { silentFollowups = 0; listen(beep = true) }
        }
    }

    /** Yazılarak sorulan soru (klavye). */
    fun ask(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        interruptAll()
        handle(t)
    }

    /** Explicit screen chip: use this capture once without enabling automatic screen sharing. */
    fun askWithScreen(text: String) {
        if (text.isBlank() || screenText.isBlank()) return
        interruptAll()
        handle(text.trim(), useScreen = true)
    }

    /**
     * Sesli slash komutu — çıktıyı okur, sonra dinlemeye döner.
     * Sunucu çıktısı boşsa (iş cihazda yapılmış olabilir) sessiz geçilmez:
     * hoparlör ikonu çalışan balonu gösterir, ama "yapıldı" denmez.
     */
    private fun runSlash(command: String) {
        turn = scope.launch {
            val out = runCatching { brain.slash(command) }.getOrElse { it.message ?: "Hata" }
            if (out.isBlank()) afterSpeaking() else speakOnce(out.take(400)) { afterSpeaking() }
        }
    }

    /** Yeni konu: bağlamı unut. */
    fun newTopic() {
        interruptAll()
        brain.forgetSession()
        _state.value = JarvisState(hint = "Yeni konu — dinliyorum")
        listen(beep = true)
    }

    fun stop() {
        interruptAll()
        _state.update { it.copy(phase = JarvisPhase.Idle, level = 0f) }
        releaseFocus()
        com.hermes.mobile.data.VoiceSessions.owners.release(owner)
        ownsMicrophone = false
        WakeWordControl.resume()
    }

    fun release() {
        stop()
        recognizer?.destroy()
        recognizer = null
        voice.release()
        brain.release()
        scope.cancel()
    }

    // ── Dinleme ────────────────────────────────────────────────────────

    private fun listen(beep: Boolean, retry: Boolean = false) {
        ++generation
        voice.onDone = null; voice.onLevel = null; voice.onError = null
        voice.stop()
        ownMicrophone()
        val recognitionToken = ++recognitionGeneration
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fail("Mikrofon izni yok — Hermes uygulamasını açıp mikrofon izni ver")
            return
        }
        val rec = recognizer ?: RecognizerPicker.create(context)?.also { r ->
            recognizer = r
        }
        if (rec == null) {
            fail("Bu telefonda ses tanıma servisi bulunamadı (Google uygulaması gerekli)")
            return
        }
        rec.setRecognitionListener(listenerFor(recognitionToken, retry))
        if (!grabFocus()) { fail("Ses odağı alınamadı — yeniden dene"); return }
        _state.update { it.copy(phase = JarvisPhase.Listening, heard = "", error = null, level = 0f, tool = null) }
        if (beep) earcon()
        main.postDelayed({
            if (recognitionToken != recognitionGeneration || _state.value.phase != JarvisPhase.Listening) return@postDelayed
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            scope.launch {
                try {
                    kotlinx.coroutines.withTimeout(2_000) { WakeWordControl.awaitIdle() }
                    if (recognitionToken == recognitionGeneration && _state.value.phase == JarvisPhase.Listening) rec.startListening(intent)
                } catch (e: Exception) {
                    if (recognitionToken == recognitionGeneration) fail("Dinleme başlatılamadı")
                }
            }
        }, if (beep) 160L else 0L)
    }

    private fun listenerFor(token: Long, retried: Boolean) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            if (token == recognitionGeneration && _state.value.phase == JarvisPhase.Listening)
                _state.update { it.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            if (token == recognitionGeneration) _state.update { it.copy(level = 0f) }
        }

        override fun onError(error: Int) {
            if (token != recognitionGeneration || _state.value.phase != JarvisPhase.Listening) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    silentFollowups++
                    stop()
                    _state.update { it.copy(phase = JarvisPhase.Idle, level = 0f, hint = "Dinlemek için dokun") }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    // Önceki oturumun artığı: tanıyıcıyı yenileyip bir kez daha dene.
                    recognizer?.destroy()
                    recognizer = null
                    if (retried) {
                        fail("Ses tanıyıcı başlatılamadı. Google/Samsung ses tanıma servisini ve mikrofon iznini kontrol et.")
                    } else {
                        main.postDelayed({
                            if (token == recognitionGeneration && _state.value.phase == JarvisPhase.Listening)
                                listen(beep = false, retry = true)
                        }, 350)
                    }
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fail("Mikrofon izni yok — Hermes uygulamasını açıp izin ver")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    fail("Ses tanıma için internet gerekli")
                else -> fail("Ses tanıma hatası ($error)")
            }
        }

        override fun onResults(results: Bundle?) {
            if (token != recognitionGeneration || _state.value.phase != JarvisPhase.Listening) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty().trim()
            if (text.isEmpty()) {
                onError(SpeechRecognizer.ERROR_NO_MATCH)
                return
            }
            silentFollowups = 0
            handle(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (token != recognitionGeneration || _state.value.phase != JarvisPhase.Listening) return
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (p.isNotBlank()) _state.update { it.copy(heard = p) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    // ── Karar ─────────────────────────────────────────────────────────

    private fun handle(text: String, useScreen: Boolean = false) {
        ownMicrophone()
        if (!grabFocus()) { fail("Ses odağı alınamadı — yeniden dene"); return }
        val turnToken = ++generation
        // Never reuse a voice whose worker may still deliver a callback.
        voice.release()
        voice = newVoice()
        bindVoice()
        ++recognitionGeneration
        _state.update { it.copy(phase = JarvisPhase.Thinking, heard = text, answer = "", tool = null, error = null, hint = null, level = 0f) }
        DiagLog.i("jarvis", "soru (${text.length} krkt)")

        // Seslenme ayarı: tetikleyici "hey jarvis" modelini dinler ama komut
        // ayardaki kelimeyle başlıyorsa soyulur ("hey kitt hava durumu" →
        // "hava durumu"). Başlamıyorsa kullanıcı normal soru sormuştur.
        val heard = JarvisLogic.stripWake(text, settings.wakeWord) ?: text

        // Sesli Hermes komutu: "buna cevap verme", "ayri dal ac"…
        JarvisLogic.voiceSlash(heard)?.let { cmd ->
            speakOnce(if (cmd.startsWith("/steer")) "Tamam, ekliyorum." else "Tamam.") { runSlash(cmd) }
            return
        }

        if (JarvisLogic.isStopPhrase(heard)) {
            speakOnce(if (heard.contains("teşekkür", true) || heard.contains("sağ ol", true)) "Rica ederim." else "Tamam.") {
                onHide()
            }
            return
        }

        // Telefon komutu: sunucuya gitmeden, anında.
        PhoneIntent.parse(heard)?.let { act ->
            turn = scope.launch {
                val out = withContext(Dispatchers.IO) {
                    try { kotlinx.coroutines.runInterruptible { tools.execute(act.tool, act.toJson()) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { error.message ?: "Hata" }
                }
                val spoken = if (PhoneTools.isReadTool(act.tool)) out.take(700) else out.take(200)
                _state.update { it.copy(answer = out) }
                speakOnce(spoken) {
                    if (JarvisLogic.opensApp(act.tool)) onHide() else afterSpeaking()
                }
            }
            return
        }

        val question = if ((settings.assistantScreenContext || useScreen) && JarvisLogic.needsScreen(text) && screenText.isNotBlank())
            JarvisLogic.withScreen(text, screenText, screenApp) else text

        val splitter = JarvisLogic.SentenceSplitter()
        var spokenChars = 0
        var truncated = false
        fun speakChunk(sentence: String) {
            // Uzun yanıtın tamamı okunmaz: ~900 karakterden sonra "devamı sohbette".
            if (spokenChars >= JarvisLogic.MAX_SPOKEN_CHARS) { truncated = true; return }
            spokenChars += sentence.length
            if (_state.value.phase == JarvisPhase.Thinking) _state.update { it.copy(phase = JarvisPhase.Speaking) }
            voice.say(sentence)
        }
        voice.stop()
        turn = scope.launch {
            val sink = object : JarvisBrain.Sink {
                override fun onDelta(text: String) {
                    main.post {
                        if (turnToken != generation) return@post
                        _state.update { it.copy(answer = it.answer + text, tool = null) }
                        splitter.push(text).forEach(::speakChunk)
                    }
                }
                override fun onTool(name: String) {
                    main.post { if (turnToken == generation) _state.update { it.copy(tool = name) } }
                }
                override fun onNeedsApproval(text: String) {
                    main.post { if (turnToken == generation) _state.update { it.copy(hint = "Ajan onay/bilgi istiyor — \"Sohbette aç\" ile yanıtla") } }
                }
            }
            val result = try { Result.success(brain.ask(question, sink)) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { Result.failure(e) }
            main.post {
                if (turnToken != generation) return@post
                result.onFailure { e ->
                    DiagLog.w("jarvis", "beyin hatası: ${e.message}")
                    _state.update { it.copy(error = e.message) }
                    speakOnce("Üzgünüm, ${e.message ?: "bir hata oldu"}") { afterSpeaking() }
                    return@post
                }
                splitter.flush()?.let(::speakChunk)
                if (truncated) voice.say("Devamı sohbet ekranında.")
                if (_state.value.answer.isBlank()) _state.update { it.copy(answer = "(yanıt yok)") }
                if (_state.value.phase == JarvisPhase.Thinking) _state.update { it.copy(phase = JarvisPhase.Speaking) }
                voice.finish()
            }
        }
    }

    /** Tek seferlik konuşma; bitince [then]. */
    private fun speakOnce(text: String, then: () -> Unit) {
        bindVoice(then)
        _state.update { it.copy(phase = JarvisPhase.Speaking) }
        voice.say(text)
        voice.finish()
    }

    private fun afterSpeaking() {
        if (_state.value.phase != JarvisPhase.Speaking && _state.value.phase != JarvisPhase.Thinking) return
        _state.update { it.copy(level = 0f) }
        if (JarvisLogic.shouldListenAgain(settings.assistantContinuous, silentFollowups) && _state.value.hint == null) {
            listen(beep = true)
        } else {
            stop()
        }
    }

    private fun interruptAll() {
        ++generation
        ++recognitionGeneration
        turn?.cancel()
        turn = null
        if (_state.value.phase == JarvisPhase.Thinking || _state.value.phase == JarvisPhase.Speaking) brain.interrupt()
        runCatching { recognizer?.cancel(); recognizer?.destroy() }
        recognizer = null
        voice.stop()
        voice.onDone = null
        voice.onError = null
        voice.onLevel = null
    }

    private fun fail(msg: String) {
        interruptAll()
        _state.update { it.copy(phase = JarvisPhase.Error, error = msg, level = 0f) }
        releaseFocus()
        com.hermes.mobile.data.VoiceSessions.owners.release(owner)
        ownsMicrophone = false
    }

    // ── Ses odağı + işaret sesi ────────────────────────────────────────

    private fun grabFocus(): Boolean {
        if (focus != null) return true
        val token = ++focusGeneration
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            ).setOnAudioFocusChangeListener({ change ->
                if (token == focusGeneration && focus != null && (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)) {
                    fail("Ses odağı kaybedildi — yeniden dene")
                }
            }, main).build()
        if (runCatching { audio.requestAudioFocus(req) }.getOrNull() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = req
        return true
    }

    private fun releaseFocus() {
        ++focusGeneration
        focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        focus = null
    }

    /** Kısa "dinliyorum" sesi — ekrana bakmadan da anlaşılsın. */
    private fun earcon() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
            main.postDelayed({ runCatching { tg.release() } }, 300)
        }
    }
}
