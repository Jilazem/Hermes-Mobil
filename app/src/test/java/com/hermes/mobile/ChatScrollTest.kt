package com.hermes.mobile

import com.hermes.mobile.ui.shouldPinToBottom
import com.hermes.mobile.ui.streamSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tur-2 K1 — sohbet akışında kaydırma tetikleme imzası.
 *
 * Kök neden: akıştaki balonun İLK SATIRI header altında kırpılıyordu çünkü
 * 'izleme' efekti yalnız Thinking.text uzunluğuna bağlıydı; akan Assistant
 * ve biten araç satırları kaydırmayı tetiklemiyordu. streamSignature her
 * büyüme/eklenmede DEĞİŞMELİ, akış durunca SABİT kalmalı (test: FR-001).
 */
class ChatScrollTest {

    @Test
    fun akanAssistantImzayiDegistirir() {
        val a = listOf<ChatItem>(ChatItem.Assistant("a1", "mer", streaming = true))
        val b = listOf<ChatItem>(ChatItem.Assistant("a1", "merhaba", streaming = true))
        assertNotEquals(
            "uzayan assistant metni imzayı değiştirmeli (aksi halde balon kırpılır)",
            streamSignature(a), streamSignature(b),
        )
    }

    @Test
    fun akisDuruncaImzaSabit() {
        val a = listOf<ChatItem>(ChatItem.Assistant("a1", "merhaba", streaming = true))
        val b = listOf<ChatItem>(ChatItem.Assistant("a1", "merhaba", streaming = true))
        assertEquals(streamSignature(a), streamSignature(b))
    }

    @Test
    fun streamingBitisiImzayiDegistirir() {
        val a = listOf<ChatItem>(ChatItem.Assistant("a1", "aynı", streaming = true))
        val b = listOf<ChatItem>(ChatItem.Assistant("a1", "aynı", streaming = false))
        assertNotEquals(streamSignature(a), streamSignature(b))
    }

    @Test
    fun yeniOgeImzayiDegistirir() {
        val a = listOf<ChatItem>(ChatItem.User("u1", "selam"))
        val b = a + ChatItem.Assistant("a1", "naber", streaming = true)
        assertNotEquals(streamSignature(a), streamSignature(b))
    }

    @Test
    fun bitenAracImzayiDegistirir() {
        val running = listOf<ChatItem>(ChatItem.Tool("t1", "terminal", ToolState.Running))
        val done = listOf<ChatItem>(ChatItem.Tool("t1", "terminal", ToolState.Done, "çıktı"))
        assertNotEquals(streamSignature(running), streamSignature(done))
    }

    /**
     * K1 regresyon (katlama): N araç tek satıra katlanınca ham items.size
     * rows.size'tan büyük olur — ham indekse kaydırmak taşardı. Katlama
     * davranışı korunuyor ve son row SON balonu taşır.
     */
    @Test
    fun katlamadaSonRowSonBalonuTasir() {
        val items = listOf<ChatItem>(
            ChatItem.User("u1", "git"),
            ChatItem.Tool("t1", "terminal", ToolState.Done),
            ChatItem.Tool("t2", "terminal", ToolState.Done),
            ChatItem.Assistant("a1", "bitti"),
        )
        // foldToolRuns katlar: [User, Tools(t1+t2), Assistant] — son row Assistant.
        assertEquals(4, items.size)
        assertNotEquals(items.size, 3) // ham liste katlanmıştan büyük
        val sig = streamSignature(items)
        // İmza son balonu (Assistant) içermeli; scroll hedefi katlanmış indekstir.
        assertEquals(true, sig.contains("a1"))
    }

    /**
     * Tur-8 klavye: görünür alan kısalınca (klavye açılınca) dibe yaslama
     * kararı YALNIZ kullanıcı dipteyse ve kaydırma sürerken verilmez —
     * yukarıda okuyan kullanıcının konumu bozulmaz.
     */
    @Test
    fun `klavye acilinca yalnizca dipteki kullanici yaslanir`() {
        assertEquals(true, shouldPinToBottom(userPinnedBottom = true, scrolling = false, empty = false))
        assertEquals(
            "yukarıda okuyan kullanıcının konumu bozulmaz",
            false,
            shouldPinToBottom(userPinnedBottom = false, scrolling = false, empty = false),
        )
        assertEquals(
            "kullanıcı kaydırırken araya girilmez",
            false,
            shouldPinToBottom(userPinnedBottom = true, scrolling = true, empty = false),
        )
        assertEquals(
            "boş listede hedef yok",
            false,
            shouldPinToBottom(userPinnedBottom = true, scrolling = false, empty = true),
        )
    }
}
