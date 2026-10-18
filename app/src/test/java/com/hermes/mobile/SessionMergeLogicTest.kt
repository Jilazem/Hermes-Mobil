package com.hermes.mobile

import com.hermes.mobile.data.MergeKaynak
import com.hermes.mobile.data.MergeSonuc
import com.hermes.mobile.data.SessionMergeState
import com.hermes.mobile.data.kaynakOzeti
import com.hermes.mobile.data.mergeGovdesi
import com.hermes.mobile.data.mergeSecilebilirMi
import com.hermes.mobile.data.parseMergeSonuc
import com.hermes.mobile.data.temizBirlestirBasligi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K13c: oturum birleştirme mantığı — istek gövdesi, yanıt çözümü, seçim kuralları.
 *
 * En önemli sözleşme: birleştirme SUNUCUDA yapılır; istemciye yalnız üst veri
 * (session_id/başlık/kaynak özeti/sayaçlar) döner. Bu testlerde birleşik
 * İÇERİĞİ taşıyan hiçbir alan yoktur — telefona içerik inmez.
 */
class SessionMergeLogicTest {

    private val ornekYanit = """
        {
          "ok": true,
          "session_id": "local-abc123",
          "title": "İlk iş (+1 oturum birleşik)",
          "sources": [
            {"sid": "sess-aaa", "baslik": "İlk iş", "ilet": 3},
            {"sid": "sess-bbb", "baslik": "Aynı işin devamı", "ilet": 2}
          ],
          "source_message_count": 5,
          "merged_message_count": 4,
          "duplicates_dropped": 1,
          "imported": 1
        }
    """.trimIndent()

    @Test
    fun `yanıt üst veriye çözülür`() {
        val s = parseMergeSonuc(ornekYanit)!!
        assertEquals("local-abc123", s.sessionId)
        assertEquals("İlk iş (+1 oturum birleşik)", s.title)
        assertEquals(2, s.kaynaklar.size)
        assertEquals("sess-aaa", s.kaynaklar[0].sid)
        assertEquals("İlk iş", s.kaynaklar[0].baslik)
        assertEquals(3, s.kaynaklar[0].ilet)
        assertEquals(5, s.kaynakIlet)
        assertEquals(4, s.birlesikIlet)
        assertEquals(1, s.atilan)
    }

    @Test
    fun `bozuk ve eksik yanıt toler edilir`() {
        assertNull(parseMergeSonuc(""))
        assertNull(parseMergeSonuc("404 Not Found"))
        assertNull(parseMergeSonuc("""{"title":"id yok"}"""))
        // session_id null alanı "null" metni sanılmaz.
        assertNull(parseMergeSonuc("""{"session_id": null, "title": "t"}"""))
    }

    @Test
    fun `id null alanı literal null dizi olarak okunmaz`() {
        val s = parseMergeSonuc(
            """{"session_id":"x","title":"t","sources":[{"sid":null,"baslik":"b","ilet":1}]}""",
        )!!
        // sid'siz kaynak kaydı düşer — sid'siz kaynak birleştirilemez.
        assertTrue(s.kaynaklar.isEmpty())
    }

    @Test
    fun `en az iki farklı oturum gerekir`() {
        assertFalse(mergeSecilebilirMi(emptyList()))
        assertFalse(mergeSecilebilirMi(listOf("a")))
        assertFalse(mergeSecilebilirMi(listOf("a", "a")))
        assertTrue(mergeSecilebilirMi(listOf("a", "b")))
        assertFalse(mergeSecilebilirMi(listOf("a", " ", "")))
    }

    @Test
    fun `başlık temizliği — boş null, uzun kırpar`() {
        assertNull(temizBirlestirBasligi(null))
        assertNull(temizBirlestirBasligi("   "))
        assertNull(temizBirlestirBasligi(""))
        assertEquals("Birleşik", temizBirlestirBasligi("  Birleşik  "))
        assertEquals(80, temizBirlestirBasligi("x".repeat(120))!!.length)
    }

    @Test
    fun `istek gövdesi id'leri güvenli tırnaklar ve başlığı ekler`() {
        assertEquals("""{"ids":["a","b"]}""", mergeGovdesi(listOf("a", "b"), null))
        // Mükerrer ve boş id'ler gövdeye girmez.
        assertEquals("""{"ids":["a","b"]}""", mergeGovdesi(listOf("a", "b", "a", " "), null))
        // Tırnak içi kaçış: id'de tırnak olsa bile gövde geçerli JSON kalır.
        assertEquals("""{"ids":["a\"b"]}""", mergeGovdesi(listOf("""a"b"""), null))
        assertEquals("""{"ids":["a","b"],"title":"Seç"}""", mergeGovdesi(listOf("a", "b"), " Seç "))
        // Uzun başlık 80'e kırpar.
        assertEquals(80, Regex("\"title\":\"(.{80})\"").find(mergeGovdesi(listOf("a", "b"), "y".repeat(120)))!!.groupValues[1].length)
    }

    @Test
    fun `kaynak özeti okunabilir etiket verir`() {
        val s = MergeSonuc(
            sessionId = "x",
            title = "t",
            kaynaklar = listOf(MergeKaynak("a", "İlk iş", 3), MergeKaynak("b", "Devamı", 2)),
        )
        assertEquals("İlk iş, Devamı", kaynakOzeti(s))
        val bos = MergeSonuc(sessionId = "x", title = "t")
        assertEquals("0 kaynak", kaynakOzeti(bos))
    }

    @Test
    fun `durum kutusu geçişleri`() {
        val bos = SessionMergeState()
        assertFalse(bos.calisiyor)
        assertNull(bos.sonuc)
        assertNull(bos.hata)
        val calisiyor = SessionMergeState(calisiyor = true)
        assertTrue(calisiyor.calisiyor)
        val sonuc = SessionMergeState(sonuc = MergeSonuc("x", "t"))
        assertNull(sonuc.hata)
        val hata = SessionMergeState(hata = "Birleştirilemedi: X")
        assertNull(hata.sonuc)
    }
}
