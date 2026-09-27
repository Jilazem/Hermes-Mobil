package com.hermes.mobile

import com.hermes.mobile.data.JarvisIdentity
import com.hermes.mobile.data.JarvisPhase
import com.hermes.mobile.data.JarvisVisualLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tur-23 r1 (denetim SC-1 + SC-2): UI-safı bağlantı testleri.
 *
 * 1) LiveVoiceState / faz-harita / seviye saf mantığı (16 test) — denetimin
 *    "ChatScreenTest LiveVoiceState 16 testi yok" bulgusunun GERÇEK karşılığı:
 *    ayrı dosyada, ayrı adla (bu sınıf).
 * 2) WIRING (6 test) — "tanımlı ama çağrısız" uç kalmadığını kaynak dosya
 *    üzerinden doğrular (grep muadili, D-01 dersi: STATEFLow ekrana
 *    bağlanmadan bırakılmaz).
 */
class JarvisUiWiringTest {

    // ── 1) LiveVoiceState + faz haritaları (16) ──────────────────────────

    @Test
    fun `liveVoiceState speakLevel varsayilani sifir`() {
        assertEquals(0f, LiveVoiceState().speakLevel)
    }

    @Test
    fun `liveVoiceState copy speakLevel korur`() {
        assertEquals(0.7f, LiveVoiceState().copy(speakLevel = 0.7f).speakLevel)
    }

    @Test
    fun `liveVoiceState level ile speakLevel bagimsiz`() {
        // Barge-in: mikrofon `level` ile TTS `speakLevel` ayrı akar.
        val s = LiveVoiceState().copy(level = 0.9f, speakLevel = 0.2f)
        assertEquals(0.9f, s.level)
        assertEquals(0.2f, s.speakLevel)
    }

    @Test
    fun `liveVoiceState isRunning Idle ile false`() {
        assertFalse(LiveVoiceState().isRunning)
    }

    @Test
    fun `fromLiveState Listening karsiligi Listening`() {
        assertEquals(JarvisPhase.Listening, JarvisVisualLogic.fromLiveState("Listening"))
    }

    @Test
    fun `fromLiveState Connecting karsiligi Thinking`() {
        // LiveVoiceClient.State'te Thinking yok; STT→sunucu aralığı Connecting'tir.
        assertEquals(JarvisPhase.Thinking, JarvisVisualLogic.fromLiveState("Connecting"))
    }

    @Test
    fun `fromLiveState Speaking karsiligi Speaking`() {
        assertEquals(JarvisPhase.Speaking, JarvisVisualLogic.fromLiveState("Speaking"))
    }

    @Test
    fun `fromLiveState Idle karsiligi Idle`() {
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromLiveState("Idle"))
    }

    @Test
    fun `fromLiveState bilinmeyen durum Idle'a duser`() {
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromLiveState("YeniDurum"))
    }

    @Test
    fun `fromSpeakStage Downloading Thinking, Playing Speaking`() {
        assertEquals(JarvisPhase.Thinking, JarvisVisualLogic.fromSpeakStage("Downloading"))
        assertEquals(JarvisPhase.Speaking, JarvisVisualLogic.fromSpeakStage("Playing"))
    }

    @Test
    fun `fromSpeakStage Idle null bos hepsi Idle`() {
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromSpeakStage("Idle"))
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromSpeakStage(null))
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromSpeakStage(""))
    }

    @Test
    fun `fromAssistantPhase Recording karsiligi Listening`() {
        assertEquals(JarvisPhase.Listening, JarvisVisualLogic.fromAssistantPhase("Recording"))
    }

    @Test
    fun `fromAssistantPhase Transcribing ve AwaitingReply Thinking`() {
        assertEquals(JarvisPhase.Thinking, JarvisVisualLogic.fromAssistantPhase("Transcribing"))
        assertEquals(JarvisPhase.Thinking, JarvisVisualLogic.fromAssistantPhase("AwaitingReply"))
    }

    @Test
    fun `fromAssistantPhase Speaking karsiligi Speaking, Ready Idle`() {
        assertEquals(JarvisPhase.Speaking, JarvisVisualLogic.fromAssistantPhase("Speaking"))
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromAssistantPhase("Ready"))
    }

    @Test
    fun `effectiveSpeakLevel kare yoksa FALLBACK sabiti`() {
        // Kare gelmezse (mp3/parse yok) 'ölü' görünmesin: FALLBACK 0.6f.
        assertEquals(0.6f, JarvisVisualLogic.effectiveSpeakLevel(null, null), 0.0001f)
    }

    @Test
    fun `effectiveSpeakLevel kare varsa yumusatilmis gercek deger`() {
        val v = JarvisVisualLogic.effectiveSpeakLevel(0.8f, null)
        assertEquals(0.8f, v, 0.0001f)
        // prev ile üstel karışım: 0.1 + (0.9-0.1)*0.45 = 0.46
        val w = JarvisVisualLogic.effectiveSpeakLevel(0.9f, 0.1f)
        assertEquals(0.46f, w, 0.0001f)
    }

    // ── 2) WIRING — ölü uç kalmasın (6) ──────────────────────────────────

    /**
     * Gradle modül dizininden (app/) ya da worktree kökünden çalışabilir;
     * ikisini de dene. Gerçek dosya okunur — iddia değil kanıt (D-05).
     */
    private fun source(vararg parts: String): String {
        val rel = listOf("src", "main", "java", "com", "hermes", "mobile") + parts.toList()
        val direct = File(rel.joinToString(File.separator))
        val f = if (direct.exists()) direct
        else File("app" + File.separator + rel.joinToString(File.separator))
        assertTrue("kaynak bulunamadı: ${rel.joinToString("/")}", f.exists())
        return f.readText()
    }

    @Test
    fun `wiring sohbet seviyesi ChatScreen banner'a bagli`() {
        // MainActivity chatViewModel.voiceMsg.speakLevel'i toplar ve
        // ChatScreen.voiceSpeakLevel olarak geçirir (SC-2 hattı).
        val act = source("MainActivity.kt")
        assertTrue("voiceMsg.speakLevel MainActivity'de toplanmıyor", act.contains("voiceMsg.speakLevel"))
        assertTrue("voiceSpeakLevel ChatScreen'e geçirilmiyor", act.contains("voiceSpeakLevel = voiceSpeakLevel"))
        val cs = source("ui", "ChatScreen.kt")
        assertTrue("ChatScreen voiceSpeakLevel parametresi yok", cs.contains("voiceSpeakLevel: Float"))
        // Ölü parameter olmaması: AssistantBanner'a gerçekten veriliyor.
        assertTrue("voiceSpeakLevel AssistantBanner'a gitmiyor", cs.contains("level = voiceSpeakLevel"))
    }

    @Test
    fun `wiring playFile frames + onLevel besliyor`() {
        val vmc = source("data", "VoiceMessageController.kt")
        assertTrue("frames üretilmiyor", vmc.contains("framesFromWav"))
        assertTrue("onLevel _speakLevel'e yazılmıyor", vmc.contains("_speakLevel.value = v"))
    }

    @Test
    fun `wiring player level ticker sesi frames ile suruyor`() {
        val vap = source("data", "VoiceAudioPorts.kt")
        assertTrue("ticker karesiz kuruluyor", vap.contains("if (frames != null && frames.isNotEmpty())"))
        assertTrue("frameIndexAt kullanılmıyor", vap.contains("SpeakLevelLogic.frameIndexAt(pos, frames.size)"))
    }

    @Test
    fun `wiring persona SYSTEM_PROMPT modele gidiyor`() {
        // KAPSAM-1: systemPrompt ölü kalmasın — chat çağrısında gerçekten geçsin.
        val vm = source("ChatViewModel.kt")
        assertTrue(
            "system parametresi aktarılmıyor",
            vm.contains("system = JarvisIdentity.SYSTEM_PROMPT"),
        )
        val jl = source("data", "JarvisLogic.kt")
        assertTrue("yönerge ad kaynaklı değil", jl.contains("Sen ${'$'}NAME'sın"))
    }

    @Test
    fun `wiring persona SYSTEM_PROMPT icerik dogrulugu`() {
        val p = JarvisIdentity.SYSTEM_PROMPT
        assertTrue("ad geçmiyor", p.startsWith("Sen ${JarvisIdentity.NAME}"))
        assertTrue("Türkçe yönerge", p.contains("Türkçe konuş"))
        assertTrue("NAME ile tutarlı", p.contains(JarvisIdentity.NAME))
    }

    @Test
    fun `wiring JarvisVisualizer her iki UI yolunda da cagriliyor`() {
        // LiveVoiceSheet (canlı ses) ve ChatScreen (asistan şeridi).
        assertTrue(
            source("ui", "LiveVoiceSheet.kt").contains("JarvisVisualizer("),
        )
        assertTrue(
            source("ui", "ChatScreen.kt").contains("JarvisVisualizer("),
        )
    }
}
