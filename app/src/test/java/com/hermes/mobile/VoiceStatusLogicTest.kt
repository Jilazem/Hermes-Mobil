package com.hermes.mobile

import com.hermes.mobile.data.VoiceApiEndpoints
import com.hermes.mobile.data.VoiceHealth
import com.hermes.mobile.data.VoiceSpeakLogic
import com.hermes.mobile.data.VoiceStatusLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-12 — "Ses ucu durumu" satırı ve **ısıtma** durum makinesi (saf mantık).
 *
 * Kullanıcı bildirimi: durum sabit kalıyordu, motorun yüklenip yüklenmediği
 * görünmüyordu. Bu testler iki sözü bağlar:
 *  - `/health` gövdesi **yapılandırılmış** satıra çözülür ve eksik bilgi
 *    fail-closed kapalı sayılır → 'Isıt' görünür.
 *  - Isıtma akışı (`Isıt → Isıtılıyor… (~2-3 dk) → Hazır ✓`) çift tıklama
 *    koruması, tavan (420 sn — tur-12b güvenlik payı) ve motor değişimiyle
 *    sıfırlama ile birlikte deterministik koşar (sahte saat).
 */
class VoiceStatusLogicTest {

    private val t: (String, String) -> String = { tr, _ -> tr }

    private fun health(
        ok: Boolean = true,
        stt: String = "acik",
        engines: Map<String, String> = mapOf(
            "kahya" to "kapali",
            "chatterbox" to "kapali",
            "kadin" to "kapali",
        ),
    ) = VoiceHealth(ok = ok, stt = stt, engines = engines)

    // ── Durum satırı ──────────────────────────────────────────────────

    @Test
    fun `durum satiri kullanicinin istedigi bicimde`() {
        val chips = VoiceStatusLogic.chips(health(), t)
        assertEquals(
            "Metinleştirme: açık · Kahya: kapalı · Kadın: kapalı · Chatterbox: kapalı",
            VoiceStatusLogic.line(chips, t),
        )
    }

    @Test
    fun `parca sirasi metinlestirme kahya kadin chatterbox`() {
        val chips = VoiceStatusLogic.chips(health(), t)
        assertEquals(
            listOf("Metinleştirme", "Kahya", "Kadın", "Chatterbox"),
            chips.map { it.label },
        )
    }

    @Test
    fun `acik motor yesil yolu icin isaretlenir`() {
        val chips = VoiceStatusLogic.chips(
            health(engines = mapOf("kahya" to "acik", "kadin" to "kapali")),
            t,
        )
        assertTrue(chips.first { it.label == "Metinleştirme" }.on)
        assertTrue(chips.first { it.label == "Kahya" }.on)
        assertFalse(chips.first { it.label == "Kadın" }.on)
    }

    @Test
    fun `metinlestirme kapaliysa satirda kapali yazar`() {
        val chips = VoiceStatusLogic.chips(health(stt = "kapali"), t)
        assertEquals("Metinleştirme: kapalı", VoiceStatusLogic.line(chips.take(1), t))
    }

    @Test
    fun `eksik motor anahtari bilinmiyor ve kapali sayilir`() {
        val chips = VoiceStatusLogic.chips(health(engines = emptyMap()), t)
        val kahya = chips.first { it.label == "Kahya" }
        assertFalse(kahya.known)
        assertFalse(kahya.on)
        assertEquals("bilinmiyor", VoiceStatusLogic.valueText(kahya, t))
        assertTrue(VoiceStatusLogic.warmVisible(VoiceStatusLogic.Probe(health = health(engines = emptyMap())), VoiceSpeakLogic.Engine.KAHYA))
    }

    @Test
    fun `motor anahtari buyuk harfli gelse de okunur`() {
        val h = health(engines = mapOf("KAHYA" to "ACIK"))
        assertTrue(VoiceStatusLogic.chips(h, t).first { it.label == "Kahya" }.on)
        assertTrue(VoiceStatusLogic.engineOpen(h, VoiceSpeakLogic.Engine.KAHYA))
    }

    @Test
    fun `isOn degerleri`() {
        listOf("acik", "AÇIK", "açık", "on", "hazir", "hazır", "ready").forEach {
            assertTrue("\"$it\" açık sayılmalı", VoiceStatusLogic.isOn(it))
        }
        listOf("kapali", "kapalı", "off", "", "bilinmiyor").forEach {
            assertFalse("\"$it\" kapalı sayılmalı", VoiceStatusLogic.isOn(it))
        }
        // Türkçe harf tuzağı: yerel ayara göre lowercase() farklı dönebilir.
        assertEquals("acik", VoiceStatusLogic.ascii("AÇIK"))
        assertEquals("acik", VoiceStatusLogic.ascii("açık"))
        assertEquals("hazir", VoiceStatusLogic.ascii("HAZIR"))
        assertEquals("kapali", VoiceStatusLogic.ascii(" KAPALI "))
        assertFalse(VoiceStatusLogic.isOn(null))
    }

    @Test
    fun `ingilizce etiketler de dogru`() {
        val en: (String, String) -> String = { _, e -> e }
        val chips = VoiceStatusLogic.chips(health(), en)
        assertEquals(
            "Transcription: on · Kahya: off · Female: off · Chatterbox: off",
            VoiceStatusLogic.line(chips, en),
        )
    }

    // ── Isıt görünürlüğü + ipucu ──────────────────────────────────────

    @Test
    fun `motor kapaliysa isit gorunur aciksa gorunmez`() {
        val closed = VoiceStatusLogic.Probe(health = health())
        val open = VoiceStatusLogic.Probe(health = health(engines = mapOf("kahya" to "acik")))
        assertTrue(VoiceStatusLogic.warmVisible(closed, VoiceSpeakLogic.Engine.KAHYA))
        assertFalse(VoiceStatusLogic.warmVisible(open, VoiceSpeakLogic.Engine.KAHYA))
        // Seçili motor kapalıysa (Kahya açık, Kadın kapalı) yine görünür.
        assertTrue(VoiceStatusLogic.warmVisible(open, VoiceSpeakLogic.Engine.KADIN))
    }

    @Test
    fun `uc yanit vermiyorsa isit gosterilmez`() {
        assertFalse(VoiceStatusLogic.warmVisible(null, VoiceSpeakLogic.Engine.KAHYA))
        assertFalse(
            VoiceStatusLogic.warmVisible(
                VoiceStatusLogic.Probe(error = "Ses ucuna ulaşılamıyor"),
                VoiceSpeakLogic.Engine.KAHYA,
            ),
        )
    }

    @Test
    fun `kapali motor ipucu isit onerir`() {
        val hint = VoiceStatusLogic.coldHint(t)
        assertTrue(hint.contains("Motor kapalı"))
        assertTrue(hint.contains("Isıt"))
        // tur-12b: süre bilgisi soğuk başlangıç uyarısıyla AYNI olmalı
        // ("ilk yanıt 2-3 dk sürebilir (bazen 5 dk'ya kadar)").
        assertTrue(hint.contains("2-3 dk"))
        assertTrue(hint.contains("5 dk"))
    }

    @Test
    fun `probe saglik bilgisini tasir`() {
        val h = health(engines = mapOf("kadin" to "acik"))
        val p = VoiceStatusLogic.Probe(health = h, base = "http://192.168.1.101:8174", atMs = 42L)
        assertTrue(p.ok)
        assertTrue(p.usable)
        assertEquals("acik", p.engines["kadin"])
        assertNull(p.error)
    }

    @Test
    fun `hata tasiyan probe ok degildir`() {
        val p = VoiceStatusLogic.Probe(error = "yetkisiz", atMs = 7L)
        assertFalse(p.ok)
        assertFalse(p.usable)
        assertTrue(p.engines.isEmpty())
    }

    // ── Isıtma durum makinesi ─────────────────────────────────────────

    @Test
    fun `isitma baslarken motor ve baslangic zamani kaydedilir`() {
        val s = VoiceStatusLogic.warmStart(VoiceStatusLogic.WarmState(), VoiceSpeakLogic.Engine.KAHYA, 1_000L)
        assertEquals(VoiceStatusLogic.WarmPhase.Warming, s.phase)
        assertEquals("kahya", s.engineId)
        assertEquals(1_000L, s.startedAtMs)
        assertTrue(s.busy)
        assertFalse(s.ready)
    }

    @Test
    fun `isitma surerken ikinci istek yok sayilir`() {
        val first = VoiceStatusLogic.warmStart(VoiceStatusLogic.WarmState(), VoiceSpeakLogic.Engine.KAHYA, 1_000L)
        val second = VoiceStatusLogic.warmStart(first, VoiceSpeakLogic.Engine.KAHYA, 2_000L)
        assertSame("çift tık koruması: aynı nesne dönmeli", first, second)
        assertEquals(1_000L, second.startedAtMs)
    }

    @Test
    fun `hata sonrasi yeniden isitilabilir`() {
        val warming = VoiceStatusLogic.warmStart(VoiceStatusLogic.WarmState(), VoiceSpeakLogic.Engine.KADIN, 100L)
        val failed = VoiceStatusLogic.warmFail(warming, "ulaşılamadı", 200L)
        assertEquals(VoiceStatusLogic.WarmPhase.Failed, failed.phase)
        assertEquals(100L, failed.tookMs)
        val retry = VoiceStatusLogic.warmStart(failed, VoiceSpeakLogic.Engine.KADIN, 300L)
        assertEquals(VoiceStatusLogic.WarmPhase.Warming, retry.phase)
        assertNotEquals(failed, retry)
    }

    @Test
    fun `basarili isitma hazir durumuna ve sure satirina gecer`() {
        val warming = VoiceStatusLogic.warmStart(VoiceStatusLogic.WarmState(), VoiceSpeakLogic.Engine.KAHYA, 5_000L)
        val done = VoiceStatusLogic.warmDone(warming, 42_000L, 47_000L, t)
        assertEquals(VoiceStatusLogic.WarmPhase.Ready, done.phase)
        assertEquals(42_000L, done.tookMs)
        assertEquals("kahya", done.engineId)
        assertTrue(done.message!!.contains("42 sn"))
        assertTrue(VoiceStatusLogic.warmReadyFor(done, VoiceSpeakLogic.Engine.KAHYA))
        assertFalse(VoiceStatusLogic.warmReadyFor(done, VoiceSpeakLogic.Engine.KADIN))
    }

    @Test
    fun `dugme etiketleri dort durumu ayirir`() {
        val idle = VoiceStatusLogic.WarmState()
        val warming = VoiceStatusLogic.warmStart(idle, VoiceSpeakLogic.Engine.KAHYA, 0L)
        val ready = VoiceStatusLogic.warmDone(warming, 3_000L, 3_000L, t)
        val failed = VoiceStatusLogic.warmFail(warming, "hata", 9_000L)

        assertEquals("Isıt", VoiceStatusLogic.warmLabel(idle, VoiceSpeakLogic.Engine.KAHYA, t))
        assertEquals("Isıtılıyor… (~2-3 dk)", VoiceStatusLogic.warmLabel(warming, VoiceSpeakLogic.Engine.KAHYA, t))
        assertEquals("Hazır ✓", VoiceStatusLogic.warmLabel(ready, VoiceSpeakLogic.Engine.KAHYA, t))
        // Başka motora geçilirse "Hazır ✓" o motora ait sayılmaz.
        assertEquals("Isıt", VoiceStatusLogic.warmLabel(ready, VoiceSpeakLogic.Engine.KADIN, t))
        assertEquals("Yeniden dene", VoiceStatusLogic.warmLabel(failed, VoiceSpeakLogic.Engine.KAHYA, t))
    }

    @Test
    fun `motor degisince hazir isareti sifirlanir`() {
        val warming = VoiceStatusLogic.warmStart(VoiceStatusLogic.WarmState(), VoiceSpeakLogic.Engine.KAHYA, 0L)
        val ready = VoiceStatusLogic.warmDone(warming, 1_000L, 1_000L, t)
        assertEquals(VoiceStatusLogic.WarmState(), VoiceStatusLogic.warmReset(ready, VoiceSpeakLogic.Engine.KADIN))
        assertSame(ready, VoiceStatusLogic.warmReset(ready, VoiceSpeakLogic.Engine.KAHYA))
    }

    @Test
    fun `isitma surerken motor degisse de is kaybolmaz`() {
        val warming = VoiceStatusLogic.warmStart(VoiceStatusLogic.WarmState(), VoiceSpeakLogic.Engine.KAHYA, 0L)
        assertSame(warming, VoiceStatusLogic.warmReset(warming, VoiceSpeakLogic.Engine.KADIN))
        assertTrue(warming.busy)
    }

    @Test
    fun `zaman asimi satiri tavani soyler`() {
        val msg = VoiceStatusLogic.warmTimeoutMsg(t)
        assertTrue(msg.contains("420 sn"))
        assertTrue(msg.contains("Yenile"))
    }

    @Test
    fun `saniye metni kirpilir`() {
        assertEquals("0", VoiceStatusLogic.seconds(990L))
        assertEquals("1", VoiceStatusLogic.seconds(1_900L))
        assertEquals("187", VoiceStatusLogic.seconds(187_400L))
    }

    // ── Sabitler (sözleşme) ───────────────────────────────────────────

    @Test
    fun `canli yenileme 4-5 saniye araliginda`() {
        assertTrue("yenileme aralığı 4-5 sn olmalı", VoiceStatusLogic.REFRESH_MS in 4_000L..5_000L)
    }

    @Test
    fun `isitma tavani sentez zaman asimiyla ayni 420 sn`() {
        assertEquals(VoiceApiEndpoints.SYNTH_TIMEOUT_MS, VoiceStatusLogic.WARM_TIMEOUT_MS)
        assertEquals(420_000L, VoiceStatusLogic.WARM_TIMEOUT_MS)
    }

    @Test
    fun `isitma cumlesi kisa sabit cumle`() {
        assertEquals("Merhaba, sesli asistan hazır!", VoiceStatusLogic.WARM_SENTENCE)
        assertTrue(VoiceStatusLogic.WARM_SENTENCE.length <= 40)
    }
}
