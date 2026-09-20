package com.hermes.mobile

import com.hermes.mobile.data.VoiceSpeakLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-11 — seslendirme kararları: motor seçimi, markdown sadeleştirme,
 * önbellek adı, "soğuk motor" uyarısı ve durum geçişleri.
 *
 * Ses ucu ekibinin notu: ilk sentez SOĞUKKEN 173-187 sn sürer; motorlar tembel
 * açılır ve `/health` motoru ısıtmaz. Üretim önerisi: ana motor kahya, kadın
 * için kadin; chatterbox referanssızken kararsız.
 */
class VoiceSpeakLogicTest {

    private val t: (String, String) -> String = { tr, _ -> tr }

    @Test
    fun `varsayilan motor yerel`() {
        // Tur-21 gizlilik kararı: telefon verisi buluta çıkmasın → YEREL.
        assertEquals(VoiceSpeakLogic.Engine.YEREL, VoiceSpeakLogic.Engine.DEFAULT)
        assertEquals(VoiceSpeakLogic.Engine.YEREL, VoiceSpeakLogic.Engine.fromId(null))
        assertEquals(VoiceSpeakLogic.Engine.YEREL, VoiceSpeakLogic.Engine.fromId(""))
        assertEquals(VoiceSpeakLogic.Engine.YEREL, VoiceSpeakLogic.Engine.fromId("bilinmeyen"))
    }

    @Test
    fun `motor kimlikleri sozlesmeyle ayni`() {
        assertEquals(
            listOf("kahya", "chatterbox", "kadin", "yerel"),
            VoiceSpeakLogic.Engine.ids,
        )
    }

    @Test
    fun `motor adi buyuk harf ve boslukla da cozulur`() {
        assertEquals(VoiceSpeakLogic.Engine.KADIN, VoiceSpeakLogic.Engine.fromId(" KADIN "))
        assertEquals(VoiceSpeakLogic.Engine.CHATTERBOX, VoiceSpeakLogic.Engine.fromId("chatterbox"))
        assertEquals(VoiceSpeakLogic.Engine.YEREL, VoiceSpeakLogic.Engine.fromId("YEREL"))
    }

    @Test
    fun `motor secenekleri etiketli`() {
        val options = VoiceSpeakLogic.engineOptions(t)
        assertEquals(4, options.size)
        assertEquals("yerel", options[0].first)
        assertTrue(options[0].second.contains("Yerel kadın"))
    }

    @Test
    fun `motor ipucu chatterbox uyarisini soyler`() {
        assertTrue(VoiceSpeakLogic.engineHint(VoiceSpeakLogic.Engine.CHATTERBOX, t).contains("deneysel"))
        assertTrue(VoiceSpeakLogic.engineHint(VoiceSpeakLogic.Engine.KAHYA, t).contains("Ana motor"))
        assertTrue(VoiceSpeakLogic.engineHint(VoiceSpeakLogic.Engine.KADIN, t).contains("Kadın"))
        // Tur-21: yerel motor gizlilik notunu söyler (buluta gitmez + CC0 + Apache-2.0).
        val hint = VoiceSpeakLogic.engineHint(VoiceSpeakLogic.Engine.YEREL, t)
        assertTrue(hint.contains("çıkmaz") && hint.contains("CC0") && hint.contains("Apache-2.0"))
    }

    @Test
    fun `markdown isaretleri sese girmez`() {
        val md = "# Başlık\n**kalın** ve *eğik* `kod` [bağlantı](https://x.y)\n- madde"
        val plain = VoiceSpeakLogic.plainText(md)
        assertFalse(plain.contains("**"))
        assertFalse(plain.contains("`"))
        assertFalse(plain.contains("](http"))
        assertFalse(plain.startsWith("#"))
        assertTrue(plain.contains("kalın"))
        assertTrue(plain.contains("bağlantı"))
    }

    @Test
    fun `kod bloklari sesli okunmaz`() {
        val md = "Açıklama:\n```kotlin\nval x = 1\n```\nSonuç."
        val plain = VoiceSpeakLogic.plainText(md)
        assertFalse(plain.contains("val x"))
        assertTrue(plain.contains("Açıklama"))
        assertTrue(plain.contains("Sonuç"))
    }

    @Test
    fun `baslik ve alinti isaretleri temizlenir`() {
        val plain = VoiceSpeakLogic.plainText("## Madde\n> alıntı\n1. birinci")
        assertFalse(plain.contains("##"))
        assertFalse(plain.contains(">"))
        assertTrue(plain.contains("Madde"))
        assertTrue(plain.contains("alıntı"))
    }

    @Test
    fun `prepare uzun metni kirpar`() {
        val long = (1..400).joinToString(" ") { "kelime$it" }
        val out = VoiceSpeakLogic.prepare(long)!!
        assertTrue(out.length <= VoiceSpeakLogic.MAX_CHARS + 1)
        assertTrue(out.endsWith("…"))
    }

    @Test
    fun `prepare bos metinde null doner`() {
        assertNull(VoiceSpeakLogic.prepare("   \n  "))
        assertNull(VoiceSpeakLogic.prepare("```kod```"))
    }

    @Test
    fun `prepare kisa metni aynen gecirir`() {
        assertEquals("Merhaba dünya", VoiceSpeakLogic.prepare("**Merhaba** dünya"))
    }

    @Test
    fun `onbellek adi ayni metin ve motorda sabit`() {
        val a = VoiceSpeakLogic.cacheName("merhaba", VoiceSpeakLogic.Engine.KAHYA)
        val b = VoiceSpeakLogic.cacheName("merhaba", VoiceSpeakLogic.Engine.KAHYA)
        assertEquals(a, b)
        assertTrue(a.endsWith(".ogg"))
        assertTrue(a.contains("kahya"))
    }

    @Test
    fun `motor ya da metin degisince onbellek adi degisir`() {
        val kahya = VoiceSpeakLogic.cacheName("merhaba", VoiceSpeakLogic.Engine.KAHYA)
        val kadin = VoiceSpeakLogic.cacheName("merhaba", VoiceSpeakLogic.Engine.KADIN)
        val diger = VoiceSpeakLogic.cacheName("iyi akşamlar", VoiceSpeakLogic.Engine.KAHYA)
        assertNotEquals(kahya, kadin)
        assertNotEquals(kahya, diger)
    }

    @Test
    fun `ayni balona ikinci dokunus durdurur`() {
        val playing = VoiceSpeakLogic.started("a-1", cached = true)
        assertTrue(VoiceSpeakLogic.togglesOff(playing, "a-1"))
        assertFalse(VoiceSpeakLogic.togglesOff(playing, "a-2"))

        val downloading = VoiceSpeakLogic.start("a-1")
        assertTrue(VoiceSpeakLogic.togglesOff(downloading, "a-1"))
        assertFalse(VoiceSpeakLogic.togglesOff(VoiceSpeakLogic.done(), "a-1"))
    }

    @Test
    fun `soğuk motor uyarisi 8 sn sonra cikar`() {
        val early = VoiceSpeakLogic.waiting(VoiceSpeakLogic.start("a"), 3_000)
        val line = VoiceSpeakLogic.statusLine(early, t)!!
        assertFalse(line.contains("İlk yanıt"))

        val late = VoiceSpeakLogic.waiting(VoiceSpeakLogic.start("a"), 9_000)
        val cold = VoiceSpeakLogic.statusLine(late, t)!!
        assertTrue(cold.contains("İlk yanıt uzun sürebilir"))
        // tur-12b: süre bilgisi "Isıt" ipucuyla AYNI (2-3 dk, bazen 5 dk).
        assertTrue(cold.contains("2-3 dk"))
        assertTrue(cold.contains("5 dk"))
        assertEquals(8_000L, VoiceSpeakLogic.COLD_HINT_AFTER_MS)
    }

    @Test
    fun `bekleme suresi birikir`() {
        var s = VoiceSpeakLogic.start("a")
        s = VoiceSpeakLogic.waiting(s, 120)
        s = VoiceSpeakLogic.waiting(s, 240)
        assertEquals(240L, s.waitingMs)
    }

    @Test
    fun `caliyor satiri durdurmayi soyler`() {
        val line = VoiceSpeakLogic.statusLine(VoiceSpeakLogic.started("a", true), t)!!
        assertTrue(line.contains("durdur"))
    }

    @Test
    fun `idle durumda mesaj yoksa satir yok`() {
        assertNull(VoiceSpeakLogic.statusLine(VoiceSpeakLogic.done(), t))
        assertEquals("hata", VoiceSpeakLogic.statusLine(VoiceSpeakLogic.failed("hata"), t))
    }

    @Test
    fun `durum gecisleri tutarli`() {
        val s = VoiceSpeakLogic.start("k")
        assertEquals(VoiceSpeakLogic.Phase.Downloading, s.phase)
        assertTrue(s.busy)
        val p = VoiceSpeakLogic.started("k", cached = true)
        assertEquals(VoiceSpeakLogic.Phase.Playing, p.phase)
        assertTrue(p.playing)
        assertTrue(p.cached)
        assertEquals(VoiceSpeakLogic.Phase.Idle, VoiceSpeakLogic.done().phase)
    }

    @Test
    fun `motor etiketi cozulur`() {
        assertEquals("Kahya (bulut)", VoiceSpeakLogic.engineLabel(VoiceSpeakLogic.Engine.KAHYA, t))
        assertEquals("Kadın (bulut)", VoiceSpeakLogic.engineLabel(VoiceSpeakLogic.Engine.KADIN, t))
    }
}
