package com.hermes.mobile.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.data.*
import kotlinx.coroutines.*
import java.io.File

/** Read-only checks using this device's encrypted configuration. Never exports credentials. */
class VoiceReadinessActivity : ComponentActivity() {
    private lateinit var view: TextView
    private val lines = mutableListOf<String>()
    private fun step(line: String) {
        lines += line
        view.text = lines.joinToString("\n")
        File(filesDir, "voice-readiness.txt").writeText(lines.joinToString("\n"))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = TextView(this).apply { textSize = 16f; setPadding(32, 64, 32, 32) }
        setContentView(view)
        lifecycleScope.launch {
            val s = SettingsStore(this@VoiceReadinessActivity).settings.value
            val p = ServerProfileStore(this@VoiceReadinessActivity).active()
            step("EMA mode=${s.emaMode}; model_present=${EmaModelStore.present(this@VoiceReadinessActivity)}")
            step("profile_present=${p != null}; token_present=${p?.token?.isNotBlank() == true}; remote_present=${p?.normalizedRemote?.isNotBlank() == true}")
            step("STT explicit_present=${s.voiceUrl.isNotBlank()}; explicit_private=${runCatching { java.net.URI(s.voiceUrl).host?.let(::isPrivateHost) }.getOrNull()}")
            suspend fun probe(name: String, body: suspend () -> String) {
                try { step("$name: " + withTimeout(60_000) { body() }) }
                catch (e: Exception) { step("$name: FAIL ${e.javaClass.simpleName}") }
            }
            if (p != null) {
                probe("HERMES") { "OK gateway=${HermesClient(p).status().gatewayState}" }
                if (p.normalizedRemote.isNotBlank()) probe("REMOTE HERMES") {
                    "OK gateway=${HermesClient(p.copy(baseUrl=p.normalizedRemote, remoteUrl="")).status().gatewayState}"
                }
            }
            val ema = EmaConfig.from(this@VoiceReadinessActivity)?.client()
            var wav: ByteArray? = null
            if (ema == null) step("EMA: NOT CONFIGURED") else {
                probe("EMA HEALTH") { "ready=${ema.health()}" }
                probe("EMA SYNTHESIS") {
                    wav=ema.speak("Merhaba. Bu bir ses bağlantısı denemesidir.")
                    "OK bytes=${wav!!.size}"
                }
            }
            if (p != null) {
                val bases=VoiceApiEndpoints.candidates(p,s.voiceUrl,s.voiceLastOk)
                step("STT candidate_count=${bases.size}; private_count=${bases.count { runCatching { java.net.URI(it).host?.let(::isPrivateHost) == true }.getOrDefault(false) }}")
                val stt=VoiceApiClient(bases,p.token,p.id)
                probe("STT HEALTH") { val h=stt.health(); "OK ok=${h.ok}; stt=${h.stt}" }
                wav?.let { bytes -> probe("STT TRANSCRIBE") { "OK nonblank=${stt.transcribe(bytes,"diagnostic.wav","audio/wav").isNotBlank()}" } }
                if (p.normalizedRemote.isNotBlank()) {
                    val remoteProfile=p.copy(baseUrl=p.normalizedRemote, remoteUrl="")
                    val remoteStt=VoiceApiClient(VoiceApiEndpoints.candidates(remoteProfile),p.token,p.id)
                    probe("REMOTE STT HEALTH") { val h=remoteStt.health(); "OK ok=${h.ok}; stt=${h.stt}" }
                    wav?.let { bytes -> probe("REMOTE STT TRANSCRIBE") { "OK nonblank=${remoteStt.transcribe(bytes,"diagnostic.wav","audio/wav").isNotBlank()}" } }
                }
            }
            step("DONE")
        }
    }
}
