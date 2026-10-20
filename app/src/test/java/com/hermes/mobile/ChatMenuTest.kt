package com.hermes.mobile

import com.hermes.mobile.ui.BubbleAction
import com.hermes.mobile.ui.ChatMenuAction
import com.hermes.mobile.ui.chatMenuActions
import com.hermes.mobile.ui.interventionSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KALAN-2: açık sohbetin taşma menüsü.
 *
 * Tur-4'te sohbet üst şeridi "tek satır konu + ⋯" hâline geldi ama ⋯ yalnız
 * model seçicisini açıyordu; ayrıca `onOpenReasoning` parametresi ChatHeader'a
 * geçiyor ve HİÇ kullanılmıyordu (düşünme panosuna sohbetten ulaşmak imkânsız).
 * Yeni sözleşme: model + düşünme her zaman; çalışan ajanda müdahale + durdurma.
 */
class ChatMenuTest {

    @Test
    fun `bosta menude model ve dusunme var`() {
        assertEquals(listOf(ChatMenuAction.Model, ChatMenuAction.Reasoning), chatMenuActions(agentBusy = false))
    }

    @Test
    fun `calisan ajanda mudahale ve durdurma eklenir`() {
        val items = chatMenuActions(agentBusy = true)
        assertTrue(items.contains(ChatMenuAction.Intervene))
        assertTrue(items.contains(ChatMenuAction.Stop))
        assertEquals("boşta durdurma anlamsız — menü kısa kalır", 2, chatMenuActions(false).size)
    }

    @Test
    fun `durdurma her zaman en sonda`() {
        assertEquals(ChatMenuAction.Stop, chatMenuActions(agentBusy = true).last())
        assertFalse(chatMenuActions(agentBusy = false).contains(ChatMenuAction.Stop))
    }

    // ── K24: Çatalla ────────────────────────────────────────────────

    @Test
    fun `k24 calisan ajanda catalla gorunur`() {
        assertTrue(chatMenuActions(agentBusy = true).contains(ChatMenuAction.Fork))
    }

    @Test
    fun `k24 bosta catalla yok — sakli oturum yoksa`() {
        assertFalse(chatMenuActions(agentBusy = false).contains(ChatMenuAction.Fork))
    }

    @Test
    fun `v51b bosta sakli mac oturumu catallanabilir`() {
        // V5-1B: pasif (Mac'te açılmış) oturum session.branch_stored ile
        // çatallanır; menüde boşta da görünmeli.
        val items = chatMenuActions(agentBusy = false, canFork = true)
        assertTrue(items.contains(ChatMenuAction.Fork))
        assertEquals("boşta müdahale/durdurma yok", true, !items.contains(ChatMenuAction.Stop))
    }

    @Test
    fun `k24 catalla durdurmadan once gelir`() {
        val items = chatMenuActions(agentBusy = true)
        assertTrue(items.indexOf(ChatMenuAction.Fork) < items.indexOf(ChatMenuAction.Stop))
    }

    @Test
    fun `mudahale sonucu sessiz kalmaz`() {
        val ok = interventionNotice(InterventionKind.Redirect, "queued")
        val ekle = interventionNotice(InterventionKind.Add, "queued")
        val hata = interventionNotice(InterventionKind.Add, "rejected")
        assertTrue(ok.contains("yönlendirildi"))
        assertTrue(ekle.contains("iletil"))
        assertTrue("başarısızlık kullanıcıya söylenir", hata.contains("rejected"))
        assertFalse("iki müdahale türü aynı metni paylaşmamalı", ok == ekle)
    }

    @Test
    fun `mudahale diyalogu acik sohbetin kimligini tasir`() {
        val state = ChatState(sessionId = "abc123", topic = "Rapor özeti")
        val live = interventionSession(state)
        assertEquals("abc123", live.id)
        assertEquals("steer/redirect süreç içi kimliği bekler", "abc123", live.dbId)
        assertEquals("Rapor özeti", live.title)
    }

    @Test
    fun `pr93508 balon menusunde kopyala eylemi var`() {
        // PR 93508: Copy/More erişimi — uzun basma menüsünde Kopyala olmalı.
        assertTrue(BubbleAction.entries.contains(BubbleAction.Copy))
    }
}
