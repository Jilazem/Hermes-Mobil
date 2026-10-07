package com.hermes.mobile.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Sesli konuşma — ChatGPT'nin sesli sohbet kipine denk eller-serbest döngü.
 *
 *   dinle → yazıya çevir → ajana gönder → yanıtı seslendir → tekrar dinle
 *
 * Android'in yerleşik `SpeechRecognizer` (STT) ve `TextToSpeech` (TTS) motorlarını
 * kullanır; Türkçe her ikisinde de destekli.
 *
 * SINIR: Bu ses→metin→ses zinciridir, ChatGPT'nin "Advanced Voice" gibi uçtan uca
 * ses akışı DEĞİL. Hermes gateway'inde ses akışı ucu yok, o yüzden mümkün değil.
 * Pratikte fark, yanıt başlarken araya girip konuşamamak ve ton/duygu aktarımının
 * olmaması.
 */
class VoiceController(private val context: Context) {

    companion object {
        /**
         * Tek seferlik seslendirme -- ajan `phone_speak` cagirdiginda.
         *
         * Sinifin kendi TTS ornegi sohbet ekranina bagli ve o ekran acik
         * olmayabilir; ajan istegi arka planda gelebiliyor. Bu yuzden kisa
         * omurlu bir motor kuruluyor, konusma bitince kapatiliyor.
         */
        fun speakOnce(context: Context, text: String) {
            EmaConfig.from(context)?.let { config ->
                val voice = com.hermes.mobile.assistant.EmaVoice(context, config.client())
                voice.onDone = { voice.release() }
                voice.onError = { message ->
                    DiagLog.w("ema", "speech failed")
                    Notifier.agentMessage(context, "Hermes EMA", message)
                    voice.release()
                }
                voice.say(text.take(3000)); voice.finish()
                return
            }
            val clean = text.take(3_000)
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context.applicationContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    engine?.shutdown()
                    return@TextToSpeech
                }
                engine?.setOnUtteranceProgressListener(
                    object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) { engine?.shutdown() }
                        @Deprecated("deprecated in API 21")
                        override fun onError(utteranceId: String?) { engine?.shutdown() }
                    }
                )
                engine?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "hermes-agent-speak")
            }
        }
    }

    enum class Mode { Off, Listening, Speaking, Thinking }

    private val _mode = MutableStateFlow(Mode.Off)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _partial = MutableStateFlow("")
    /** Dinlerken oluşan kısmi metin — kullanıcıya canlı gösterilir. */
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Eller-serbest kip: yanıt bitince kendiliğinden yeniden dinlemeye geçer. */
    var handsFree: Boolean = false
        private set

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ema: com.hermes.mobile.assistant.JarvisVoice? = null
    private var emaConfig: EmaConfig? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun refreshEma(): Boolean {
        val config = EmaConfig.from(context) ?: run {
            if (ema != null) ttsReady = false
            ema?.release(); ema = null; emaConfig = null
            return false
        }
        if (config != emaConfig) {
            ema?.release()
            tts?.stop(); tts?.shutdown(); tts = null
            emaConfig = config
            ema = com.hermes.mobile.assistant.EmaVoice(context, config.client()).also { v ->
                v.onDone = { main.post {
                    if (handsFree) onFinalText?.let { startListening(it) } else _mode.value = Mode.Off
                } }
                v.onError = { error -> main.post { handsFree = false; _error.value = error; _mode.value = Mode.Off } }
            }
        }
        ttsReady = true
        return true
    }
    private var onFinalText: ((String) -> Unit)? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun initTts(onReady: (Boolean) -> Unit = {}) {
        if (refreshEma()) { onReady(true); return }
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("tr", "TR")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _mode.value = Mode.Speaking
                    }

                    override fun onDone(utteranceId: String?) {
                        if (handsFree) startListening(onFinalText ?: return)
                        else _mode.value = Mode.Off
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _mode.value = Mode.Off
                    }
                })
            }
            onReady(ttsReady)
        }
    }

    fun startListening(onResult: (String) -> Unit) {
        onFinalText = onResult
        _error.value = null
        _partial.value = ""

        if (!isAvailable) {
            _error.value = "Bu cihazda ses tanıma yok"
            _mode.value = Mode.Off
            return
        }

        stopSpeaking()
        recognizer?.destroy()
        // V3: Hermes varsayılan asistan olunca varsayılan tanıyıcı Hermes'in
        // vekili olur — doğrudan gerçek tanıyıcıya git (çift atlama olmasın).
        recognizer = (com.hermes.mobile.assistant.RecognizerPicker.create(context)
            ?: SpeechRecognizer.createSpeechRecognizer(context)).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _mode.value = Mode.Listening
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _mode.value = Mode.Thinking
                }

                override fun onError(error: Int) {
                    _partial.value = ""
                    // Sessizlik zaman aşımı eller-serbest kipte normaldir — tekrar dinle.
                    if (handsFree && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_NO_MATCH)
                    ) {
                        startListening(onResult)
                        return
                    }
                    _error.value = describeError(error)
                    _mode.value = Mode.Off
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()
                    _partial.value = ""
                    if (text.isEmpty()) {
                        if (handsFree) startListening(onResult) else _mode.value = Mode.Off
                        return
                    }
                    _mode.value = Mode.Thinking
                    onResult(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    _partial.value = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        _partial.value = ""
        if (_mode.value == Mode.Listening) _mode.value = Mode.Off
    }

    /** Ajan yanıtını seslendirir. Markdown işaretleri sesli okumada gürültü olur, temizlenir. */
    fun speak(text: String) {
        val useEma = refreshEma()
        if (!ttsReady && !useEma) return
        val clean = stripMarkdownForSpeech(text)
        if (clean.isBlank()) {
            if (handsFree) startListening(onFinalText ?: return)
            return
        }
        _mode.value = Mode.Speaking
        if (useEma) {
            ema?.stop(); ema?.say(clean.take(3000)); ema?.finish()
        } else tts?.speak(clean.take(3_000), TextToSpeech.QUEUE_FLUSH, null, "hermes-reply")
    }

    fun stopSpeaking() {
        ema?.stop()
        tts?.stop()
        if (_mode.value == Mode.Speaking) _mode.value = Mode.Off
    }

    fun setHandsFree(enabled: Boolean, onResult: (String) -> Unit) {
        handsFree = enabled
        onFinalText = onResult
        if (enabled) startListening(onResult) else {
            stopListening()
            stopSpeaking()
            _mode.value = Mode.Off
        }
    }

    fun release() {
        handsFree = false
        ema?.release(); ema = null; emaConfig = null
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        _mode.value = Mode.Off
    }

    fun clearError() {
        _error.value = null
    }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Ses kaydı hatası"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni yok"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ağ hatası"
        SpeechRecognizer.ERROR_NO_MATCH -> "Anlaşılamadı"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses algılanmadı"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Tanıyıcı meşgul"
        else -> "Ses tanıma hatası ($code)"
    }
}

/** Kod blokları, işaretler ve URL'ler sesli okumada anlamsız — ayıklanır. */
internal fun stripMarkdownForSpeech(source: String): String = source
    .replace(Regex("```[\\s\\S]*?```"), " kod bloğu. ")
    .replace(Regex("`([^`]*)`"), "$1")
    .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    .replace(Regex("[*_~#>]+"), "")
    .replace(Regex("https?://\\S+"), " bağlantı ")
    .replace(Regex("[ \\t]+"), " ")
    .trim()
