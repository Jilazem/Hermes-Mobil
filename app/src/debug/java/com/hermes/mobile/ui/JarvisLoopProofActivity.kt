package com.hermes.mobile.ui

import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.data.JarvisLoopController
import com.hermes.mobile.data.JarvisLoopLogic
import com.hermes.mobile.data.Notifier
import com.hermes.mobile.data.PlayerPort
import com.hermes.mobile.data.RecorderPort
import com.hermes.mobile.data.VoiceApiException
import com.hermes.mobile.data.VoiceHealth
import com.hermes.mobile.data.VoiceSpeakLogic
import com.hermes.mobile.data.VoiceTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * JARVIS-2 (tur24) kanıt yüzeyi (yalnız DEBUG; launcher'da yok — adb ile açılır).
 *
 * Gerçek [JarvisLoopController] + gerçek ticker (VAD_TICK_MS) koşturur; yalnız
 * çevre sahtedir:
 *  - [ScriptRec]: betikli genlik — önce ses, sonra 1.3sn sessiz → VAD keser;
 *    dinleme yeniden başlayınca sayaç sıfırlanır (bir sonraki tur).
 *  - Mock transport: 2 turluk metin; /health tüm motorları açık döndürür.
 *  - FakePlayer: ~1sn sahte çalma, sonra onDone → otomatik dinlemeye geç.
 *
 * Kanıt akışı (log = JARVIS-LOOP-PROOF satırları):
 *   tur1: Listening→Stt→WaitReply→Speaking→(onDone)→Listening
 *   tur2: aynı zincir yeniden — altyazıda 4 satır (2 user + 2 agent).
 *   sonda "kapat" komutu → Off (neden=komut).
 *
 * Faz değişimleri ekrana yazılır + logcat'e düşer; adb screencap ile
 * farklı fazlarda çekilen PNG'ler farklı md5 üretir.
 */
class JarvisLoopProofActivity : ComponentActivity() {

    companion object { private const val TAG = "JARVIS-LOOP-PROOF" }

    private val lines = mutableListOf<String>()
    private lateinit var view: TextView

    private fun step(s: String) {
        lines += s
        Log.i(TAG, s)
        runOnUiThread { view.text = lines.joinToString("\n") }
    }

    /** Betikli mikrofon: 12 sesli + 12 sessiz örnek çevrimi — VAD her turda keser. */
    private inner class ScriptRec : RecorderPort {
        private val n = AtomicInteger()
        @Volatile private var last: File? = null
        override fun start(target: File): Boolean {
            target.parentFile?.mkdirs()
            target.writeBytes(byteArrayOf(1, 2, 3, 4))
            last = target
            return true
        }
        override fun stop(): RecorderPort.Recorded? =
            last?.let { RecorderPort.Recorded(it, "audio/mp4a-latm") }
        override fun cancel() { }
        override fun amplitude(): Int {
            // 120ms ticker; 12 sesli + 12 sessiz örnek = 1.44sn ses + 1.44sn
            // sessizlik (> SILENCE_MS 1.3sn → CutSilence). Tur 2 aynı çevrim.
            val i = n.getAndIncrement() % 24
            return if (i < 12) 6_000 else 0
        }
    }

    private inner class FakePlayer : PlayerPort {
        override fun play(
            file: File,
            onDone: () -> Unit,
            onError: (String) -> Unit,
            frames: List<Float>?,
            onLevel: (Float) -> Unit,
        ): Boolean {
            lifecycleScope.launch {
                delay(1_000)          // ~1 sn'lık sahte oynatma
                onLevel(0f)
                onDone()
            }
            return true
        }
        override fun stop() { step("player.stop() cagrildi") }
    }

    private inner class MockCloud : VoiceTransport {
        private val turns = listOf(
            "bugun hava nasıl",            // tur 1 kullanıcı metni
            "yarın toplantım var mı",      // tur 2 kullanıcı metni
        )
        private val turn = AtomicInteger()
        override suspend fun health(): VoiceHealth =
            VoiceHealth(ok = true, engines = JarvisLoopLogic.LOOP_FALLBACK.associateWith { "hazir" })
        override suspend fun transcribe(audio: ByteArray, fileName: String, mime: String): String {
            delay(200)   // sahte STT gecikmesi
            val i = turn.getAndIncrement()
            return turns.getOrElse(i) { "kapat" }   // 3. tur: kapatma komutu
        }
        override suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine): ByteArray {
            delay(150)
            if (engine.id == "chatterbox") throw VoiceApiException("mock: chatterbox kapali")
            return byteArrayOf(82, 73, 70, 70)
        }
        override val working: String? get() = "mock://proof"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(28, 48, 28, 28)
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@JarvisLoopProofActivity).apply { addView(view) })
        })

        val rec = ScriptRec()
        val cloud = MockCloud()
        val ctrl = JarvisLoopController(
            transport = { cloud },
            recorder = rec,
            player = FakePlayer(),
            cacheDir = { cacheDir },
            scope = lifecycleScope,
            healthEngines = {
                JarvisLoopLogic.LOOP_FALLBACK.associateWith { "hazir" }
            },
            diag = { msg -> step("diag: $msg") },
        )
        val sendReplies = listOf(
            "Bugün hava açık, 24 derece.",
            "Yarın saat 10:00'da toplantın var.",
        )
        var sendCount = 0
        ctrl.onSend = { text ->
            step("SEND: $text")
            // Ajan yanıtını simüle et: ViewModel'in onAgentReply kapısı.
            val reply = sendReplies.getOrElse(sendCount) { "" }
            sendCount++
            lifecycleScope.launch { delay(250); ctrl.onAgentReply(reply) }
        }
        ctrl.onNotice = { msg -> step("NOTICE: $msg") }
        ctrl.onCaption = { role, text -> step("CAPTION[$role]: $text") }
        ctrl.onLoopEngine = { id -> step("ENGINE: $id") }
        ctrl.onLoopClosed = { reason ->
            step("CLOSED: $reason")
            // Madde 4 kanıtı: gerçek Notifier — kapanışta kalıcı bildirim kalkmalı.
            Notifier.jarvisListening(this, false)
            step("NOTIF: kaldirildi")
            step("KANIT TAMAM: 2 tam tur + kapat komutu")
        }

        // Madde 4 kanıtı: gerçek Notifier.jarvisListening — kalıcı "Jarvis
        // dinliyor" bildirimi açılışta basılır (PNG'de durum çubuğunda görünür;
        // denetim r1 MEDIUM-a: harness Notify'i baypas ediyordu).
        Notifier.jarvisListening(this, true)
        step("NOTIF: basilendi (ongoing 4815)")

        // Faz izleyicisi — her değişim ekrana ve logcat'e.
        lifecycleScope.launch {
            ctrl.state.collect { st ->
                step("FAZ: ${st.phase} (hata=${st.failures})")
            }
        }

        step("JARVIS-LOOP-PROOF: dongu aciliyor (gercek ticker, sahte cevre)")
        ctrl.start()
    }
}
