package com.hermes.mobile

import com.hermes.mobile.data.ForkedSession
import com.hermes.mobile.data.GatewayWsClient
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K24 — Çalışan oturumda çatallama (fork) + yeni direktif (steer).
 *
 * Gateway protokolü araştırması (tui_gateway/contracts/sessions.py):
 * `session.branch` canlı oturumu o ana kadarki bağlamıyla YENİ saklı çocuğa
 * dallandırır; orijinal etkilenmez. İstemci bu gerçek ucu kullanır — protokol
 * mesajı UYDURULMADI. Aşağıdaki "mock gateway" testleri sunucunun belgeli
 * yanıt şemasını (`SessionBranchResult`) ayrıştırıcıya verir.
 */
class ForkSteerK24Test {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** tui_gateway SessionBranchResult şeması — gerçek gateway çıktısı birebir alanlar. */
    private fun branchResultJson(extra: String = "") = json.parseToJsonElement(
        """
        {
          "session_id": "a1b2c3d4",
          "stored_session_id": "2026-10-18/fork-child",
          "title": "Rapor özeti (branch)",
          "parent": "2026-10-18/parent",
          "message_count": 7,
          "messages": [],
          "info": {"session_id": "a1b2c3d4", "status": "idle"}
          $extra
        }
        """.trimIndent(),
    )

    // ── fork: mock gateway yanıtı ───────────────────────────────────

    @Test
    fun `k24 fork yaniti tam alanlarla ayristirilir`() {
        val fork = GatewayWsClient.parseForkResponse(branchResultJson())
        assertNotNull(fork)
        assertEquals("a1b2c3d4", fork!!.sessionId)
        assertEquals("2026-10-18/fork-child", fork.storedSessionId)
        assertEquals("Rapor özeti (branch)", fork.title)
        assertEquals("2026-10-18/parent", fork.parent)
        assertEquals(7, fork.messageCount)
    }

    @Test
    fun `k24 fork yanitinda bilinmeyen alanlar yok sayilir`() {
        val fork = GatewayWsClient.parseForkResponse(branchResultJson(extra = ",\"gelecek_alan\": true"))
        assertNotNull(fork)
        assertEquals("a1b2c3d4", fork!!.sessionId)
    }

    @Test
    fun `k24 fork yanitisiz ya da session_idsiz cevap null doner`() {
        assertNull(GatewayWsClient.parseForkResponse(null))
        assertNull(GatewayWsClient.parseForkResponse(json.parseToJsonElement("{}")))
    }

    @Test
    fun `k24 fork child orijinalin cocugu olarak isaretlenir`() {
        val fork = GatewayWsClient.parseForkResponse(branchResultJson())!!
        assertEquals("çatal ebeveyne bağlanmalı", fork.parent, "2026-10-18/parent")
        assertFalse(fork.sessionId == fork.parent)
    }

    // ── fork: bildirimler ───────────────────────────────────────────

    @Test
    fun `k24 catallandi bildirimi yeni oturum adini tasir`() {
        val notice = forkNotice("Rapor özeti (branch)")
        assertTrue(notice.contains("Çatallandı"))
        assertTrue(notice.contains("Rapor özeti (branch)"))
    }

    @Test
    fun `k24 catallanamadi bildirimi sessiz kalmaz`() {
        val notice = forkFailedNotice("bağlantı koptu")
        assertTrue(notice.contains("Çatallanamadı"))
        assertTrue(notice.contains("bağlantı koptu"))
    }

    // ── steer: kuyruk durumu ────────────────────────────────────────

    @Test
    fun `k24 steer queued kuyrukta sayilir`() {
        assertTrue(steerQueuedAfter("queued"))
    }

    @Test
    fun `k24 steer queued disi durumlar kuyruk cipi acmaz`() {
        assertFalse(steerQueuedAfter("rejected"))
        assertFalse(steerQueuedAfter("error"))
        assertFalse(steerQueuedAfter("unknown"))
    }

    // ── fork: veri sınıfı ───────────────────────────────────────────

    @Test
    fun `k24 forked session varsayilanlari guvenli`() {
        val fork = ForkedSession(sessionId = "s1")
        assertEquals("", fork.storedSessionId)
        assertEquals("", fork.title)
        assertEquals(0, fork.messageCount)
    }
}