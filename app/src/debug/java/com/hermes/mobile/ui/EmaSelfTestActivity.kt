package com.hermes.mobile.ui

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.assistant.EmaVoice
import com.hermes.mobile.data.EmaTtsClient
import com.hermes.mobile.data.PhoneTools
import com.hermes.mobile.data.SettingsStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Debug-only real EMA health, WAV, playback, cancellation and phone-tool proof. */
class EmaSelfTestActivity : ComponentActivity() {
    private lateinit var view: TextView
    private val lines = mutableListOf<String>()
    private var speaker: EmaVoice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = TextView(this).apply { setPadding(32, 64, 32, 32); textSize = 16f }
        setContentView(ScrollView(this).apply { addView(view) })
        val base = intent.getStringExtra("base") ?: "http://127.0.0.1:8176"
        val tokenFile = File(filesDir, "ema-test-token")
        val token = tokenFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        tokenFile.delete()
        val failureOnly = intent.getBooleanExtra("failure", false)
        lifecycleScope.launch {
            try {
                check(token.isNotBlank()) { "Test anahtarı gerekli" }
                val client = EmaTtsClient(base, token)
                if (!failureOnly) {
                    check(client.health())
                    step("PASS EMA health")
                    val wav = client.speak("Merhaba Gökhan. Hermes EMA ses denemesi.")
                    File(filesDir, "ema-probe.wav").writeBytes(wav)
                    step("PASS WAV bytes=${wav.size}")
                    SettingsStore(this@EmaSelfTestActivity).update { it.copy(emaUrl = base, emaToken = token) }
                }
                val done = CompletableDeferred<Unit>()
                val level = CompletableDeferred<Unit>()
                val errors = CompletableDeferred<String>()
                val completions = AtomicInteger()
                val voice = EmaVoice(this@EmaSelfTestActivity, client).also { speaker = it }
                voice.onDone = { completions.incrementAndGet(); done.complete(Unit) }
                voice.onError = { errors.complete(it) }
                voice.onLevel = { if (it > 0f) level.complete(Unit) }
                if (failureOnly) {
                    voice.say("Bağlantı hatası denemesi."); voice.finish()
                    withTimeout(15_000) { errors.await() }
                    delay(400)
                    check(completions.get() == 0)
                    step("PASS failure reported; no completion or alternative voice")
                } else {
                    voice.say("Merhaba Gökhan. Yeni sesim EMA.")
                    voice.say("Sesli ve yazılı sohbet aynı ekranda.")
                    voice.finish(); voice.finish()
                    withTimeout(90_000) { done.await() }
                    check(completions.get() == 1 && !errors.isCompleted)
                    step("PASS queued playback completed exactly once")
                    completions.set(0)
                    val cancelStarted = CompletableDeferred<Unit>()
                    voice.onLevel = { if (it > 0f) cancelStarted.complete(Unit) }
                    voice.say("İptal denemesi. " + "Bu uzun yanıt durdurulacak. ".repeat(20))
                    voice.finish()
                    withTimeout(30_000) { cancelStarted.await() }
                    delay(150)
                    voice.stop()
                    delay(800)
                    check(completions.get() == 0 && !errors.isCompleted)
                    step("PASS playback cancelled without success or error callback")
                    val status = PhoneTools(this@EmaSelfTestActivity).execute("phone_status", JsonObject(emptyMap()))
                    check(status.isNotBlank())
                    step("PASS actual phone_status tool")
                }
                step("RESULT PASS")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { step("RESULT FAIL ${e.javaClass.simpleName}: ${e.message}") }
            finally { speaker?.release(); speaker = null }
        }
    }

    private fun step(message: String) {
        lines += message
        view.text = lines.joinToString("\n")
        File(filesDir, "ema-selftest.txt").writeText(lines.joinToString("\n"))
    }
    override fun onDestroy() { speaker?.release(); super.onDestroy() }
}
