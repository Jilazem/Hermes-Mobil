package com.hermes.mobile.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.LocalTtsEngine
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.data.VoiceApiClient
import com.hermes.mobile.data.VoiceApiEndpoints
import com.hermes.mobile.data.VoiceSpeakLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Asistanın sesi. Cümleler akış hâlinde kuyruğa girer ([say]); [finish] "başka
 * cümle yok" der; kuyruk boşalınca [onDone] bir kez çağrılır. [onLevel] 0..1
 * ses düzeyi verir (KITT çizgisi bununla oynar).
 */
interface JarvisVoice {
    var onDone: (() -> Unit)?
    var onLevel: ((Float) -> Unit)?
    var onError: ((String) -> Unit)?
    fun say(sentence: String)
    fun finish()
    fun stop()
    fun release()

    companion object {
        /** Ayardaki motora göre ses; yerel/sunucu kurulamazsa telefon TTS'e düşer. */
        fun create(context: Context): JarvisVoice {
            val s = SettingsStore(context).settings.value
            return when (s.assistantVoiceEngine) {
                "yerel" -> {
                    val engine = LocalTtsEngine(context)
                    if (engine.modelOk()) PiperVoice(context, engine, s.assistantSpeechRate)
                    else AndroidTtsVoice(context, s.assistantTtsPackage, s.assistantVoiceName, s.assistantSpeechRate, s.assistantPitch)
                }
                "kahya", "kadin", "chatterbox" -> {
                    val p = ServerProfileStore(context).active()
                    if (p == null) AndroidTtsVoice(context, s.assistantTtsPackage, s.assistantVoiceName, s.assistantSpeechRate, s.assistantPitch)
                    else ServerVoice(
                        context,
                        VoiceApiClient(VoiceApiEndpoints.candidates(p, s.voiceUrl, s.voiceLastOk), p.token, p.id),
                        VoiceSpeakLogic.Engine.fromId(s.assistantVoiceEngine),
                    )
                }
                else -> AndroidTtsVoice(context, s.assistantTtsPackage, s.assistantVoiceName, s.assistantSpeechRate, s.assistantPitch)
            }
        }
    }
}

/**
 * Telefonun kendi TTS motoru (Google / Samsung) — gecikmesiz, çevrimdışı.
 * Motor paketi ve ses adı Ayarlar'daki Ses stüdyosundan gelir.
 */
class AndroidTtsVoice(
    context: Context,
    enginePackage: String,
    private val voiceName: String,
    private val rate: Float,
    private val pitch: Float,
) : JarvisVoice {

    override var onDone: (() -> Unit)? = null
    override var onLevel: ((Float) -> Unit)? = null
    override var onError: ((String) -> Unit)? = null

    private var ready = false
    private var failed = false
    private val waiting = mutableListOf<String>()
    private val pending = AtomicInteger(0)
    @Volatile private var finished = false
    private var seq = 0
    private val activeUtterances = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val tts: TextToSpeech = TextToSpeech(
        context.applicationContext,
        { status -> onInit(status) },
        enginePackage.takeIf { it.isNotBlank() },
    )

    /** Tur sonu: "başka cümle yok" dendi ve kuyruk boş → bir kez haber ver. */
    private fun maybeDone() {
        if (finished && pending.get() <= 0) {
            finished = false
            pending.set(0)
            onDone?.invoke()
        }
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            DiagLog.w("jarvis", "TTS açılamadı ($status)")
            onError?.invoke("Telefon ses motoru açılamadı. Ayarlar → Metin okuma'dan bir ses motoru yükle veya seç.")
            synchronized(waiting) { waiting.clear() }
            pending.set(0)
            maybeDone()
            return
        }
        applyVoice(tts, voiceName)
        tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        tts.setPitch(pitch.coerceIn(0.5f, 2.0f))
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = utteranceEnded(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = utteranceFailed(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = utteranceFailed(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                audio?.let { onLevel?.invoke(pcm16Level(it)) }
            }
        })
        ready = true
        val queued = synchronized(waiting) { waiting.toList().also { waiting.clear() } }
        queued.forEach { speakNow(it) }
        maybeDone()
    }

    private fun utteranceFailed(id: String?) {
        if (id == null || id !in activeUtterances) return
        onError?.invoke("Ses okunamadı. Telefonun Türkçe ses paketini ve metin okuma ayarlarını kontrol et.")
        utteranceEnded(id)
    }

    private fun utteranceEnded(id: String?) {
        if (id == null || !activeUtterances.remove(id)) return
        onLevel?.invoke(0f)
        pending.decrementAndGet()
        maybeDone()
    }

    override fun say(sentence: String) {
        val clean = JarvisLogic.speakable(sentence)
        if (clean.isBlank() || failed) return
        pending.incrementAndGet()
        if (!ready) {
            synchronized(waiting) { waiting += clean }
            return
        }
        speakNow(clean)
    }

    private fun speakNow(text: String) {
        val id = "jarvis-${seq++}"
        activeUtterances.add(id)
        val r = tts.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), id)
        if (r != TextToSpeech.SUCCESS) utteranceFailed(id)
    }

    override fun finish() {
        finished = true
        if (ready || failed) maybeDone()
    }

    override fun stop() {
        synchronized(waiting) { waiting.clear() }
        pending.set(0)
        activeUtterances.clear()
        finished = false
        runCatching { tts.stop() }
        onLevel?.invoke(0f)
    }

    override fun release() {
        stop()
        runCatching { tts.shutdown() }
    }

    companion object {
        /** Seçili ses adı varsa o, yoksa Türkçe (tr-TR). */
        fun applyVoice(tts: TextToSpeech, voiceName: String) {
            val chosen: Voice? = voiceName.takeIf { it.isNotBlank() }?.let { name ->
                runCatching { tts.voices?.firstOrNull { it.name == name } }.getOrNull()
            }
            if (chosen != null) tts.voice = chosen else tts.language = Locale("tr", "TR")
        }

        /** 16 bit PCM parçasının 0..1 düzeyi (RMS, kabaca logaritmik). */
        fun pcm16Level(bytes: ByteArray): Float {
            if (bytes.size < 2) return 0f
            var sum = 0.0
            var n = 0
            var i = 0
            while (i + 1 < bytes.size) {
                val v = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
                sum += v.toDouble() * v
                n++
                i += 2
            }
            val rms = sqrt(sum / n) / 32768.0
            return (rms * 4.0).coerceIn(0.0, 1.0).toFloat()
        }
    }
}

/**
 * Dosya üreten motorlar (yerel Piper, sunucu) için ortak sıra: her cümle
 * üretilir ve SIRAYLA çalınır; üretim bir sonraki cümleyle çakışır, böylece
 * cümle arası boşluk kısalır.
 */
abstract class FileQueueVoice(context: Context) : JarvisVoice {
    override var onDone: (() -> Unit)? = null
    override var onLevel: ((Float) -> Unit)? = null
    override var onError: ((String) -> Unit)? = null

    protected val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queue = Channel<String>(Channel.UNLIMITED)
    private var worker: Job? = null
    private var player: MediaPlayer? = null
    private val dir = File(appContext.cacheDir, "jarvis").apply { mkdirs() }
    private var n = 0
    private var fallback: AndroidTtsVoice? = null

    /** Metni [target] dosyasına seslendirir (IO iş parçacığında çağrılır). */
    protected abstract suspend fun synth(text: String, target: File): File

    override fun say(sentence: String) {
        val clean = JarvisLogic.speakable(sentence)
        if (clean.isBlank()) return
        ensureWorker()
        queue.trySend(clean)
    }

    override fun finish() {
        ensureWorker()
        queue.close()
    }

    /** Her tur kendi kuyruğu: önceki tur [finish] ile kapandıysa yenisi açılır. */
    private fun ensureWorker() {
        if (worker?.isActive == true && !queue.isClosedForSend) return
        if (queue.isClosedForSend) queue = Channel(Channel.UNLIMITED)
        val q = queue
        worker = scope.launch {
            for (text in q) {
                try {
                    val file = synth(text, File(dir, "c${n++}.audio"))
                    try { play(file) } finally { file.delete() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DiagLog.w("jarvis", "sentez/çalma hatası, telefon sesi kullanılıyor: ${e.message}")
                    speakFallback(text)
                }
            }
            onLevel?.invoke(0f)
            onDone?.invoke()
        }
    }

    private suspend fun speakFallback(text: String) = withContext(Dispatchers.Main) {
        val settings = SettingsStore(appContext).settings.value
        val v = fallback ?: AndroidTtsVoice(appContext, settings.assistantTtsPackage,
            settings.assistantVoiceName, settings.assistantSpeechRate, settings.assistantPitch)
            .also { fallback = it }
        val done = CompletableDeferred<Unit>()
        v.onLevel = { onLevel?.invoke(it) }
        v.onError = { onError?.invoke(it) }
        v.onDone = { done.complete(Unit) }
        try {
            v.say(text)
            v.finish()
            withTimeout(90_000) { done.await() }
        } finally {
            v.onDone = null
            v.stop()
        }
    }

    private suspend fun play(file: File) = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        player = mp
        val pulse = scope.launch {
            // Dosya motorlarında anlık genlik yok: konuşma boyunca dalgalı düzey.
            var t = 0.0
            while (true) {
                t += 0.35
                onLevel?.invoke((0.45 + 0.35 * kotlin.math.sin(t) * kotlin.math.sin(t * 0.37)).toFloat())
                kotlinx.coroutines.delay(60)
            }
        }
        fun end(error: Exception? = null) {
            pulse.cancel()
            runCatching { mp.release() }
            if (player === mp) player = null
            if (cont.isActive) {
                if (error == null) cont.resume(Unit)
                else cont.resumeWith(Result.failure(error))
            }
        }
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { end() }
            mp.setOnErrorListener { _, what, extra -> end(IllegalStateException("Ses dosyası çalınamadı ($what/$extra)")); true }
            mp.prepare()
            mp.start()
        }.onFailure { end(IllegalStateException("Ses dosyası açılamadı", it)) }
        cont.invokeOnCancellation { pulse.cancel(); runCatching { mp.stop(); mp.release() } }
    }

    override fun stop() {
        worker?.cancel()
        worker = null
        fallback?.stop()
        queue.close()
        queue = Channel(Channel.UNLIMITED)
        runCatching { player?.stop(); player?.release() }
        player = null
        onLevel?.invoke(0f)
    }

    override fun release() {
        stop()
        fallback?.release()
        fallback = null
        scope.cancel()
    }
}

/** Yerel Piper (sherpa-onnx) — telefonda, çevrimdışı. */
class PiperVoice(context: Context, private val engine: LocalTtsEngine, private val rate: Float) : FileQueueVoice(context) {
    override suspend fun synth(text: String, target: File): File {
        if (!engine.ensureLoaded()) throw IllegalStateException("yerel ses motoru açılamadı")
        val wav = File(target.parentFile, target.nameWithoutExtension + ".wav")
        return engine.synthesize(text, wav, speed = rate)
    }
}

/** Sunucu sesi (kahya/kadın) — tek tek cümleler; soğuk motorda ilk cümle geç gelir. */
class ServerVoice(
    context: Context,
    private val client: VoiceApiClient,
    private val engine: VoiceSpeakLogic.Engine,
) : FileQueueVoice(context) {
    override suspend fun synth(text: String, target: File): File {
        val bytes = client.synthesize(text, engine)
        val ogg = File(target.parentFile, target.nameWithoutExtension + ".ogg")
        ogg.writeBytes(bytes)
        return ogg
    }
}
