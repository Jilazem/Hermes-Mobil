package com.hermes.mobile.ui

import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.data.AndroidVoicePlayer
import com.hermes.mobile.data.HttpVoiceTransport
import com.hermes.mobile.data.RecorderPort
import com.hermes.mobile.data.VoiceApiClient
import com.hermes.mobile.data.VoiceMessageController
import com.hermes.mobile.data.VoiceSpeakLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Tur-11 kanıt yüzeyi (yalnız DEBUG; launcher'da yok — adb ile explicit açılır).
 *
 * Emülatörde **uygulamanın gerçek sesli mesaj kodunu** koşturur:
 *  1. `GET /health` → motor durumu
 *  2. asset'teki `sesli/kayit.ogg` → `POST /transcribe` → metin
 *  3. o metin → `POST /synthesize` → ogg indirme → dosyaya yazma
 *  4. indirilen sesi `MediaPlayer` ile çalma (bitene kadar bekleme)
 *
 * Adres ve token intent extra'larıyla verilir (`base`, `token`): token kaynak
 * kodda ya da komut satırı geçmişinde DURMAZ, `--es token "$(cat ...)"` ile
 * tek kullanımlık geçirilir. Sonuç hem ekrana (uiautomator dökümü için) hem
 * `files/voice-selftest.txt` dosyasına yazılır.
 */
class VoiceSelfTestActivity : ComponentActivity() {

    private val lines = mutableListOf<String>()
    private lateinit var view: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        view = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(28, 48, 28, 28)
        }
        setContentView(
            ScrollView(this).apply {
                addView(
                    LinearLayout(this@VoiceSelfTestActivity).apply { addView(view) },
                )
            },
        )

        val base = intent.getStringExtra("base") ?: "http://10.0.2.2:8199"
        val token = intent.getStringExtra("token").orEmpty()
        val text = intent.getStringExtra("text") ?: "Bu bir sesli mesaj denemesidir."
        val engine = VoiceSpeakLogic.Engine.fromId(intent.getStringExtra("engine"))

        step("sesli mesaj oz-testi")
        step("uc: $base")
        step("token: ${if (token.isBlank()) "yok" else "var (${token.length} karakter)"}")
        step("motor: ${engine.id}")

        lifecycleScope.launch {
            val client = VoiceApiClient(listOf(base), token, "selftest")
            val transport = HttpVoiceTransport(client)
            val player = AndroidVoicePlayer()
            val seen = mutableListOf<String>()
            val ctl = VoiceMessageController(
                transport = { transport },
                cacheDir = { cacheDir },
                scope = lifecycleScope,
                recorder = RecorderPort.Noop,
                player = player,
                now = { System.currentTimeMillis() },
                lang = { tr, _ -> tr },
            ).also {
                it.engine = engine
                it.onNotice = { msg -> seen += msg }
            }

            var failed = false
            try {
                // 1) Sağlık
                val health = transport.health()
                step("1 OK health ok=${health.ok} stt=${health.stt} motorlar=${health.engines}")
            } catch (e: Exception) {
                failed = true
                step("1 HATA health: ${e.message}")
            }

            if (!failed) {
                // 2) Asset'teki kayıt → metin
                try {
                    val bytes = assets.open("sesli/kayit.ogg").use { it.readBytes() }
                    step("2 kayit.ogg okundu: ${bytes.size} bayt")
                    val t0 = System.currentTimeMillis()
                    val text2 = ctl.transcribeFile(bytes, "kayit.ogg", "audio/ogg")
                    step("2 OK metin (${System.currentTimeMillis() - t0} ms): $text2")
                    if (text2.isBlank()) {
                        failed = true
                        step("2 HATA metin boş")
                    } else {
                        // 3) Metin → ogg indirme
                        val t1 = System.currentTimeMillis()
                        val file = ctl.synthesizeToFile(text.ifBlank { text2 })
                        step(
                            "3 OK ogg indirildi: ${file.name} ${file.length()} bayt " +
                                "(${System.currentTimeMillis() - t1} ms)",
                        )
                        // 4) Çalma
                        val began = player.play(file, onDone = {}, onError = { msg -> seen += msg })
                        step("4 OYNATMA basladi=$began sure=${player.durationMs} ms")
                        var ticks = 0
                        while (player.playing && ticks < 100) {
                            delay(200)
                            ticks++
                        }
                        player.stop()
                        step("4 OK calma tamam (${ticks * 200} ms izlendi, hala caliyor=${player.playing})")
                    }
                } catch (e: Exception) {
                    failed = true
                    step("2/3/4 HATA: ${e.javaClass.simpleName}: ${e.message}")
                }
            }

            if (seen.isNotEmpty()) step("bildirimler: ${seen.joinToString(" | ")}")
            step(if (failed) "SONUC: HATA" else "SONUC: BASARILI")

            runCatching {
                File(filesDir, "voice-selftest.txt").writeText(lines.joinToString("\n") + "\n")
            }
        }
    }

    private fun step(line: String) {
        lines += line
        runOnUiThread { view.text = lines.joinToString("\n") }
    }
}
