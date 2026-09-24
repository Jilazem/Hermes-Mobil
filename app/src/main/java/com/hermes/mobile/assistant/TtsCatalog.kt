package com.hermes.mobile.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.hermes.mobile.data.LocalTtsEngine
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.data.VoiceApiClient
import com.hermes.mobile.data.VoiceApiEndpoints
import com.hermes.mobile.data.VoiceSpeakLogic

/** Ses stüdyosunda listelenen tek telefon sesi. */
data class TtsVoiceInfo(val name: String, val label: String, val network: Boolean, val quality: Int)

/**
 * Telefonun TTS motorları ve Türkçe sesleri (Ses stüdyosu). Ad çözümü saf
 * ([voiceLabel]) — "tr-tr-x-mfs-local" gibi iç adlar kullanıcıya "Ses MFS ·
 * cihazda" olarak görünür.
 */
object TtsCatalog {

    fun engines(tts: TextToSpeech): List<Pair<String, String>> =
        runCatching { tts.engines.map { it.name to it.label } }.getOrDefault(emptyList())

    fun voices(tts: TextToSpeech, language: String = "tr"): List<TtsVoiceInfo> =
        runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet<Voice>())
            .filter { it.locale.language == language }
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }.thenBy { it.name })
            .map { TtsVoiceInfo(it.name, voiceLabel(it.name, it.isNetworkConnectionRequired), it.isNetworkConnectionRequired, it.quality) }

    /** "tr-tr-x-mfs-local" → "Ses MFS · cihazda"; "tr-TR-language" → "Varsayılan · cihazda". */
    fun voiceLabel(name: String, network: Boolean): String {
        val n = name.lowercase()
        val code = Regex("-x-([a-z0-9]+)").find(n)?.groupValues?.get(1)
            ?: Regex("smt([a-z0-9]+)").find(n)?.groupValues?.get(1)
            ?: if (n.endsWith("-language") || n.endsWith("language")) null else n.substringAfterLast('-').takeIf { it.length in 2..6 }
        val base = code?.let { "Ses ${it.uppercase()}" } ?: "Varsayılan"
        val where = if (network || n.endsWith("-network")) "internetle" else "cihazda"
        return "$base · $where"
    }

    const val SAMPLE = "Merhaba efendim, ben Hermes. Size nasıl yardımcı olabilirim?"
}

/** Ayarlardaki "Dinle" düğmeleri: seçimi kaydetmeden örnek cümleyi okur. */
class VoicePreview(private val context: Context) {
    private var current: JarvisVoice? = null

    fun play(engine: String, ttsPackage: String, voiceName: String, rate: Float, pitch: Float, onDone: () -> Unit = {}) {
        stop()
        val v: JarvisVoice = when (engine) {
            "yerel" -> {
                val e = LocalTtsEngine(context)
                if (e.modelOk()) PiperVoice(context, e, rate) else AndroidTtsVoice(context, ttsPackage, voiceName, rate, pitch)
            }
            "kahya", "kadin", "chatterbox" -> {
                val p = ServerProfileStore(context).active()
                val s = SettingsStore(context).settings.value
                if (p == null) AndroidTtsVoice(context, ttsPackage, voiceName, rate, pitch)
                else ServerVoice(
                    context,
                    VoiceApiClient(VoiceApiEndpoints.candidates(p, s.voiceUrl, s.voiceLastOk), p.token, p.id),
                    VoiceSpeakLogic.Engine.fromId(engine),
                )
            }
            else -> AndroidTtsVoice(context, ttsPackage, voiceName, rate, pitch)
        }
        current = v
        v.onDone = {
            onDone()
            if (current === v) current = null
            v.release()
        }
        v.say(TtsCatalog.SAMPLE)
        v.finish()
    }

    fun stop() {
        current?.release()
        current = null
    }
}
