package com.hermes.mobile

import com.hermes.mobile.data.BotCard
import com.hermes.mobile.data.BotRosterStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * K18B-G1 — bot roster deposu: kalıcılık, güncelleme, çoğaltma, pin ve
 * gizlilik şeması (kişisel veri yok: kart JSON'unda gizli alan bulunmaz).
 */
class BotRosterStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(dir: File = tmp.newFolder()) = BotRosterStore(dir)

    private fun card(id: String = "b1", name: String = "Araştırmacı") = BotCard(
        id = id,
        name = name,
        emoji = "🔍",
        personality = "Meraklı, kaynak ister.",
        profile = "Yedek Bot",
        provider = "nous",
        model = "deepseek-v4",
        createdAt = 10L,
        updatedAt = 20L,
    )

    @Test
    fun `kaydet yukle ile birebir doner`() {
        val dir = tmp.newFolder()
        val s = store(dir)
        assertTrue(s.kaydet(card()))
        val liste = store(dir).yukle()  // yeni örnek = diskten okuma
        assertEquals(1, liste.size)
        assertEquals(card(), liste.first())
    }

    @Test
    fun `ayni id ile kaydet yerinde gunceller`() {
        val s = store()
        s.kaydet(card())
        assertTrue(s.kaydet(card().copy(name = "Yeni ad", updatedAt = 30L)))
        val liste = s.yukle()
        assertEquals(1, liste.size)
        assertEquals("Yeni ad", liste.first().name)
        assertEquals(30L, liste.first().updatedAt)
    }

    @Test
    fun `gecersiz kart yazilmaz fail-closed`() {
        val s = store()
        assertFalse(s.kaydet(card(name = "   ")))
        assertFalse(s.kaydet(card().copy(personality = "p".repeat(501))))
        assertEquals(0, s.yukle().size)
    }

    @Test
    fun `sil kaydi kaldirir ve yarista sessiz gecer`() {
        val s = store()
        s.kaydet(card(id = "a"))
        s.kaydet(card(id = "b", name = "İkinci"))
        s.sil("a")
        s.sil("olmayan")
        assertEquals(listOf("b"), s.yukle().map { it.id })
    }

    @Test
    fun `cogalt benzersiz adla yeni kart acar`() {
        val s = store()
        s.kaydet(card())
        val kopya = s.duplicate("b1", now = 99L)
        assertNotNull(kopya)
        assertEquals("Araştırmacı (kopya)", kopya!!.name)
        assertFalse(kopya.pinned)
        assertEquals(99L, kopya.createdAt)
        assertTrue(kopya.id != "b1")
        // İkinci çoğaltma da benzersiz ad alır.
        val kopya2 = s.duplicate("b1", now = 100L)
        assertEquals("Araştırmacı (kopya 2)", kopya2!!.name)
        assertEquals(3, s.yukle().size)
    }

    @Test
    fun `cogalt silinmis kaynakta null doner`() {
        assertNull(store().duplicate("yok", now = 1L))
    }

    @Test
    fun `pin ac kapa kaydedilir`() {
        val s = store()
        s.kaydet(card())
        s.setPinned("b1", true, now = 5L)
        assertTrue(s.find("b1")!!.pinned)
        assertEquals(5L, s.find("b1")!!.updatedAt)
        s.setPinned("b1", false, now = 6L)
        assertFalse(s.find("b1")!!.pinned)
    }

    @Test
    fun `bozuk dosya kirilmaz sonraki kaydet yeniden kurar`() {
        val dir = tmp.newFolder()
        File(dir, "bot-roster.json").writeText("{ bu json degil")
        val s = store(dir)
        assertEquals(0, s.yukle().size)      // en iyi çaba: boş liste, çökme yok
        assertTrue(s.kaydet(card()))
        assertEquals(1, s.yukle().size)      // yığın yeniden kuruldu
    }

    @Test
    fun `kart serilestirmesinde gizli alan yok kihsel veri cihazda kalir`() {
        val dir = tmp.newFolder()
        store(dir).kaydet(card())
        val metin = File(dir, "bot-roster.json").readText()
        val anahtarlar = Json.parseToJsonElement(metin).jsonObject
            .getValue("bots").let { it as kotlinx.serialization.json.JsonArray }
            .first().jsonObject.keys
        // Şema tam ve sınırlı: yalnız kullanıcı girdisi + kimlik/zaman.
        assertEquals(
            setOf("id", "name", "emoji", "personality", "profile", "provider", "model", "pinned", "createdAt", "updatedAt"),
            anahtarlar,
        )
        // Gizli veri sınıfı bu dosyada hiç yok (D-05/gizlilik kuralı).
        assertFalse(metin.contains("token"))
        assertFalse(metin.contains("ApiKey"))
        assertFalse(metin.contains("gemini"))
    }
}