package com.hermes.mobile

import com.hermes.mobile.data.DiagLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ayıklama testleri.
 *
 * Bu kaydın tek amacı **paylaşılmak**. Bir token sızarsa bunu kimse fark
 * etmez — kayıt normal görünür, sır çoktan gitmiştir. O yüzden ayıklama,
 * sızma senaryosu senaryo test ediliyor.
 */
class DiagLogRedactTest {

    private val gizli = "hs_9f3c8a1b7e2d4056af11cc93be7710aa"

    private fun temizMi(giren: String) {
        val cikan = DiagLog.redact(giren)
        assertFalse("sır kayda sızdı: $cikan", cikan.contains(gizli))
        assertTrue("ayıklama izi yok: $cikan", cikan.contains("***"))
    }

    @Test
    fun `sorgu parametresindeki token ayiklanir`() {
        temizMi("live başarısız url=wss://h.tld/live-relay/live?token=$gizli http=-")
    }

    @Test
    fun `zincirlenmis parametreler ayiklanir`() {
        temizMi("ws://x:9170/live?token=$gizli&model=gemini-2.5&voice=Puck")
    }

    @Test
    fun `basliktaki token ayiklanir`() {
        temizMi("istek başlıkları: X-Hermes-Session-Token: $gizli")
        temizMi("Authorization: Bearer $gizli")
    }

    @Test
    fun `google anahtari ayiklanir`() {
        val key = "AIzaSyD-1234567890abcdefghijklmnopqrs"
        val out = DiagLog.redact("upstream reddetti api_key=$key")
        assertFalse(out.contains(key))
    }

    @Test
    fun `api_key ve apikey yazimlarinin ikisi de yakalanir`() {
        temizMi("?api_key=$gizli")
        temizMi("?apiKey=$gizli")
        temizMi("?key=$gizli")
    }

    @Test
    fun `zararsiz metin bozulmaz`() {
        // Fazla ayıklama kaydı okunmaz hale getirir; sınır burada.
        val duz = "ws kapandı kod=1006 sebep=- · kuyruk=3 · oturum=sess_42"
        assertTrue(DiagLog.redact(duz) == duz)
    }

    @Test
    fun `birden fazla sir ayni satirda ayiklanir`() {
        val out = DiagLog.redact("a?token=$gizli b&key=$gizli")
        assertFalse(out.contains(gizli))
        assertTrue(out.split("***").size == 3)
    }
}
