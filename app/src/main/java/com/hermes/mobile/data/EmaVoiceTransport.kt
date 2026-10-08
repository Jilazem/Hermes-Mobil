package com.hermes.mobile.data

import android.content.Context

data class EmaConfig(val url: String, val token: String, val bases: List<String> = listOf(url), val offlineContext: Context? = null) {
    fun client(): EmaSpeech = offlineContext?.let { EmaOfflineSpeech(it) } ?: EmaTtsClient(bases, token)
    companion object {
        fun from(context: Context, profile: ServerProfile? = ServerProfileStore(context).active()): EmaConfig? {
            val s = SettingsStore(context).settings.value
            if (s.emaMode == "offline") return EmaConfig("offline-ema", "", offlineContext = context.applicationContext)
            if (s.emaUrl.isBlank() && s.emaToken.isBlank()) return null
            val p = profile
            val bases = if (s.emaUrl.isNotBlank()) {
                val custom = s.emaUrl.trim()
                val private = runCatching { java.net.URI(custom).host?.let(::isPrivateHost) == true }.getOrDefault(false)
                val remote = p?.normalizedRemote?.takeIf { private && it.isNotBlank() }?.plus("/ema-api")
                (if (p?.activeUrl == p?.normalizedRemote) listOfNotNull(remote, custom) else listOfNotNull(custom, remote))
            }
                else listOfNotNull(p?.activeUrl).plus(p?.candidates.orEmpty()).distinct().map { it.trimEnd('/') + "/ema-api" }
            if (bases.isEmpty()) return null
            return EmaConfig(bases.first(), s.emaToken.ifBlank { p?.token.orEmpty() }, bases)
        }
    }
}

/** STT stays at the existing endpoint; every TTS request goes to EMA. */
class EmaVoiceTransport(private val stt: VoiceTransport?, private val ema: EmaSpeech) : VoiceTransport {
    override val working: String? get() = stt?.working
    override suspend fun health(): VoiceHealth {
        check(ema.health()) { "EMA servisi hazır değil" }
        return (stt?.health() ?: VoiceHealth(ok = true, stt = "kapali")).copy(engines = mapOf("ema" to "hazir"))
    }
    override suspend fun transcribe(audio: ByteArray, fileName: String, mime: String) =
        (stt ?: throw java.io.IOException("Konuşmayı yazıya çevirmek için Hermes bağlantısını tamamla")).transcribe(audio, fileName, mime)
    override suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine) = ema.speak(text)
}
