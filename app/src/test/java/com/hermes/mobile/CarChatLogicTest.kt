package com.hermes.mobile

import com.hermes.mobile.car.CarChatLogic
import com.hermes.mobile.data.SessionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V5-3 — araç ekranı mesaj akışı kuralları.
 *
 * Kabul: Android Auto'da sohbet dökümü görünür; sistem/tool kayıtları ve boş
 * içerik listeyi kirletmez; uzun mesaj tek satıra iner; sıra bozulmaz.
 */
class CarChatLogicTest {

    private fun user(text: String) = SessionMessage(role = "user", content = text)
    private fun assistant(text: String?) = SessionMessage(role = "assistant", content = text)

    @Test fun `kullanici ve asistan turlari sirayla listelenir`() {
        val rows = CarChatLogic.rows(listOf(user("Merhaba"), assistant("Selam, buyur")))
        assertEquals(2, rows.size)
        assertEquals("Sen", rows[0].sender)
        assertEquals("Merhaba", rows[0].snippet)
        assertEquals("Hermes", rows[1].sender)
        assertTrue(rows[0].isUser)
        assertFalse(rows[1].isUser)
    }

    @Test fun `sistem ve tool kayitlari listeye girmez`() {
        val rows = CarChatLogic.rows(
            listOf(
                SessionMessage(role = "system", content = "Sistem promptu"),
                SessionMessage(role = "tool", content = "araç çıktısı", toolName = "shell"),
                user("gerçek soru"),
            )
        )
        assertEquals(1, rows.size)
        assertEquals("gerçek soru", rows[0].fullText)
    }

    @Test fun `bos veya null icerik atlanir`() {
        val rows = CarChatLogic.rows(listOf(assistant(null), assistant("   "), user("dolu")))
        assertEquals(1, rows.size)
    }

    @Test fun `en yeni mesajlar korunur ve sira degismez`() {
        val many = (1..50).map { user("m$it") }
        val rows = CarChatLogic.rows(many, limit = 30)
        assertEquals(30, rows.size)
        assertEquals("m21", rows.first().fullText)
        assertEquals("m50", rows.last().fullText)
    }

    @Test fun `limit sifir veya negatifse bos liste`() {
        assertEquals(0, CarChatLogic.rows(listOf(user("a")), limit = 0).size)
        assertEquals(0, CarChatLogic.rows(listOf(user("a")), limit = -3).size)
    }

    @Test fun `cok satirli metin tek satira iner`() {
        val s = CarChatLogic.snippet("birinci satır\n\nikinci   satır\tüçüncü")
        assertEquals("birinci satır ikinci satır üçüncü", s)
    }

    @Test fun `uzun metin kelime ortasinda kesilmez ve ellipsis alir`() {
        val text = (1..60).joinToString(" ") { "kelime$it" }
        val s = CarChatLogic.snippet(text)
        assertTrue(s.endsWith("…"))
        assertTrue(s.length <= CarChatLogic.SNIPPET_CHARS + 1)
        assertFalse(s.substringBefore("…").endsWith("kelim"))
        assertTrue(text.startsWith(s.removeSuffix("…").trimEnd()))
    }

    @Test fun `tam metin detay icin kirpilmadan tasinir`() {
        val long = "x".repeat(500)
        val rows = CarChatLogic.rows(listOf(assistant(long)))
        assertEquals(500, rows[0].fullText.length)
        assertTrue(rows[0].snippet.length < 200)
    }

    @Test fun `bos durum metinleri durust`() {
        assertEquals("Mesajlar yükleniyor…", CarChatLogic.emptyText(loading = true, error = null))
        assertEquals("ağ yok", CarChatLogic.emptyText(loading = false, error = "ağ yok"))
        assertEquals("Bu oturumda okunacak mesaj yok", CarChatLogic.emptyText(loading = false, error = null))
    }

    @Test fun `baslik kimlikle ayniysa sohbet yazilir`() {
        assertEquals("Sohbet", CarChatLogic.title(null, "abc"))
        assertEquals("Sohbet", CarChatLogic.title("abc", "abc"))
        assertEquals("Sohbet", CarChatLogic.title("  ", "abc"))
        assertEquals("Dava özeti", CarChatLogic.title("Dava özeti", "abc"))
    }

    @Test fun `baslik altmis karakterle sinirli`() {
        val rows = CarChatLogic.rows(listOf(user("a")))
        assertEquals(1, rows.size)
        assertEquals(60, CarChatLogic.title("b".repeat(200), "abc").length)
    }
}
