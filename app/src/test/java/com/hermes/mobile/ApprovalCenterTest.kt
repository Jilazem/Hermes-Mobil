package com.hermes.mobile

import com.hermes.mobile.data.ApprovalCenter
import com.hermes.mobile.data.ApprovalDecision
import com.hermes.mobile.data.ApprovalGuard
import com.hermes.mobile.data.ApprovalVoice
import com.hermes.mobile.data.FullControl
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * K18B-G3 — merkezi onay kutusu birim testleri.
 *
 * Kilitlenen davranışlar:
 *  - 2+ eşzamanlı onay isteği TEK listede (MausBot "Waiting on your yes" deseni).
 *  - Onayla → `/approve`, Reddet → `/deny [sebep]` (sohbet kartıyla AYNI dil).
 *  - Çift cevap engeli (çift dokunuş/çift kanal ikinci komutu göndermez).
 *  - `approval.settled` (başka kanal) kaydı kapatır.
 *  - "Bazı eylemler durmaz": salt-okunur araçlar onaysız devam eder,
 *    bilinmeyen araç temkin gereği DURUR (fail-closed) — guardReason deseni.
 *  - Sesli onay: "onayla" / "reddet [sebep]" + sıra; belirsiz girdi YORUMLANMAZ.
 */
class ApprovalCenterTest {

    @Before
    fun setUp() = ApprovalCenter.reset()

    @After
    fun tearDown() = ApprovalCenter.reset()

    // ── Liste ─────────────────────────────────────────────────────────

    @Test
    fun `iki es zamanli onay isteki tek listede gorunur`() {
        val a = ApprovalCenter.submit(
            sessionId = "s1", sessionTitle = "Derleme", request = "rm -rf build klasörü",
        )
        val b = ApprovalCenter.submit(
            sessionId = "s2", sessionTitle = "Dağıtım", request = "apk imzala ve gönder",
        )
        val pending = ApprovalCenter.pending()
        assertEquals(2, pending.size)
        assertEquals(listOf(a.id, b.id), pending.map { it.id })
        // Oturum ayrımı korunur — her istek kendi oturumuna yanıtlanır.
        assertEquals("s1", pending[0].sessionId)
        assertEquals("s2", pending[1].sessionId)
    }

    @Test
    fun `bekleyen istek tarihceye girmez kararli girer`() {
        val e = ApprovalCenter.submit(sessionId = null, sessionTitle = "", request = "x")
        assertEquals(0, ApprovalCenter.history().size)
        ApprovalCenter.decide(e.id, approve = true)
        assertEquals(0, ApprovalCenter.pending().size)
        assertEquals(1, ApprovalCenter.history().size)
    }

    // ── Olay çıkarımı (gateway sözleşmesi) ────────────────────────────

    @Test
    fun `approval request payload alanlari karta doner - uydurma yok`() {
        val payload = buildJsonObject {
            put("prompt_id", "p-42")
            put("description", "Tehlikeli komut çalıştırmak istiyor")
            put("command", "rm -rf /tmp/ornek")
        }
        val e = ApprovalCenter.submitFromEvent(
            type = "approval.request",
            sessionId = "s9",
            sessionTitle = "Terminal",
            text = "metin yedek",
            payload = payload,
        )
        assertEquals("Tehlikeli komut çalıştırmak istiyor", e.request)
        assertEquals("rm", e.tool)
        assertEquals("-rf /tmp/ornek", e.argsSummary)
        assertEquals("p-42", e.promptId)
        assertFalse(e.isSudo)
    }

    @Test
    fun `payload alani yoksa alan uydurulmaz`() {
        val e = ApprovalCenter.submitFromEvent(
            type = "sudo.request",
            sessionId = null,
            sessionTitle = "",
            text = null,
            payload = null,
        )
        assertNull(e.tool)
        assertNull(e.argsSummary)
        assertNull(e.promptId)
        assertTrue(e.isSudo)
        // Metin de yoksa kartta genel açıklama görünür (sessiz boş kart yok).
        assertTrue(e.request.isNotBlank())
        // D-05: ham id/boş etiket kart başlığı olmaz.
        assertEquals("Sohbet", e.sessionTitle)
    }

    @Test
    fun `ayni promptId tekrar gelirse ikinci kayit acilmaz`() {
        val payload = buildJsonObject { put("prompt_id", "p-dup") }
        val first = ApprovalCenter.submitFromEvent("approval.request", "s1", "a", "x", payload = payload)
        val second = ApprovalCenter.submitFromEvent("approval.request", "s1", "a", "x", payload = payload)
        assertEquals(first.id, second.id)
        assertEquals(1, ApprovalCenter.pending().size)
    }

    // ── Karar akışı: onayla → devam, reddet → iptal ───────────────────

    @Test
    fun `onayla - karar onaylanir ve komut slash-exec ile ayni dil`() {
        val e = ApprovalCenter.submit(sessionId = "s1", sessionTitle = "t", request = "r")
        assertTrue(ApprovalCenter.decide(e.id, approve = true))
        assertTrue(ApprovalCenter.history().single().decision is ApprovalDecision.Approved)
        // Sohbet kartıyla AYNI komut dili (tek kaynak: ApprovalCenter.slashCommand).
        assertEquals("/approve", ApprovalCenter.slashCommand(approve = true))
    }

    @Test
    fun `reddet - sebep kayda gecer ve komut sebep tasir`() {
        val e = ApprovalCenter.submit(sessionId = "s1", sessionTitle = "t", request = "r")
        assertTrue(ApprovalCenter.decide(e.id, approve = false, reason = "  yer yok  "))
        val denied = ApprovalCenter.history().single().decision as ApprovalDecision.Denied
        assertEquals("yer yok", denied.reason)
        assertEquals("/deny yer yok", ApprovalCenter.slashCommand(approve = false, reason = "yer yok"))
        assertEquals("/deny", ApprovalCenter.slashCommand(approve = false))
    }

    @Test
    fun `cift cevap engeli - ikinci karar komut gondermez`() {
        val e = ApprovalCenter.submit(sessionId = "s1", sessionTitle = "t", request = "r")
        assertTrue(ApprovalCenter.decide(e.id, approve = true))
        // İkinci dokunuş / diğer kanal: komut GİTMEZ.
        assertFalse(ApprovalCenter.decide(e.id, approve = false, reason = "fikir değiştirdim"))
        val decision = ApprovalCenter.history().single().decision
        assertTrue(decision is ApprovalDecision.Approved)
    }

    @Test
    fun `approval settled baskali kanali kapatir`() {
        val payload = buildJsonObject { put("prompt_id", "p-7") }
        val e = ApprovalCenter.submitFromEvent("approval.request", "s1", "a", "x", payload = payload)
        // Eşleşmeyen prompt_id kaydı KAPATMAZ.
        assertFalse(ApprovalCenter.settleByPrompt("p-baska"))
        assertEquals(1, ApprovalCenter.pending().size)
        assertTrue(ApprovalCenter.settleByPrompt("p-7"))
        assertTrue(ApprovalCenter.pending().isEmpty())
        val settled = ApprovalCenter.history().single()
        assertEquals(e.id, settled.id)
        assertTrue(settled.decision is ApprovalDecision.SettledElsewhere)
        // Settled kayda sonra dokunulamaz.
        assertFalse(ApprovalCenter.decide(e.id, approve = true))
    }

    @Test
    fun `tarihce budanir - liste sinirsiz buyumez`() {
        repeat(ApprovalCenter.HISTORY_LIMIT + 10) { i ->
            val e = ApprovalCenter.submit(sessionId = "s$i", sessionTitle = "t", request = "r$i")
            ApprovalCenter.decide(e.id, approve = true)
        }
        ApprovalCenter.pruneHistory()
        assertEquals(ApprovalCenter.HISTORY_LIMIT, ApprovalCenter.history().size)
    }

    // ── "Bazı eylemler durmaz" — guardReason deseni ───────────────────

    @Test
    fun `salt-okunur araclar onaysiz devam eder - durmaz`() {
        for (tool in FullControl.READ_TOOLS) {
            assertNull("salt-okunur araç durmamalı: $tool", ApprovalGuard.stopReason(tool))
        }
    }

    @Test
    fun `cihazi degistiren araclar durur`() {
        for (tool in FullControl.WRITE_TOOLS) {
            assertEquals(
                "değiştiren araç durmalı: $tool",
                ApprovalGuard.WHY_CHANGES,
                ApprovalGuard.stopReason(tool),
            )
        }
    }

    @Test
    fun `bilinmeyen arac temkin geregi durur - fail-closed`() {
        assertEquals(ApprovalGuard.WHY_UNKNOWN, ApprovalGuard.stopReason(null))
        assertEquals(ApprovalGuard.WHY_UNKNOWN, ApprovalGuard.stopReason("bash"))
        // Bilinmeyen araç sudo ise sudo sebebi öne çıkar.
        assertEquals(ApprovalGuard.WHY_SUDO, ApprovalGuard.stopReason("bash", isSudo = true))
        // Salt-okunur sudo olsa bile durmaz — sınıf kararı sınıfı belirler.
        assertNull(ApprovalGuard.stopReason(FullControl.SCREENSHOT, isSudo = true))
    }

    @Test
    fun `durmayan siniflar kutuda gorunur - gercek arac adlariyla`() {
        val classes = ApprovalGuard.passThroughClasses()
        assertTrue(classes.isNotEmpty())
        val joined = classes.joinToString(" ") { it.title + " " + it.detail }
        assertTrue(joined.contains("salt-okunur"))
        for (tool in FullControl.READ_TOOLS) {
            assertTrue("kutuda görünmeli: $tool", joined.contains(tool))
        }
    }

    // ── Sesli onay ────────────────────────────────────────────────────

    @Test
    fun `ses - onayla en eski istegi hedefler`() {
        val c = ApprovalVoice.parse("Onayla", pendingCount = 3)
        assertNotNull(c)
        assertEquals(0, c!!.entryIndex)
        assertTrue(c.approve)
        assertEquals("", c.reason)
    }

    @Test
    fun `ses - reddet sebebi tasinir`() {
        val c = ApprovalVoice.parse("reddet sebep: yer yok", pendingCount = 3)
        assertNotNull(c)
        assertFalse(c!!.approve)
        assertEquals("yer yok", c.reason)
    }

    @Test
    fun `ses - sirali komut ikinci istegi hedefler`() {
        assertEquals(1, ApprovalVoice.parse("onayla 2", pendingCount = 3)!!.entryIndex)
        assertEquals(1, ApprovalVoice.parse("ikinci isteği reddet", pendingCount = 3)!!.entryIndex)
    }

    @Test
    fun `ses - turkce buyuk harf ve aksan katlanir`() {
        assertNotNull(ApprovalVoice.parse("ONAYLA"))
        assertNotNull(ApprovalVoice.parse("Hayır"))
        assertNotNull(ApprovalVoice.parse("İptal"))
    }

    @Test
    fun `ses - belirsiz ya da anlamsiz girdi yorumlanmaz`() {
        assertNull(ApprovalVoice.parse("onayla reddet"))
        assertNull(ApprovalVoice.parse("hava bugün güzel"))
        assertNull(ApprovalVoice.parse(""))
        assertNull(ApprovalVoice.parse("reddet 5", pendingCount = 2))
        assertNull(ApprovalVoice.parse("onayla 0"))
    }
}