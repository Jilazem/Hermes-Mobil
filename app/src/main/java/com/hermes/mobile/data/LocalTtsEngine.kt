package com.hermes.mobile.data

import android.content.Context
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.IOException

/**
 * Tur-21 yerel TTS motoru — sherpa-onnx OfflineTts + Piper tr_TR-fettah-medium.
 *
 * Model dosyaları [LocalTtsDownloader] ile indirilmiş olmalıdır
 * (filesDir/local-tts/tr-fettah). Motor TEMBEL açılır: ilk [synthesize]
 * ~1-2 sn model yükler, sonrası cümle başına ~0.2 sn (Mac ölçümü) —
 * bulut kahya'sının 173-297 sn soğuk açılımıyla kıyaslanmaz.
 *
 * Çıktı: 22050 Hz mono 16-bit WAV ([GeneratedAudio.save] — motorun kendi
 * yazıcısı; MediaPlayer ile doğrudan çalınır, önbellek adı .ogg yerine .wav).
 */
class LocalTtsEngine(context: Context) {

    private val appContext = context.applicationContext
    private val modelDir = LocalTtsLogic.modelDir(appContext.filesDir)

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var loading = false

    private val lock = Any()

    val isReady: Boolean get() = tts != null

    /** Model diskte + hash'ler doğruysa motor açılabilir demektir. */
    fun modelOk(): Boolean = LocalTtsLogic.filesPresent(modelDir)

    /**
     * Motoru yükler (idempotent, senkron — çağıran IO dispatcher'da koşar).
     *
     * @return motor hazır; model eksikse/başarısızsa false (sebep DiagLog'da).
     */
    fun ensureLoaded(): Boolean = synchronized(lock) {
        tts?.let { return true }
        if (!LocalTtsLogic.filesPresent(modelDir)) {
            DiagLog.w("localtts", "model yuklenemedi: dosyalar yok (${modelDir.absolutePath})")
            return false
        }
        loading = true
        try {
            val engine = OfflineTts(
                // Mutlak yol ile yükleme: assetManager NULL olmalı — aksi halde
                // C++ tarafı dosyayı okuyamıyor (sherpa-onnx #2562, 20.09 emülatör
                // logcat kanıtı: "assetManager is NOT set to null" + Load failed).
                null,
                OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = File(modelDir, "tr_TR-fettah-medium.onnx").absolutePath,
                            tokens = File(modelDir, "tokens.txt").absolutePath,
                            dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                        ),
                        numThreads = 2,
                    ),
                    maxNumSentences = 4,
                ),
            )
            tts = engine
            DiagLog.i("localtts", "motor hazir · ${engine.sampleRate()} Hz · ${engine.numSpeakers()} kisi")
            true
        } catch (e: Throwable) {
            // sherpa-onnx C++ tarafı C++ istisnası da fırlatabilir (UnsatisfiedLinkError vb.)
            DiagLog.e("localtts", "motor acilamadi", e)
            false
        } finally {
            loading = false
        }
    }

    /**
     * Metni seslendirir → hedef WAV dosyasına yazar, dosyayı döner.
     *
     * Boş/uygunsuz metin (yalnız noktalama) IOException fırlatır — sessiz
     * boş dosya bırakmaz ( MediaPlayer boşta hata patlatırdı).
     */
    @Throws(IOException::class)
    fun synthesize(text: String, target: File): File {
        val engine = tts ?: throw IOException("Yerel ses motoru yüklü değil")
        val clean = text.trim()
        if (clean.isBlank()) throw IOException("Okunacak metin boş")
        val audio: GeneratedAudio = try {
            engine.generate(clean, sid = 0, speed = 1.0f)
        } catch (e: Throwable) {
            throw IOException("Yerel sentez hatası: ${e.message}", e)
        }
        if (audio.samples.isEmpty()) throw IOException("Yerel sentez boş ses döndürdü")
        target.parentFile?.mkdirs()
        runCatching { target.delete() }
        if (!audio.save(target.absolutePath) || !target.exists() || target.length() == 0L) {
            throw IOException("WAV yazılamadı: ${target.name}")
        }
        DiagLog.i(
            "localtts",
            "sentez ${audio.samples.size} ornek @ ${audio.sampleRate} · ${target.length()} bayt · ${target.name}",
        )
        return target
    }

    fun release() = synchronized(lock) {
        runCatching { tts?.release() }
        tts = null
    }
}
