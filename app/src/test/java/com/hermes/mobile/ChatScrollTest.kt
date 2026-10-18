package com.hermes.mobile

import com.hermes.mobile.ui.firstUnreadRowIndex
import com.hermes.mobile.ui.newestTsOf
import com.hermes.mobile.ui.scrollButtonVisible
import com.hermes.mobile.ui.shouldPinToBottom
import com.hermes.mobile.ui.streamSignature
import com.hermes.mobile.ui.unreadRowCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ── M8 (patron eki): açılış konumu + sona git butonu — saf kararlar ──

    @Test
    fun `buton en altta degilken ve liste bos degilken gorunur`() {
        assertTrue(scrollButtonVisible(followBottom = false, rowsEmpty = false))
        assertFalse(scrollButtonVisible(followBottom = true, rowsEmpty = false))
        assertFalse(scrollButtonVisible(followBottom = false, rowsEmpty = true))
    }

    @Test
    fun `okunmamis sayaci negatif olmaz`() {
        assertEquals(3, unreadRowCount(totalRows = 10, seenRows = 7))
        assertEquals(0, unreadRowCount(totalRows = 5, seenRows = 9))
    }

    @Test
    fun `ilk okunmamis satir zamana gore bulunur`() {
        val ts = listOf(100.0, 110.0, 120.0, 130.0)
        assertEquals(2, firstUnreadRowIndex(ts, lastReadTs = 110.0))
        // Hepsi okunmuşsa hedef YOK (davranış: en altta kal).
        assertNull(firstUnreadRowIndex(ts, lastReadTs = 130.0))
        assertNull(firstUnreadRowIndex(ts, lastReadTs = 999.0))
        // Kayıt yokken karar verilmez (eski davranış: en altta).
        assertNull(firstUnreadRowIndex(ts, lastReadTs = null))
    }

    @Test
    fun `mesaj sayisi degisse de konum karari sabit kalir - satir indeksi degil damga`() {
        // Oturumda 4 mesajken okundu kaydı 110; sonra BAŞINA 2 mesaj EKLENDİ.
        // İndeks kayar (2→4) ama KARAR aynı mesaja (damga 120) çıkar —
        // patron eki kabul ölçütü: "mesaj sayısı değişince kaymaya dayanıklı".
        val tsOnce = listOf(100.0, 110.0, 120.0, 130.0)
        val tsSonra = listOf(90.0, 95.0, 100.0, 110.0, 120.0, 130.0)
        val iOnce = firstUnreadRowIndex(tsOnce, 110.0)!!
        val iSonra = firstUnreadRowIndex(tsSonra, 110.0)!!
        assertEquals(120.0, tsOnce[iOnce]!!, 0.0)
        assertEquals(120.0, tsSonra[iSonra]!!, 0.0)
    }

    @Test
    fun `damgasiz satirlar atlanir ve kayit geriye gitmez`() {
        assertEquals(1, firstUnreadRowIndex(listOf(null, 110.0, null, 130.0), 100.0))
        assertNull(firstUnreadRowIndex(listOf(null, null), 100.0))
        assertEquals(130.0, newestTsOf(listOf(110.0, 130.0), 120.0)!!, 0.0)
        // Görülen en yeni eskiyse kayıt GERİYE GİTMEZ (okunmamış korunur).
        assertEquals(120.0, newestTsOf(listOf(110.0), 120.0)!!, 0.0)
        assertNull(newestTsOf(listOf(null, null), null))
    }
}
