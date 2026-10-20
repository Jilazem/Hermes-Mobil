package com.hermes.mobile

import com.hermes.mobile.data.CarConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * V5-3: araç sohbeti akışı (Android Auto bildirim-mesajlaşma) saf katman testi.
 *
 * Kök neden kuralı: AA yalnız aktif oturumu sırasında gelen bildirimlerin
 * mesaj geçmişini gösterir — konuşma birikmeli ve her mesajda güncellenmeli.
 */
class CarConversationTest {

    @Before
    fun reset() {
        CarConversation.close("p1", "s1")
        CarConversation.close("p1", "s2")
    }

    @Test
    fun `start sohbet gecmisini tasiyor ve konusmayi aktif yapiyor`() {
        val history = listOf(
            CarConversation.Msg(true, "merhaba", 1000),
            CarConversation.Msg(false, "selam Gökhan", 2000),
        )
        val msgs = CarConversation.start("p1", "s1", "Deneme", history)
        assertEquals(2, msgs.size)
        assertTrue(msgs[0].isUser)
        assertEquals("selam Gökhan", msgs[1].text)
        assertTrue(CarConversation.isActive("p1", "s1"))
        assertEquals("Deneme", CarConversation.currentTitle())
    }

    @Test
    fun `akisi 25 mesajla sinirli - eskileri dusuyor`() {
        val history = (1..30).map { CarConversation.Msg(false, "m$it", it.toLong()) }
        val msgs = CarConversation.start("p1", "s1", null, history)
        assertEquals(CarConversation.MAX_MESSAGES, msgs.size)
        assertEquals("m6", msgs.first().text)
        assertEquals("m30", msgs.last().text)
    }

    @Test
    fun `aktif konusmaye mesaj akisi duser ve birikir`() {
        CarConversation.start("p1", "s1", "T", listOf(CarConversation.Msg(false, "ilk", 1)))
        val after = CarConversation.append("p1", "s1", CarConversation.Msg(true, "soru", 2))
        assertEquals(2, after!!.size)
        val after2 = CarConversation.append("p1", "s1", CarConversation.Msg(false, "yanit", 3))
        assertEquals(3, after2!!.size)
        assertTrue(after2.last().isUser.not())
    }

    @Test
    fun `farkli oturumun mesaji konusmeye dusmez`() {
        CarConversation.start("p1", "s1", "T", emptyList())
        assertNull(CarConversation.append("p1", "s2", CarConversation.Msg(false, "x", 1)))
        assertFalse(CarConversation.isActive("p1", "s2"))
    }

    @Test
    fun `bos mesaj eklenmez`() {
        CarConversation.start("p1", "s1", "T", emptyList())
        val msgs = CarConversation.append("p1", "s1", CarConversation.Msg(false, "   ", 1))
        assertEquals(0, msgs!!.size)
    }

    @Test
    fun `uzun metin 4000 karaktere kirpiliyor`() {
        CarConversation.start("p1", "s1", "T", emptyList())
        val long = "a".repeat(CarConversation.MAX_TEXT + 500)
        val msgs = CarConversation.append("p1", "s1", CarConversation.Msg(false, long, 1))
        assertEquals(CarConversation.MAX_TEXT, msgs!!.single().text.length)
    }

    @Test
    fun `baslik kirpiliyor ve konusma kapaninca durum sifirlaniyor`() {
        CarConversation.start("p1", "s1", "  " + "b".repeat(120), listOf(CarConversation.Msg(false, "m", 1)))
        assertEquals(60, CarConversation.currentTitle().length)
        CarConversation.close("p1", "s1")
        assertFalse(CarConversation.isActive("p1", "s1"))
        assertEquals(0, CarConversation.messages().size)
        assertEquals("", CarConversation.currentTitle())
    }

    @Test
    fun `kapanan konusmaye mesaj dussmez - yeni konusma gerekir`() {
        CarConversation.start("p1", "s1", "T", emptyList())
        CarConversation.close("p1", "s1")
        assertNull(CarConversation.append("p1", "s1", CarConversation.Msg(false, "x", 1)))
    }

    @Test
    fun `timeOf saniye ve milisaniyeyi ayirt ediyor`() {
        assertEquals(1_700_000_000_000L, CarConversation.timeOf(1_700_000_000.0, 0L))
        assertEquals(1_700_000_000_123L, CarConversation.timeOf(1_700_000_000_123.0, 0L))
        assertEquals(42L, CarConversation.timeOf(null, 42L))
    }

    @Test
    fun `anahtar profil ve oturumdan turetiliyor`() {
        assertEquals("p/s", CarConversation.keyOf("p", "s"))
        assertEquals("/", CarConversation.keyOf(null, null))
    }
}