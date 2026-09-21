package com.hermes.mobile

import com.hermes.mobile.data.JarvisAnim
import com.hermes.mobile.data.JarvisPhase
import com.hermes.mobile.data.JarvisVisualLogic
import com.hermes.mobile.data.SpeakLevelLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Tur-23 (JARVIS-1): SpeakLevelLogic + JarvisVisualLogic saf birim testleri.
 *  - RMS kareleri  : biliner dalga genliği, 20ms pencere, tavan 1f
 *  - Kare eşleme   : oynatma-ms → kare indeksi, kenetleme, boş liste
 *  - Yumuşatma     : null ilk kare, aralık 0..1, taban
 *  - WAV ayrıştırma: geçerli başlık, desteklenmeyen biçim, bozuk dosya
 *  - Faz haritası  : 4 faz, toplam haritalar, reduced-motion durağanlık
 */
class JarvisSpeakLevelTest {

    // 440 Hz sinüs, 16kHz örnekleme, 16-bit
    private fun sine(samples: Int, amp: Short): ShortArray =
        ShortArray(samples) { (amp * sin(2.0 * Math.PI * 440.0 * it / 16000.0)).toInt().toShort() }

    @Test
    fun `rmsFrames bos girdi bos liste doner`() {
        assertEquals(emptyList<Float>(), SpeakLevelLogic.rmsFrames(ShortArray(0), 16000))
        assertEquals(emptyList<Float>(), SpeakLevelLogic.rmsFrames(sine(100, 1000), 0))
    }

    @Test
    fun `rmsFrames kare sayisi 20ms pencereye gore`() {
        // 16000 Hz → 20 ms = 320 örnek; 960 örnek → 3 kare
        val frames = SpeakLevelLogic.rmsFrames(sine(960, 8000), 16000)
        assertEquals(3, frames.size)
        assertTrue(frames.all { it in 0f..1f })
    }

    @Test
    fun `rmsFrames sesli kare sesten buyuk`() {
        val sessiz = SpeakLevelLogic.rmsFrames(sine(320, 200), 16000)[0]
        val sesli = SpeakLevelLogic.rmsFrames(sine(320, 12000), 16000)[0]
        assertTrue("sessiz=$sessiz sesli=$sesli", sesli > sessiz)
        assertTrue("sesli tavan asmasın", sesli <= 1f)
    }

    @Test
    fun `rmsFrames tam bolunmeyen uzunlukta son kare kaldirilir`() {
        // 700 örnek, 320'lik kare → 3 kare (sonuncu 60 örneklik)
        val frames = SpeakLevelLogic.rmsFrames(sine(700, 6000), 16000)
        assertEquals(3, frames.size)
    }

    @Test
    fun `frameIndexAt konumdan kare uretir ve kenetler`() {
        assertEquals(0, SpeakLevelLogic.frameIndexAt(0, 10))
        assertEquals(0, SpeakLevelLogic.frameIndexAt(19, 10))
        assertEquals(1, SpeakLevelLogic.frameIndexAt(20, 10))
        assertEquals(2, SpeakLevelLogic.frameIndexAt(50, 10))
        // negatif → 0
        assertEquals(0, SpeakLevelLogic.frameIndexAt(-50, 10))
        // tasarsa son kare
        assertEquals(9, SpeakLevelLogic.frameIndexAt(99999, 10))
        // bos
        assertEquals(-1, SpeakLevelLogic.frameIndexAt(100, 0))
    }

    @Test
    fun `smooth ilk kareyi aynen gecer, sonraki karisir`() {
        assertEquals(0.5f, SpeakLevelLogic.smooth(null, 0.5f), 1e-6f)
        // 0 → 1 : karışım 0.45
        assertEquals(0.45f, SpeakLevelLogic.smooth(0f, 1f), 1e-5f)
        // gerileme tabanın altına düşmez (next taban değilse 0'a yaklaşabilir,
        // ama 0 hedefse coerce floor next'i korur)
        val v = SpeakLevelLogic.smooth(0.5f, 0.2f)
        assertTrue(v in 0.2f..0.5f)
    }

    @Test
    fun `smooth aralikta kalir`() {
        for (prev in listOf(0f, 0.5f, 1f)) {
            for (next in listOf(-0.2f, 0f, 0.7f, 1.3f)) {
                val v = SpeakLevelLogic.smooth(prev, next)
                assertTrue("prev=$prev next=$next → $v", v in 0f..1f)
            }
        }
    }

    // ── WAV ayrıştırma ───────────────────────────────────────────────────
    private fun wav(channels: Int, sampleRate: Int, bits: Int, format: Int, pcm: ByteArray): ByteArray {
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        val out = ArrayList<ByteArray>()
        out.add("RIFF".toByteArray()); out.add(le32(36 + pcm.size))
        out.add("WAVE".toByteArray())
        out.add("fmt ".toByteArray()); out.add(le32(16))
        out.add(le16(format)); out.add(le16(channels)); out.add(le32(sampleRate))
        out.add(le32(byteRate)); out.add(le16(blockAlign)); out.add(le16(bits))
        out.add("data".toByteArray()); out.add(le32(pcm.size))
        out.add(pcm)
        return out.fold(ByteArray(0)) { a, b -> a + b }
    }

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    private fun le32(v: Int) = byteArrayOf(
        v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte(),
    )

    private fun pcmBytes(pcm: ShortArray): ByteArray =
        ByteArray(pcm.size * 2).also {
            for (i in pcm.indices) {
                it[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
                it[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte()
            }
        }

    @Test
    fun `wav gecerli ayristirilir`() {
        val pcm = sine(320, 5000)
        val bytes = wav(1, 16000, 16, 1, pcmBytes(pcm))
        val parsed = SpeakLevelLogic.parseWavPcm(bytes)
        assertNotNull(parsed)
        val (samples, rate) = parsed!!
        assertEquals(16000, rate)
        assertEquals(320, samples.size)
        // örnek değer korunmalı (tam eşitlik, LE geri dönüşüm)
        assertEquals(pcm[10].toInt(), samples[10].toInt())
        assertEquals(pcm[319].toInt(), samples[319].toInt())
    }

    @Test
    fun `wav 2 kanalda ilk kanal alinir`() {
        // L=100, R=20000 — mono'da 100 okunmalı (kanal toplamı YANILSIGI değil)
        val n = 4
        val interleaved = ShortArray(n * 2)
        for (i in 0 until n) { interleaved[i * 2] = 100; interleaved[i * 2 + 1] = 20000 }
        val bytes = wav(2, 16000, 16, 1, pcmBytes(interleaved))
        val parsed = SpeakLevelLogic.parseWavPcm(bytes)
        assertNotNull(parsed)
        assertEquals(n, parsed!!.first.size)
        for (i in 0 until n) assertEquals(100, parsed.first[i].toInt())
    }

    @Test
    fun `wav mp3-benzeri veya bozuk baslik reddedilir`() {
        // OGG başlangıcı
        val ogg = "OggS".toByteArray() + ByteArray(40)
        assertNull(SpeakLevelLogic.parseWavPcm(ogg))
        // çok kısa
        assertNull(SpeakLevelLogic.parseWavPcm(ByteArray(10)))
        // RIFF ama WAVE değil (AVI)
        val avi = "RIFF".toByteArray() + le32(40) + "AVI ".toByteArray() + ByteArray(32)
        assertNull(SpeakLevelLogic.parseWavPcm(avi))
    }

    @Test
    fun `wav desteklenmeyen bit veya format reddedilir`() {
        val pcm = pcmBytes(sine(320, 5000))
        // 8-bit
        assertNull(SpeakLevelLogic.parseWavPcm(wav(1, 16000, 8, 1, pcm)))
        // 2 kanal üstü
        assertNull(SpeakLevelLogic.parseWavPcm(wav(4, 16000, 16, 1, pcm)))
        // mu (float)
        assertNull(SpeakLevelLogic.parseWavPcm(wav(1, 16000, 16, 3, pcm)))
        // 44 bayttan kısa data'lı dosya
        assertNull(SpeakLevelLogic.parseWavPcm("RIFF".toByteArray() + ByteArray(20)))
    }

    @Test
    fun `wav kareleri parse + rms zinciri calisir`() {
        val pcm = sine(640, 9000) // 2 kare
        val bytes = wav(1, 16000, 16, 1, pcmBytes(pcm))
        val frames = SpeakLevelLogic.framesFromWav(bytes)
        assertNotNull(frames)
        assertEquals(2, frames!!.size)
        assertTrue(frames.all { it in 0f..1f })
    }

    // ── Faz haritaları ───────────────────────────────────────────────────
    @Test
    fun `faz haritasi total - her enum degeri eslenir`() {
        val liveStates = listOf(
            "Idle", "Connecting", "Listening", "Speaking", "Error", "Bilinmeyen",
        )
        val stages = listOf(
            null, "", "Idle", "Downloading", "Playing", "Bilinmeyen",
        )
        for (s in liveStates) {
            val p = JarvisVisualLogic.fromLiveState(s)
            assertNotNull(p)
            // 4 enum'dan biri
            assertTrue(p == JarvisPhase.Idle || p == JarvisPhase.Listening ||
                p == JarvisPhase.Thinking || p == JarvisPhase.Speaking)
        }
        for (s in stages) {
            assertNotNull(JarvisVisualLogic.fromSpeakStage(s))
        }
    }

    @Test
    fun `fromLiveState 4 fazı dogru esler`() {
        assertEquals(JarvisPhase.Listening, JarvisVisualLogic.fromLiveState("Listening"))
        assertEquals(JarvisPhase.Thinking, JarvisVisualLogic.fromLiveState("Connecting"))
        assertEquals(JarvisPhase.Speaking, JarvisVisualLogic.fromLiveState("Speaking"))
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromLiveState("Idle"))
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromLiveState("Error"))
    }

    @Test
    fun `fromSpeakStage 4 fazı dogru esler`() {
        assertEquals(JarvisPhase.Thinking, JarvisVisualLogic.fromSpeakStage("Downloading"))
        assertEquals(JarvisPhase.Speaking, JarvisVisualLogic.fromSpeakStage("Playing"))
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromSpeakStage("Idle"))
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromSpeakStage(null))
        assertEquals(JarvisPhase.Idle, JarvisVisualLogic.fromSpeakStage(""))
    }

    @Test
    fun `anim tablosu 4 faz icerir ve sayilar araliga uyar`() {
        assertEquals(4, JarvisVisualLogic.ANIMS.size)
        for ((phase, a) in JarvisVisualLogic.ANIMS) {
            assertTrue("pulseAmp $phase", a.pulseAmp in 0f..0.5f)
            assertTrue("centerGlow $phase", a.centerGlow in 0f..1f)
            assertTrue("arcPeriod $phase", a.arcPeriodMs >= 0)
            if (a.arcPeriodMs > 0) {
                assertTrue("pulsePeriod $phase", a.pulsePeriodMs > 0)
            }
        }
    }

    @Test
    fun `idle durağan - thinking bar kapali`() {
        val idle = JarvisVisualLogic.ANIMS[JarvisPhase.Idle]!!
        assertEquals(0, idle.arcPeriodMs)
        assertEquals(false, idle.bars)
        val think = JarvisVisualLogic.ANIMS[JarvisPhase.Thinking]!!
        assertEquals(false, think.bars)
        assertEquals(1f, think.centerGlow, 1e-6f)
        val listen = JarvisVisualLogic.ANIMS[JarvisPhase.Listening]!!
        assertTrue(listen.bars)
        assertTrue(listen.arcPeriodMs > 0)
    }

    @Test
    fun `reduced_motion hareketi durdurur renk kalır`() {
        for ((_, a) in JarvisVisualLogic.ANIMS) {
            val r = JarvisVisualLogic.reducedOf(a)
            assertEquals(0, r.pulsePeriodMs)
            assertEquals(0, r.arcPeriodMs)
            assertEquals(0f, r.pulseAmp, 1e-6f)
            assertEquals(false, r.bars)
            assertEquals(a.centerGlow, r.centerGlow, 1e-6f)
        }
    }

    @Test
    fun `effectiveSpeakLevel fallback kare yoksa sabit, varsa yumusatir`() {
        // null → FALLBACK (0.6f civarı — 0 DEĞİL, tur22 BigOrb ile birebir)
        val fb = JarvisVisualLogic.effectiveSpeakLevel(null, null)
        assertEquals(SpeakLevelLogic.FALLBACK_LEVEL, abs(fb), 1e-6f)
        // frame 1f, prev 0.2f → 0.2 + 0.45*0.8 = 0.56
        val v = JarvisVisualLogic.effectiveSpeakLevel(1f, 0.2f)
        assertEquals(0.56f, v, 1e-5f)
        // 0..1 dışına çıkmaz
        for (f in listOf(-1f, 0f, 0.5f, 2f)) {
            val r = JarvisVisualLogic.effectiveSpeakLevel(f, 0.3f)
            assertTrue("f=$f → $r", r in 0f..1f)
        }
    }

    @Test
    fun `anim veri tipi javis fazlari eksiksiz 4lu`() {
        val a: JarvisAnim = JarvisVisualLogic.ANIMS.getValue(JarvisPhase.Speaking)
        assertTrue(a.bars)
        assertEquals(1300, a.arcPeriodMs)
    }
}
