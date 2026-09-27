package com.hermes.mobile.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.hermes.mobile.data.DiagLog

/**
 * Cihazdaki GERÇEK ses tanıyıcıyı bulur (Google, Samsung…), kendimizi hariç.
 *
 * Neden gerekli: Android, varsayılan dijital asistan olan uygulamanın
 * tanıyıcısını sistemin varsayılan tanıyıcısı yapar. Biz de asistan olunca
 * `SpeechRecognizer.createSpeechRecognizer(ctx)` KENDİMİZE döner → kendi
 * kendini çağıran döngü. Bu yüzden hem kendi dinlememiz hem de diğer
 * uygulamalar adına çalışan [HermesRecognitionService] her zaman açık bir
 * bileşenle gerçek tanıyıcıya gider.
 */
object RecognizerPicker {

    /** Türkçe'de en iyi sonucu verenler önce. */
    private val PREFERRED = listOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.as",
        "com.google.android.tts",
        "com.samsung.android.bixby.agent",
        "com.samsung.android.intellivoiceservice",
    )

    fun rank(packages: List<String>, self: String): List<String> =
        packages.filter { it != self }.distinct()
            .sortedBy { p -> PREFERRED.indexOf(p).let { if (it < 0) PREFERRED.size else it } }

    fun best(context: Context): ComponentName? {
        val infos = runCatching {
            context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        }.getOrDefault(emptyList())
        val byPkg = infos.mapNotNull { it.serviceInfo }.groupBy { it.packageName }
        val pkg = rank(byPkg.keys.toList(), context.packageName).firstOrNull() ?: return null
        val si = byPkg[pkg]?.firstOrNull() ?: return null
        return ComponentName(si.packageName, si.name)
    }

    /**
     * Kendi kullanımımız için tanıyıcı: önce gerçek bileşen, yoksa cihaz-içi
     * (API 31+), o da yoksa null (çağıran hata gösterir).
     */
    fun create(context: Context): SpeechRecognizer? {
        best(context)?.let { comp ->
            return runCatching { SpeechRecognizer.createSpeechRecognizer(context, comp) }.getOrNull()
        }
        if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            return runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        }
        return null
    }
}

/**
 * Varsayılan asistan için ZORUNLU tanıyıcı servisi — kendi başına tanımaz,
 * isteği cihazdaki gerçek tanıyıcıya aktarır. Böylece Hermes asistan olunca
 * klavyedeki mikrofon, Haritalar'daki sesli arama gibi diğer uygulamaların
 * sesle yazması bozulmaz.
 */
class HermesRecognitionService : RecognitionService() {

    private var delegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        delegate?.destroy()
        val real = RecognizerPicker.best(this)
        if (real == null) {
            DiagLog.w("jarvis", "tanıyıcı vekili: gerçek tanıyıcı bulunamadı")
            runCatching { listener.error(SpeechRecognizer.ERROR_CLIENT) }
            return
        }
        delegate = SpeechRecognizer.createSpeechRecognizer(this, real).apply {
            setRecognitionListener(Forward(listener))
            startListening(recognizerIntent)
        }
    }

    override fun onStopListening(listener: Callback) {
        delegate?.stopListening()
    }

    override fun onCancel(listener: Callback) {
        delegate?.cancel()
    }

    override fun onDestroy() {
        delegate?.destroy()
        delegate = null
        super.onDestroy()
    }

    /** Gerçek tanıyıcının olaylarını çağıran uygulamaya birebir taşır. */
    private class Forward(private val cb: Callback) : RecognitionListener {
        private inline fun safe(block: () -> Unit) { runCatching(block) }
        override fun onReadyForSpeech(params: Bundle?) = safe { cb.readyForSpeech(params ?: Bundle()) }
        override fun onBeginningOfSpeech() = safe { cb.beginningOfSpeech() }
        override fun onRmsChanged(rmsdB: Float) = safe { cb.rmsChanged(rmsdB) }
        override fun onBufferReceived(buffer: ByteArray?) = safe { cb.bufferReceived(buffer ?: ByteArray(0)) }
        override fun onEndOfSpeech() = safe { cb.endOfSpeech() }
        override fun onError(error: Int) = safe { cb.error(error) }
        override fun onResults(results: Bundle?) = safe { cb.results(results ?: Bundle()) }
        override fun onPartialResults(partialResults: Bundle?) = safe { cb.partialResults(partialResults ?: Bundle()) }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
