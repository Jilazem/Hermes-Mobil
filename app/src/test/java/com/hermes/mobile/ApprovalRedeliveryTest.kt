package com.hermes.mobile

import com.hermes.mobile.data.ApprovalCenter
import com.hermes.mobile.data.ApprovalDecision
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * K18B-G3 denetim koşulları (verdict-k18b-g3-merge.json, t_c807e52b):
 *
 * 1) ZORUNLU — YENİDEN TESLİM: aynı `promptId` ikinci kez olay olarak geldiğinde
 *    `ApprovalCenter` MEVCUT entry'yi (aynı id) döndürür; sohbet listesi keyed
 *    olduğu için koşulsuz kart eklemek duplicate-key ile SERT çöker
 *    (IllegalArgumentException). [mergeApprovalCard] tek kartı garanti eder.
 *
 * 2) GÜÇLÜ ÖNERİ — GÖNDERİM BAŞARISIZLIĞI: `answerApproval` kararı göndermeden
 *    ÖNCE kapatıyordu; `slashExec` ağ hatasında kayıt kilitli kalıyor ve ikinci
 *    dokunuş sessizce yutuluyordu. [ApprovalCenter.reopen] kaydı Pending'e geri
 *    açar, retry yolu (`decide` tekrar `true`) tekrar kurulur.
 */
class ApprovalRedeliveryTest {

    @Before
    fun setUp() = ApprovalCenter.reset()

    @After
    fun tearDown() = ApprovalCenter.reset()

    // ── 1) Yeniden teslim — duplicate-key yok ─────────────────────────

    private fun event(promptId: String) = buildJsonObject { put("prompt_id", promptId) }

    @Test
    fun `ayni promptId ikinci teslimde tek kart kalir - cokme yok`() {
        // Gerçek akış: aynı olay iki kez teslim edilir (yeniden bağlanma),
        // ApprovalCenter aynı kaydı (aynı id) döndürür.
        val first = ApprovalCenter.submitFromEvent(
            "approval.request", "s1", "Derleme", "x", payload = event("p-redeliver"),
        )
        val second = ApprovalCenter.submitFromEvent(
            "approval.request", "s1", "Derleme", "x", payload = event("p-redeliver"),
        )
        assertEquals(first.id, second.id)

        var items: List<ChatItem> = emptyList()
        items = mergeApprovalCard(items, first, isSudo = false)
        items = mergeApprovalCard(items, second, isSudo = false) // YENİDEN TESLİM

        val cards = items.filterIsInstance<ChatItem.Approval>()
        // Tek kart — keyed LazyColumn'da duplicate key çökmesi imkânsız.
        assertEquals(1, cards.size)
        assertEquals(first.id, cards.single().key)
        assertEquals("p-redeliver", cards.single().promptId)
        assertEquals(1, ApprovalCenter.pending().size)
    }

    @Test
    fun `farkli promptId yeni kart olarak eklenir`() {
        val a = ApprovalCenter.submitFromEvent(
            "approval.request", "s1", "t", "x", payload = event("p-1"),
        )
        val b = ApprovalCenter.submitFromEvent(
            "approval.request", "s1", "t", "x", payload = event("p-2"),
        )
        assertTrue(a.id != b.id)

        var items: List<ChatItem> = emptyList()
        items = mergeApprovalCard(items, a, isSudo = false)
        items = mergeApprovalCard(items, b, isSudo = false)
        assertEquals(2, items.filterIsInstance<ChatItem.Approval>().size)
    }

    @Test
    fun `yeniden teslim mevcut karti dokunmaz - answered korunur`() {
        val first = ApprovalCenter.submitFromEvent(
            "sudo.request", "s1", "t", "x", payload = event("p-dokunma"),
        )
        var items: List<ChatItem> = mergeApprovalCard(emptyList(), first, isSudo = true)
        // Kullanıcı cevapladı; kart işaretli. İkinci teslim bunu SIFIRLAMAMALI.
        items = items.map { (it as ChatItem.Approval).copy(answered = "Onaylandı") }
        val second = ApprovalCenter.submitFromEvent(
            "sudo.request", "s1", "t", "x", payload = event("p-dokunma"),
        )
        items = mergeApprovalCard(items, second, isSudo = true)

        val card = items.filterIsInstance<ChatItem.Approval>().single()
        assertEquals("Onaylandı", card.answered)
        assertTrue(card.isSudo)
    }

    @Test
    fun `yeni entry kart olarak eklenir ve promptId tasiyor`() {
        val e = ApprovalCenter.submitFromEvent(
            "approval.request", "s1", "t", "Ajan tehlikeli bir komut istiyor",
            payload = event("p-yeni"),
        )
        val items = mergeApprovalCard(emptyList(), e, isSudo = false)
        val card = items.filterIsInstance<ChatItem.Approval>().single()
        assertEquals(e.id, card.key)
        assertEquals(e.request, card.text)
        assertEquals("p-yeni", card.promptId)
        assertFalse(card.isSudo)
    }

    // ── 2) Gönderim başarısızlığında karar geri alma ──────────────────

    @Test
    fun `reopen kapatilan karari Pending e geri acar`() {
        val e = ApprovalCenter.submit(sessionId = "s1", sessionTitle = "t", request = "r")
        assertTrue(ApprovalCenter.decide(e.id, approve = true)) // kart kapatıldı
        assertEquals(0, ApprovalCenter.pending().size)

        // Gönderim başarısız → reopen → kayıt yeniden BEKLEMEDE.
        assertTrue(ApprovalCenter.reopen(e.id))
        assertEquals(1, ApprovalCenter.pending().size)
        assertEquals(0, ApprovalCenter.history().size)
    }

    @Test
    fun `reopen sonrasi retry decide kabul eder - ikinci komut gider`() {
        val e = ApprovalCenter.submit(sessionId = "s1", sessionTitle = "t", request = "r")
        ApprovalCenter.decide(e.id, approve = true)
        assertTrue(ApprovalCenter.reopen(e.id))
        // Retry yolu: kullanıcı (veya ses) yeniden cevaplayabilir.
        assertTrue(ApprovalCenter.decide(e.id, approve = false, reason = "vazgeçtim"))
        val denied = ApprovalCenter.history().single().decision as ApprovalDecision.Denied
        assertEquals("vazgeçtim", denied.reason)
    }

    @Test
    fun `reopen deny sebebiyle kapanan kaydi da geri acar`() {
        val e = ApprovalCenter.submit(sessionId = "s1", sessionTitle = "t", request = "r")
        ApprovalCenter.decide(e.id, approve = false, reason = "yok")
        assertTrue(ApprovalCenter.reopen(e.id))
        assertEquals(1, ApprovalCenter.pending().size)
    }

    @Test
    fun `reopen bilinmeyen id de false doner`() {
        assertFalse(ApprovalCenter.reopen("ap-olmayan"))
    }

    @Test
    fun `settled elsewhere kayit reopen ile geri acilmaz - baska kanal nihai`() {
        val e = ApprovalCenter.submitFromEvent(
            "approval.request", "s1", "t", "x", payload = event("p-son"),
        )
        assertTrue(ApprovalCenter.settleByPrompt("p-son"))
        assertFalse(ApprovalCenter.reopen(e.id))
        val settled = ApprovalCenter.history().single()
        assertTrue(settled.decision is ApprovalDecision.SettledElsewhere)
    }

    @Test
    fun `reopen zaten bekleyen kayitta kaydi bozmaz`() {
        val e = ApprovalCenter.submit(sessionId = "s1", sessionTitle = "t", request = "r")
        assertTrue(ApprovalCenter.reopen(e.id)) // zaten açık — sorun değil
        assertEquals(1, ApprovalCenter.pending().size)
        assertTrue(ApprovalCenter.decide(e.id, approve = true))
    }

    // ── WIRING — ChatViewModel onFailure yolu kaynağından doğrulanır ──
    // (D-01 dersinin repo içi muadili: bkz. JarvisUiWiringTest — "tanımlı ama
    // çağrısız" uç kalmasın; gerçek dosya okunur, iddia değil kanıt.)

    /** Gradle modül dizininden (app/) ya da worktree kökünden çalışabilir. */
    private fun source(name: String): String {
        val rel = "src/main/java/com/hermes/mobile/$name"
        val direct = File(rel)
        val f = if (direct.exists()) direct else File("app" + File.separator + rel)
        assertTrue("kaynak bulunamadı: $rel", f.exists())
        return f.readText()
    }

    @Test
    fun `wiring - slashExec hatasinda reopen cagirilir ve answered geri alinir`() {
        val src = source("ChatViewModel.kt")
        val onFailure = src.substringAfter(".onFailure { e ->")
        assertTrue(
            "onFailure içinde ApprovalCenter.reopen(key) çağrılmalı (karar geri alma)",
            onFailure.contains("ApprovalCenter.reopen(key)"),
        )
        assertTrue(
            "onFailure içinde kartın answered işareti geri alınmalı",
            onFailure.contains("item.copy(answered = null)"),
        )
        // Kart ekleme artık mergeApprovalCard üzerinden — koşulsuz append kalmadı.
        val approvalBranch = src.substringAfter("\"approval.request\", \"sudo.request\" -> {")
            .substringBefore("\"approval.settled\"")
        assertTrue(
            "approval dalı mergeApprovalCard kullanmalı",
            approvalBranch.contains("mergeApprovalCard("),
        )
        assertFalse(
            "approval dalında koşulsuz items + Approval ekleme kalmadı",
            approvalBranch.contains("items = it.items + ChatItem.Approval("),
        )
    }

    @Test
    fun `wiring - reopen ApprovalCenter kaynak dosyasinda tanimli`() {
        val src = source("data/ApprovalCenter.kt")
        assertTrue(src.contains("fun reopen(id: String): Boolean"))
    }
}
