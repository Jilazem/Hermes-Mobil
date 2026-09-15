package com.hermes.mobile.ui

import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.data.AndroidVoicePlayer
import com.hermes.mobile.data.AssistantModeLogic
import com.hermes.mobile.data.HttpVoiceTransport
import com.hermes.mobile.data.RecorderPort
import com.hermes.mobile.data.VoiceApiClient
import com.hermes.mobile.data.VoiceMessageController
import com.hermes.mobile.data.VoiceRecordLogic
import com.hermes.mobile.data.VoiceSpeakLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Tur-13 kanıt yüzeyi (yalnız DEBUG; launcher'da YOK — adb ile explicit açılır).
 *
 * Emülatörde **asistan akışının gerçek üretim kodunu** koşturur:
 *  1. asistan modu kararları (gönderim + oto-okuma) — saf katman, 4 kombinasyon
 *  2. bas-konuş kayıt makinesi (kayıt → 60 sn tavanı → yükleme) — saf katman
 *  3. `GET /health`
 *  4. asset'teki `sesli/kayit.ogg` → `VoiceMessageController.transcribeFile`
 *     (PTT bırakılınca koşan kod yolu) → metin
 *  5. o metni "asistan yanıtı" sayıp **oto-okuma kuralından geçirip**
 *     `VoiceMessageController.speak` (ChatViewModel'in `message.complete`te
 *     çağırdığı metot) ile sentezleyip çalar
 *
 * Fiziksel mikrofon emülatörde yok; bu yüzden kaydın kendisi yerine kayıt
 * durum makinesi + gerçek dosyayla yükleme kanıtlanır (tur-11 deseni).
 *
 * Adres/token intent extra'sıyla gelir; token kaynakta ve kalıcı depoda
 * durmaz. Sonuç `files/asistan-selftest.txt` dosyasına da yazılır.
 */
class AssistantSelfTestActivity : ComponentActivity() {

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
                addView(LinearLayout(this@AssistantSelfTestActivity).apply { addView(view) })
            },
        )

        val base = intent.getStringExtra("base") ?: "http://10.0.2.2:8199"
        val token = intent.getStringExtra("token").orEmpty()
        val engine = VoiceSpeakLogic.Engine.fromId(intent.getStringExtra("engine"))

        step("telefon asistani oz-testi (tur-13)")
        step("uc: $base")
        step("token: ${if (token.isBlank()) "yok" else "var (${token.length} karakter)"}")
        step("motor: ${engine.id}")

        // 1) Asistan modu kararları — saf katman, uçtan bağımsız.
        val sendAssistant = AssistantModeLogic.autoSendTranscript(assistantMode = true, settingAutoSend = false)
        val sendNormal = AssistantModeLogic.autoSendTranscript(assistantMode = false, settingAutoSend = false)
        step("1a gonderme asistan=${sendAssistant} normal=${sendNormal}")
        val readAssistant = AssistantModeLogic.shouldAutoRead(true, true, "Merhaba dunya.")
        val readNormal = AssistantModeLogic.shouldAutoRead(false, true, "Merhaba dunya.")
        val readOff = AssistantModeLogic.shouldAutoRead(true, false, "Merhaba dunya.")
        step("1b oto-okuma asistan=$readAssistant normal=$readNormal ayar_kapali=$readOff")
        step("1c izin iste: ${AssistantModeLogic.askMicOnEnter(false)} / izin var: ${AssistantModeLogic.askMicOnEnter(true)}")
        val roleSelfHolds = com.hermes.mobile.data.AssistantRole.selfHolds(this)
        val roleCandidates = com.hermes.mobile.data.AssistantRole.holderCandidates(this)
        val role = AssistantModeLogic.roleStatus(
            packageName,
            roleCandidates,
            android.os.Build.VERSION.SDK_INT,
            roleSelfHolds,
        )
        step("1d rol ipuclari=${roleCandidates.size} bizim=${roleSelfHolds}")
        step("1d rol durumu: ${role.state} · ${AssistantModeLogic.roleLine(role) { tr, _ -> tr }}")
        val pure1 = sendAssistant && !sendNormal && readAssistant && !readNormal && !readOff

        // 2) Bas-konuş kayıt makinesi (fiziksel mikrofon yok) — saf durum geçişleri.
        val rec0 = VoiceRecordLogic.State()
        val rec1 = VoiceRecordLogic.start(rec0, 1_000L)
        val (rec2, action) = VoiceRecordLogic.release(rec1, 3_500L)
        val phaseReady = AssistantModeLogic.phase(true, rec0, VoiceSpeakLogic.State(), agentBusy = false)
        val phaseRec = AssistantModeLogic.phase(true, rec1, VoiceSpeakLogic.State(), agentBusy = false)
        val phaseTrans = AssistantModeLogic.phase(true, rec2, VoiceSpeakLogic.State(), agentBusy = false)
        val phaseOff = AssistantModeLogic.phase(false, rec0, VoiceSpeakLogic.State(), agentBusy = false)
        step("2 kayit fazlari: ${rec0.phase}->${rec1.phase}->${rec2.phase} islem=$action")
        step("2 asistan fazlari: hazir=$phaseReady kayit=$phaseRec ceviri=$phaseTrans kapali=$phaseOff")
        val pure2 = rec1.phase == VoiceRecordLogic.Phase.Recording &&
            rec2.phase == VoiceRecordLogic.Phase.Transcribing &&
            action == VoiceRecordLogic.ReleaseAction.Upload &&
            phaseTrans == AssistantModeLogic.Phase.Transcribing &&
            phaseOff == AssistantModeLogic.Phase.Off
        val banner = AssistantModeLogic.bannerText(phaseRec) { tr, _ -> tr }
        step("2 serit ipucu: $banner")

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

            var failed = !(pure1 && pure2)
            if (failed) step("SAF KATMAN HATASI")

            // 3) Sağlık
            try {
                val health = transport.health()
                step("3 OK health ok=${health.ok} stt=${health.stt} motorlar=${health.engines}")
            } catch (e: Exception) {
                failed = true
                step("3 HATA health: ${e.message}")
            }

            if (!failed) {
                try {
                    // 4) PTT bırakılınca koşan gerçek kod: kayıt → /transcribe → metin
                    val bytes = assets.open("sesli/kayit.ogg").use { it.readBytes() }
                    step("4 kayit.ogg okundu: ${bytes.size} bayt (ogg imza=${bytes.size > 4 && bytes[0] == 'O'.code.toByte()})")
                    val t0 = System.currentTimeMillis()
                    val transcript = ctl.transcribeFile(bytes, "kayit.ogg", "audio/ogg")
                    step("4 OK metin (${System.currentTimeMillis() - t0} ms): $transcript")
                    if (transcript.isBlank()) {
                        failed = true
                        step("4 HATA metin bos")
                    } else {
                        // 5) Yanıt geldi say → oto-okuma kuralı → gerçek speak yolu.
                        val reply = transcript
                        val read = AssistantModeLogic.shouldAutoRead(true, true, reply)
                        step("5 oto-okuma karari=$read (asistan modu + ayar acik)")
                        if (!read) {
                            failed = true
                            step("5 HATA karar false")
                        } else {
                            val key = "asistan-selftest"
                            ctl.speak(key, reply)
                            // Gerçek uçta soğuk motor 4-5 dk sürebiliyor (tur-12
                            // ölçümü 297,5 sn): bekleme bütçesi üretim tavanına
                            // (420 sn) yakın tutuluyor, 60 sn tur-13'te YETMEDİ.
                            var ticks = 0
                            while (ctl.state.value.speak.phase != VoiceSpeakLogic.Phase.Playing && ticks < 1500) {
                                delay(200)
                                ticks++
                            }
                            val playing = ctl.state.value.speak.phase == VoiceSpeakLogic.Phase.Playing
                            val dur = player.durationMs
                            step("5 OK sentez+calma basladi=$playing sure=${dur} ms (${ticks * 200} ms beklendi)")
                            var ticks2 = 0
                            while (ctl.state.value.speak.phase == VoiceSpeakLogic.Phase.Playing && ticks2 < 150) {
                                delay(200)
                                ticks2++
                            }
                            val cached = File(cacheDir, "sesli").listFiles()?.size ?: 0
                            step("5 calma bitti faz=${ctl.state.value.speak.phase} onbellek=${cached} dosya")
                            if (!playing) failed = true
                        }
                    }
                } catch (e: Exception) {
                    failed = true
                    step("4/5 HATA: ${e.javaClass.simpleName}: ${e.message}")
                }
            }

            if (seen.isNotEmpty()) step("bildirimler: ${seen.joinToString(" | ")}")
            step(if (failed) "SONUC: HATA" else "SONUC: BASARILI")

            runCatching {
                File(filesDir, "asistan-selftest.txt").writeText(lines.joinToString("\n") + "\n")
            }
        }
    }

    private fun step(line: String) {
        lines += line
        runOnUiThread { view.text = lines.joinToString("\n") }
    }
}
