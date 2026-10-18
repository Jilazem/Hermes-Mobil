package com.hermes.mobile

import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.BotCard
import com.hermes.mobile.data.ProfilYedekBayraklari
import com.hermes.mobile.data.SavedPrompt
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.data.YEDEK_SCHEMA_VERSION
import com.hermes.mobile.data.YedekDosya
import com.hermes.mobile.data.YedekMantik
import com.hermes.mobile.data.YedekModu
import com.hermes.mobile.data.YedekOkuma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K22 — kişisel veri yedeği: gizlilik (token yok), roundtrip, çakışma
 * politikaları (birleştir/üzerine-yaz) ve bozuk dosya dayanıklılığı.
 * Kabul maddeleri tek tek burada kilitlenir.
 */
class YedekMantikTest {

    private val sifir = "GIZLI-TOKEN-123"

    private fun profil(id: String = "p1") = ServerProfile(
        id = id,
        name = "Hermes (ev)",
        baseUrl = "http://192.168.1.101:9150",
        token = sifir,
        note = "ev sunucusu",
        remoteUrl = "https://hermes.example.keenetic.pro",
        geminiApiKey = "GIZLI-GEMINI-456",
    )

    private fun bot(id: String = "b1", ad: String = "Araştırmacı") = BotCard(
        id = id, name = ad, emoji = "🔍", personality = "Meraklı",
        profile = "default", provider = "nous", model = "deepseek-v4",
    )

    private fun prompt(id: String = "pr1") = SavedPrompt(id = id, label = "Günlük özet", text = "bugünü özetle")

    private fun yedek(ayarlar: AppSettings = AppSettings(emaToken = sifir)) = YedekDosya(
        schemaVersion = YEDEK_SCHEMA_VERSION,
        exportedAt = 1000L,
        botlar = listOf(bot()),
        promptlar = listOf(prompt()),
        profiller = listOf(profil()),
        aktifProfilId = "p1",
        oturumBayraklari = listOf(
            ProfilYedekBayraklari("p1", SessionFlags(hidden = setOf("s1"), archived = setOf("s2"))),
        ),
        ayarlar = ayarlar,
    )

    // ── KABUL (1): export dosyası oluşur, schemaVersion'lu, token İÇERMEZ ──

    @Test
    fun `serialize gizli tokenlari icermmez ve gizlilik notu tasir`() {
        val metin = YedekMantik.serialize(yedek())
        assertFalse("Token değeri yedek metnine sızdı", metin.contains(sifir))
        assertFalse("Gemini anahtarı sızdı", metin.contains("GIZLI-GEMINI-456"))
        assertTrue("Gizlilik notu yok", metin.contains("token: yok"))
        // Alanlar yapısal olarak da boş — sadece metin araması değil.
        val okunan = YedekMantik.parse(metin)
        assertTrue(okunan is YedekOkuma.Tamam)
        val d = (okunan as YedekOkuma.Tamam).dosya
        assertEquals("", d.profiller.single().token)
        assertEquals("", d.profiller.single().geminiApiKey)
        assertEquals("", d.ayarlar.emaToken)
        assertEquals(YEDEK_SCHEMA_VERSION, d.schemaVersion)
    }

    @Test
    fun `serialize parse roundtrip birebir doner`() {
        val orijinal = yedek()
        val okunan = YedekMantik.parse(YedekMantik.serialize(orijinal))
        assertTrue(okunan is YedekOkuma.Tamam)
        val d = (okunan as YedekOkuma.Tamam).dosya
        assertEquals(orijinal.botlar, d.botlar)
        assertEquals(orijinal.promptlar, d.promptlar)
        // Sırlar gizsizleştirilmiş haliyle karşılaştırılır.
        assertEquals(YedekMantik.gizsizlestir(orijinal).profiller, d.profiller)
        assertEquals(orijinal.oturumBayraklari, d.oturumBayraklari)
        assertEquals(orijinal.aktifProfilId, d.aktifProfilId)
    }

    // ── KABUL (3): birleştir / üzerine-yaz ikisi de çalışır ──

    @Test
    fun `birlestir cihazdaki kayitlari korur eksikleri ekler`() {
        val mevcut = yedek().copy(
            botlar = listOf(bot("b1", "Mevcut bot")),
            promptlar = listOf(prompt("pr1")),
            oturumBayraklari = listOf(
                ProfilYedekBayraklari("p1", SessionFlags(hidden = setOf("x1"))),
            ),
            ayarlar = AppSettings(emaToken = "CIHAZ-TOKEN"),
            aktifProfilId = "p1",
        )
        val gelen = yedek().copy(
            botlar = listOf(bot("b1", "Yedek adı"), bot("b2", "Yeni bot")),
            promptlar = listOf(prompt("pr2")),
            oturumBayraklari = listOf(
                ProfilYedekBayraklari("p1", SessionFlags(hidden = setOf("s1"), archived = setOf("s2"))),
            ),
            aktifProfilId = "baska",
        )
        val hedef = YedekMantik.birlestir(mevcut, gelen)
        // Aynı id'de cihaz kazanır (token kaybını önlemek için).
        assertEquals("Mevcut bot", hedef.botlar.first { it.id == "b1" }.name)
        // Yedekte olup cihazda olmayanlar eklenir.
        assertEquals(2, hedef.botlar.size)
        assertEquals(2, hedef.promptlar.size)
        // Bayraklar küme birleşimi.
        val bayrak = hedef.oturumBayraklari.first { it.profileId == "p1" }.flags
        assertEquals(setOf("x1", "s1"), bayrak.hidden)
        assertEquals(setOf("s2"), bayrak.archived)
        // Cihazın aktif profili ve EMA token'ı korunur.
        assertEquals("p1", hedef.aktifProfilId)
        assertEquals("CIHAZ-TOKEN", hedef.ayarlar.emaToken)
    }

    @Test
    fun `birlestir ayni id profilinde cihaz tokenini korur`() {
        val mevcut = yedek() // p1 token'lı
        val gelen = yedek().copy(profiller = listOf(profil("p1").copy(token = "", note = "yedekten")))
        val hedef = YedekMantik.birlestir(mevcut, gelen)
        assertEquals(sifir, hedef.profiller.single().token)
    }

    @Test
    fun `uzerineYaz yedegi oldugu gibi uygular tokenlar boss kalir`() {
        val mevcut = yedek().copy(
            botlar = listOf(bot("eski", "Eski bot")),
            ayarlar = AppSettings(emaToken = "CIHAZ-TOKEN"),
        )
        val hedef = YedekMantik.uzerineYaz(mevcut, yedek())
        assertEquals(1, hedef.botlar.size)
        assertEquals("b1", hedef.botlar.single().id)
        assertEquals("", hedef.profiller.single().token)
        assertEquals("", hedef.profiller.single().geminiApiKey)
        assertEquals("", hedef.ayarlar.emaToken)
        assertNotEquals(mevcut.botlar, hedef.botlar)
    }

    // ── KABUL (4): bozuk dosyada çökme yok, anlaşılır hata var ──

    @Test
    fun `bos ve bozuk metin hataya duser`() {
        assertTrue(YedekMantik.parse("") is YedekOkuma.Hata)
        assertTrue(YedekMantik.parse("   ") is YedekOkuma.Hata)
        assertTrue(YedekMantik.parse("{bozuk json") is YedekOkuma.Hata)
        assertTrue(YedekMantik.parse("null") is YedekOkuma.Hata)
        // Geçerli JSON ama yedek şeması değil (ör. tema dışa aktarımı).
        assertTrue(YedekMantik.parse("""{"id":"tema","label":"X"}""") is YedekOkuma.Hata)
    }

    @Test
    fun `schemaVersion eksikse hata yenisese anlasilir mesaj`() {
        // schemaVersion alanı olmayan eski dosya.
        val eski = """{"exportedAt":1}"""
        assertTrue(YedekMantik.parse(eski) is YedekOkuma.Hata)
        // Daha yeni sürüm.
        val yeni = YedekMantik.serialize(yedek().copy(schemaVersion = YEDEK_SCHEMA_VERSION + 1))
        val okuma = YedekMantik.parse(yeni)
        assertTrue(okuma is YedekOkuma.Hata)
        assertTrue((okuma as YedekOkuma.Hata).mesaj.contains("daha yeni"))
    }

    // ── Önizleme sayıları ──

    @Test
    fun `sayim dogru sayilar verir`() {
        val s = YedekMantik.sayim(yedek())
        assertEquals(1, s.botSayisi)
        assertEquals(1, s.promptSayisi)
        assertEquals(1, s.profilSayisi)
        assertEquals(1, s.bayrakKumesiSayisi)
        assertEquals(0, s.temaSayisi)
        assertNotNull(s)
    }

    // ── YedekModu sözleşmesi: iki mod ayrı sonuç üretir ──

    @Test
    fun `birlestir ve uzerineYaz farkli sonuclar uretir`() {
        val mevcut = yedek().copy(botlar = listOf(bot("eski", "Eski bot")))
        val birlesik = YedekMantik.birlestir(mevcut, yedek())
        val uzerine = YedekMantik.uzerineYaz(mevcut, yedek())
        assertEquals(2, birlesik.botlar.size)   // eski + yedek
        assertEquals(1, uzerine.botlar.size)    // yalnız yedek
        assertEquals(YedekModu.Birlestir, YedekModu.valueOf("Birlestir"))
        assertEquals(YedekModu.UzerineYaz, YedekModu.valueOf("UzerineYaz"))
    }
}
