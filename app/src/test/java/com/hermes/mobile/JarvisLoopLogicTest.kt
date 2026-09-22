package com.hermes.mobile

import com.hermes.mobile.data.JarvisLoopLogic
import com.hermes.mobile.data.JarvisLoopLogic.Phase
import com.hermes.mobile.data.JarvisLoopLogic.VadCut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JARVIS-2 (tur24) — sürekli sesli sohbet döngüsünün SAF karar katmanı
 * (JVM; sahte `nowMs`'lerle zaman kesin koşulur, Android yok).
 *
 * Kapsam (görev maddesi 5):
 *  1. Döngü durum makinesi (Dinle→STT→Gönder→Bekle→Oku→Dinle).
 *  2. Sessizlik-VAD kesme kararı (eşik + 1,3 sn + hiç-ses/60 sn tavanları).
 *  3. "kapat/bitti" komut eşleşmesi.
 *  4. Hata-2-dene-kapat politikası (ardışık sayaç, başarıda sıfırlanır).
 *  5. Motor düşüş sırası chatterbox → kadin → pocket-kadin → pocket-erkek → yerel
 *     (`/health` engines haritasından).
 */
class JarvisLoopLogicTest {

    // ── 1) Durum makinesi ─────────────────────────────────────────────

    @Test
    fun `listening baslangic durumu sayaclari sifirlar`() {
        val s = JarvisLoopLogic.listening(1000L)
        assertEquals(Phase.Listening, s.phase)
        assertEquals(1000L, s.startedAtMs)
        assertEquals(1000L, s.lastVoiceAtMs)
        assertFalse(s.sawVoice)
    }

    @Test
    fun `dongu gecisleri fazlari sirayla ileri tasir`() {
        var s = JarvisLoopLogic.listening(0L)
        s = JarvisLoopLogic.toStt(s)
        assertEquals(Phase.Stt, s.phase)
        s = JarvisLoopLogic.toWaitReply(s)
        assertEquals(Phase.WaitReply, s.phase)
        s = JarvisLoopLogic.toSpeaking(s)
        assertEquals(Phase.Speaking, s.phase)
        s = JarvisLoopLogic.onSpeakDone(s, 5000L)
        assertEquals(Phase.Listening, s.phase)
        assertEquals(5000L, s.startedAtMs)
    }

    @Test
    fun `Off durumunda speakDone Off ta kalir`() {
        val s = JarvisLoopLogic.toOff(JarvisLoopLogic.listening(0L))
        assertEquals(Phase.Off, JarvisLoopLogic.onSpeakDone(s, 999L).phase)
    }

    @Test
    fun `toOff bos State dondurur`() {
        val s = JarvisLoopLogic.toOff(JarvisLoopLogic.toSpeaking(JarvisLoopLogic.listening(0L)))
        assertSame(Phase.Off, s.phase)
        assertEquals(0, s.failures)
    }

    @Test
    fun `basarili stt hata sayacini sifirlar`() {
        val dirty = JarvisLoopLogic.listening(0L).copy(failures = 2)
        val ok = JarvisLoopLogic.toWaitReplyOk(dirty)
        assertEquals(0, ok.failures)
        assertEquals(Phase.WaitReply, ok.phase)
    }

    // ── 2) VAD kesme kararı ───────────────────────────────────────────

    @Test
    fun `esik ustu ornek lastVoice gunceller`() {
        val s = JarvisLoopLogic.listening(0L)
        val hit = JarvisLoopLogic.vadSample(s, JarvisLoopLogic.AMP_THRESHOLD, 400L)
        assertTrue(hit.sawVoice)
        assertEquals(400L, hit.lastVoiceAtMs)
    }

    @Test
    fun `esik alti ornek zamanu ileri almaz`() {
        val s = JarvisLoopLogic.vadSample(JarvisLoopLogic.listening(0L), 5000, 100L)
        val s2 = JarvisLoopLogic.vadSample(s, 10, 900L)
        assertEquals(100L, s2.lastVoiceAtMs)
    }

    @Test
    fun `sessizlik 13 sn sonra keser`() {
        var s = JarvisLoopLogic.listening(0L)
        s = JarvisLoopLogic.vadSample(s, 9000, 500L)   // ses geldi
        // 500 + 1299 → henüz kesme yok
        assertEquals(VadCut.Continue, JarvisLoopLogic.vadCut(s, 500L + JarvisLoopLogic.SILENCE_MS - 1))
        // 500 + 1300 → sessizlik kesmesi
        assertEquals(VadCut.CutSilence, JarvisLoopLogic.vadCut(s, 500L + JarvisLoopLogic.SILENCE_MS))
    }

    @Test
    fun `hic ses gelmezse 6 sn tavani keser`() {
        val s = JarvisLoopLogic.listening(0L)
        assertEquals(VadCut.Continue, JarvisLoopLogic.vadCut(s, JarvisLoopLogic.NO_VOICE_MS - 1))
        assertEquals(VadCut.CutNoVoice, JarvisLoopLogic.vadCut(s, JarvisLoopLogic.NO_VOICE_MS))
    }

    @Test
    fun `60 sn sozlesme tavani her durumda keser`() {
        var s = JarvisLoopLogic.listening(0L)
        s = JarvisLoopLogic.vadSample(s, 9000, 59_000L)  // konuşma sürüyor
        assertEquals(VadCut.CutTimeout, JarvisLoopLogic.vadCut(s, 60_000L))
    }

    @Test
    fun `Listening disi fazda vad kesmez`() {
        val s = JarvisLoopLogic.toStt(JarvisLoopLogic.listening(0L))
        assertEquals(VadCut.Continue, JarvisLoopLogic.vadCut(s, 999_999L))
        assertEquals(s, JarvisLoopLogic.vadSample(s, 9999, 123L))
    }

    // ── 3) Kapat komutu ───────────────────────────────────────────────

    @Test
    fun `kisa kapat komutlari eslesir`() {
        for (cmd in listOf("kapat", "dur", "durdur", "bitti", "bitsin", "yeter", "çıkalım", "durdu")) {
            assertTrue("$cmd eşleşmeli", JarvisLoopLogic.isStopCommand(cmd))
        }
    }

    @Test
    fun `nokta ve buyuk harf komutu bozmaz`() {
        assertTrue(JarvisLoopLogic.isStopCommand(" Kapat. "))
        assertTrue(JarvisLoopLogic.isStopCommand("DUR!"))
        assertTrue(JarvisLoopLogic.isStopCommand("BİTTİ"))
    }

    @Test
    fun `uzun cumle yanlislikla kapanmaz`() {
        // "kapat" kelimesi geçse de tam eşitlik yok — asistan komutu kısa olmalı.
        assertFalse(JarvisLoopLogic.isStopCommand("kapat şunu sonra konuşuruz"))
        assertFalse(JarvisLoopLogic.isStopCommand("bugün dur dur ne güzel gün"))
        assertFalse(JarvisLoopLogic.isStopCommand(""))
    }

    // ── 4) Hata politikası ────────────────────────────────────────────

    @Test
    fun `stt hatasi ikiye kadar dinlemeye dondurur ucuncu kapatir`() {
        var s = JarvisLoopLogic.listening(0L)
        var (s1, closed1) = JarvisLoopLogic.onSttFailure(s, 1_000L)
        assertFalse(closed1)
        assertEquals(1, s1.failures)
        assertEquals(Phase.Listening, s1.phase)
        s = s1
        val (s2, closed2) = JarvisLoopLogic.onSttFailure(s, 2_000L)
        assertFalse(closed2)
        assertEquals(2, s2.failures)
        assertEquals(Phase.Listening, s2.phase)
        val (s3, closed3) = JarvisLoopLogic.onSttFailure(s2, 3_000L)
        assertTrue(closed3)
        assertEquals(3, s3.failures)
    }

    // ── 5) Motor düşüş sırası ─────────────────────────────────────────

    @Test
    fun `dusuk sirasi gorevdeki gibi`() {
        assertEquals(
            listOf("chatterbox", "kadin", "pocket-kadin", "pocket-erkek", "yerel"),
            JarvisLoopLogic.LOOP_FALLBACK,
        )
    }

    @Test
    fun `engineOn acik kapali ve eksik anahtari ayirt eder`() {
        val eng = mapOf("chatterbox" to "acik", "kadin" to "kapalı", "pocket-kadin" to "Açık")
        assertTrue(JarvisLoopLogic.engineOn(eng, "chatterbox"))
        assertFalse(JarvisLoopLogic.engineOn(eng, "kadin"))
        assertTrue(JarvisLoopLogic.engineOn(eng, "pocket-kadin"))
        assertFalse(JarvisLoopLogic.engineOn(eng, "kahya"))
    }

    @Test
    fun `tercih aciksa o secilir`() {
        val eng = mapOf(
            "chatterbox" to "acik", "kadin" to "acik",
            "pocket-kadin" to "acik", "pocket-erkek" to "acik",
        )
        assertEquals("chatterbox", JarvisLoopLogic.resolveLoopEngine("chatterbox", eng))
    }

    @Test
    fun `tercih kapaliysa siradaki acik motor gelir`() {
        val eng = mapOf(
            "chatterbox" to "kapalı", "kadin" to "kapalı",
            "pocket-kadin" to "acik", "pocket-erkek" to "acik",
        )
        assertEquals("pocket-kadin", JarvisLoopLogic.resolveLoopEngine("chatterbox", eng))
    }

    @Test
    fun `engines bos ise tercih korunur`() {
        assertEquals("chatterbox", JarvisLoopLogic.resolveLoopEngine("chatterbox", emptyMap()))
    }

    @Test
    fun `tum motorlar kapaliysa cozum null`() {
        val eng = JarvisLoopLogic.LOOP_FALLBACK.associateWith { "kapalı" } + ("kahya" to "acik")
        assertNull(JarvisLoopLogic.resolveLoopEngine("chatterbox", eng))
    }

    @Test
    fun `nextEngineOn zincirde ilerler sonu null`() {
        val eng = JarvisLoopLogic.LOOP_FALLBACK.associateWith { "acik" }
        assertEquals("kadin", JarvisLoopLogic.nextEngineOn("chatterbox", eng))
        assertEquals("yerel", JarvisLoopLogic.nextEngineOn("pocket-erkek", eng))
        assertNull(JarvisLoopLogic.nextEngineOn("yerel", eng))
        assertNull(JarvisLoopLogic.nextEngineOn("kahya", eng))
    }

    // ── 6) health cache + etiketler ───────────────────────────────────

    @Test
    fun `health cache 10 sn sonra yenilenir`() {
        assertFalse(JarvisLoopLogic.healthDue(0L, 9_999L))
        assertTrue(JarvisLoopLogic.healthDue(0L, 10_000L))
    }

    @Test
    fun `faz etiketleri bos olmayan tr metinler dondurur`() {
        val tr: (String, String) -> String = { a, _ -> a }
        assertEquals("", JarvisLoopLogic.phaseLabel(JarvisLoopLogic.State(), tr))
        val s = JarvisLoopLogic.listening(0L)
        assertEquals("Dinliyor…", JarvisLoopLogic.phaseLabel(s, tr))
        assertEquals(
            "Metne çevriliyor…",
            JarvisLoopLogic.phaseLabel(JarvisLoopLogic.toStt(s), tr),
        )
        assertEquals(
            "Düşünüyor…",
            JarvisLoopLogic.phaseLabel(JarvisLoopLogic.toWaitReplyOk(s), tr),
        )
        assertEquals(
            "Söylüyor",
            JarvisLoopLogic.phaseLabel(JarvisLoopLogic.toSpeaking(s), tr),
        )
    }

    @Test
    fun `caption rolleri korunur`() {
        val c = JarvisLoopLogic.Caption("user", "merhaba")
        assertEquals("user", c.role)
        assertEquals("merhaba", c.text)
    }
}
