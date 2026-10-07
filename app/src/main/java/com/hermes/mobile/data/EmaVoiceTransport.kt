package com.hermes.mobile.data

import android.content.Context

data class EmaConfig(val url: String, val token: String) {
    fun client() = EmaTtsClient(url, token)

    companion object {
        fun from(context: Context): EmaConfig? {
            val s = SettingsStore(context).settings.value
            if (s.emaUrl.isBlank()) return null
            return EmaConfig(s.emaUrl.trim(), s.emaToken.ifBlank {
                ServerProfileStore(context).active()?.token.orEmpty()
            })
        }
    }
}

/** STT stays at the existing endpoint; every TTS request goes to EMA. */
class EmaVoiceTransport(private val stt: VoiceTransport, private val ema: EmaTtsClient) : VoiceTransport {
    override val working: String? get() = stt.working
    override suspend fun health(): VoiceHealth {
        check(ema.health()) { "EMA servisi hazır değil" }
        return stt.health().copy(engines = mapOf("ema" to "hazir"))
    }
    override suspend fun transcribe(audio: ByteArray, fileName: String, mime: String) =
        stt.transcribe(audio, fileName, mime)
    override suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine) = ema.speak(text)
}
