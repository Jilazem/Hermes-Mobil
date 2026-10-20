package com.hermes.mobile

import com.hermes.mobile.data.ActivityNotice
import com.hermes.mobile.data.MediaKind
import com.hermes.mobile.data.NoticeKind
import com.hermes.mobile.data.SessionChoice
import com.hermes.mobile.data.SessionChoiceBook
import com.hermes.mobile.data.THINK_LEVELS
import com.hermes.mobile.data.classifyNoticeKind
import com.hermes.mobile.data.foreignNoticeId
import com.hermes.mobile.data.isForeignSession
import com.hermes.mobile.data.isNoticeMuted
import com.hermes.mobile.data.mediaKindOf
import com.hermes.mobile.data.noticeVisible
import com.hermes.mobile.data.safeExternalUrl
import com.hermes.mobile.data.sessionChoiceBookPut
import com.hermes.mobile.data.thinkLabel
import com.hermes.mobile.data.thinkLevelsFor
import com.hermes.mobile.data.thinkSupported
import com.hermes.mobile.data.unreadNoticeCount
import com.hermes.mobile.data.withRecentModel
import com.hermes.mobile.ui.MdBlock
import com.hermes.mobile.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V5-2 UI paketi — saf mantık testleri (madde 6/7/8).
 *
 * Üç maddenin de karar katmanı Android'den ayrıdır: sınıflandırma
 * ([classifyNoticeKind]), URL güvenliği ([safeExternalUrl]), düşünme
 * kademeleri ([thinkLevelsFor]) ve oturum defteri ([sessionChoiceBookPut]).
 * UI yalnız bu kararları çizer.
 */
class V52UiPaketTest {

    // ── Madde 6: bildirim türleri ─────────────────────────────────────

    @Test fun `cron işareti cron türüne düşer`() {
        assertEquals(NoticeKind.CRON, classifyNoticeKind(null, "Yedekleme", "[cron] gece yedeği bitti"))
        assertEquals(NoticeKind.CRON, classifyNoticeKind("cron"))
        assertEquals(NoticeKind.CRON, classifyNoticeKind(null, "", "Scheduled cron job completed"))
    }

    @Test fun `gateway ve sistem işaretleri sistem türüne düşer`() {
        assertEquals(NoticeKind.SYSTEM, classifyNoticeKind(null, "", "gateway yeniden bağlandı"))
        assertEquals(NoticeKind.SYSTEM, classifyNoticeKind("system"))
        assertEquals(NoticeKind.SYSTEM, classifyNoticeKind(null, "Sistem bildirimi", ""))
    }

    @Test fun `bilinmeyen bildirim cevaptır — sessizce sisteme kaydırılmaz`() {
        assertEquals(NoticeKind.REPLY, classifyNoticeKind(null, "Selam", "rapor hazır mı?"))
        assertEquals(NoticeKind.AGENT, classifyNoticeKind("agent", "", "toplantıya 10 dakika"))
        assertEquals(NoticeKind.FOLLOW, classifyNoticeKind("follow", "", "takip"))
    }

    @Test fun `yabancı oturum tespiti açık oturumu tanır`() {
        assertFalse(isForeignSession(null, "open-1", "db-1"))
        assertFalse(isForeignSession("open-1", "open-1", "db-1"))
        assertFalse(isForeignSession("db-1", "open-1", "db-1"))
        assertTrue(isForeignSession("cron-9", "open-1", "db-1"))
    }

    @Test fun `aynı olay aynı kimliği üretir — çift bildirim olmaz`() {
        val a = foreignNoticeId("s1", "  yedekleme bitti  ")
        val b = foreignNoticeId("s1", "yedekleme bitti")
        assertEquals(a, b)
        assertTrue(foreignNoticeId("s2", "yedekleme bitti") != a)
    }

    @Test fun `filtre ve sessize alma`() {
        val cron = ActivityNotice(profileId = "p1", title = "t", text = "x", kind = NoticeKind.CRON)
        val reply = ActivityNotice(profileId = "p1", title = "t", text = "y", kind = NoticeKind.REPLY, read = true)
        // Boş filtre = hepsi görünür.
        assertTrue(noticeVisible(cron, emptySet(), unreadOnly = false))
        // Tür filtresi diğerini eler.
        assertTrue(noticeVisible(cron, setOf(NoticeKind.CRON), unreadOnly = false))
        assertFalse(noticeVisible(reply, setOf(NoticeKind.CRON), unreadOnly = false))
        // Okunmamış filtresi okunmuşu eler.
        assertFalse(noticeVisible(reply, emptySet(), unreadOnly = true))
        // Sessiz tür rozeti şişirmez ama kutuda kalır.
        assertEquals(0, unreadNoticeCount(listOf(cron, reply), mutedKinds = setOf(NoticeKind.CRON)))
        val reply2 = ActivityNotice(profileId = "p1", title = "t", text = "z", kind = NoticeKind.REPLY)
        assertEquals(1, unreadNoticeCount(listOf(cron, reply, reply2), mutedKinds = setOf(NoticeKind.CRON)))
        assertTrue(isNoticeMuted(NoticeKind.CRON, setOf(NoticeKind.CRON)))
    }

    // ── Madde 7: bağlantı güvenliği + medya ───────────────────────────

    @Test fun `yalnız http ve https dışarı çıkar`() {
        assertEquals("https://ornek.com/x", safeExternalUrl("https://ornek.com/x"))
        assertEquals("http://ornek.com", safeExternalUrl("  http://ornek.com  "))
    }

    @Test fun `tehlikeli şemalar reddedilir`() {
        assertNull(safeExternalUrl("javascript:alert(1)"))
        assertNull(safeExternalUrl("JAVASCRIPT:alert(1)"))
        assertNull(safeExternalUrl("data:text/html;base64,PHNjcmlwdD4="))
        assertNull(safeExternalUrl("file:///etc/passwd"))
        assertNull(safeExternalUrl("content://com.android.contacts"))
        assertNull(safeExternalUrl("intent://scan/#Intent;end"))
        assertNull(safeExternalUrl("vbscript:msgbox(1)"))
    }

    @Test fun `şema gizleme denemeleri reddedilir`() {
        assertNull(safeExternalUrl("ja\nvascript:alert(1)"))
        assertNull(safeExternalUrl("java\tscript:alert(1)"))
        assertNull(safeExternalUrl("ornek.com"))
        assertNull(safeExternalUrl("http:"))
        assertNull(safeExternalUrl(""))
        assertNull(safeExternalUrl(null))
    }

    @Test fun `medya türü uzantıdan çıkar — sorgu ve çapa soyulur`() {
        assertEquals(MediaKind.IMAGE, mediaKindOf("https://x/a.PNG"))
        assertEquals(MediaKind.IMAGE, mediaKindOf("https://x/a.webp?v=2"))
        assertEquals(MediaKind.VIDEO, mediaKindOf("https://x/a.mp4#t=1"))
        assertEquals(MediaKind.NONE, mediaKindOf("https://x/a.html"))
        assertEquals(MediaKind.NONE, mediaKindOf(null))
    }

    @Test fun `markdown görsel ve video bloğu ayrışır`() {
        val blocks = parseMarkdown("![alt](https://x/y.png)\n\nhttps://x/clip.mp4")
        assertTrue(blocks[0] is MdBlock.Image)
        assertEquals("alt", (blocks[0] as MdBlock.Image).alt)
        assertTrue(blocks[1] is MdBlock.Video)
    }

    @Test fun `güvenli olmayan görsel satırı düz metin kalır`() {
        val blocks = parseMarkdown("![alt](javascript:alert(1))")
        assertTrue(blocks.none { it is MdBlock.Image })
        assertTrue(blocks.none { it is MdBlock.Video })
    }

    // ── Madde 8: düşünme kademeleri + oturum defteri ──────────────────

    @Test fun `bilinmeyen modelde tüm kademeler açık kalır`() {
        assertEquals(THINK_LEVELS, thinkLevelsFor(null))
        assertEquals(THINK_LEVELS, thinkLevelsFor(""))
        assertEquals(THINK_LEVELS, thinkLevelsFor("bilinmeyen-model-9000"))
    }

    @Test fun `gömülü modellerde düşünme yok, hızlı modellerde kademe sınırlı`() {
        assertEquals(emptyList<String>(), thinkLevelsFor("bge-m3-embedding"))
        assertEquals(emptyList<String>(), thinkLevelsFor("whisper-large"))
        assertTrue("xhigh" !in thinkLevelsFor("gemma-flash"))
        assertTrue("medium" in thinkLevelsFor("gemma-flash"))
    }

    @Test fun `düşünme modeli tam kademe destekler`() {
        assertEquals(THINK_LEVELS, thinkLevelsFor("deepseek-r1"))
        assertEquals(THINK_LEVELS, thinkLevelsFor("claude-sonnet-4.5"))
    }

    @Test fun `thinkSupported ve etiketler`() {
        assertTrue(thinkSupported("gemma-flash", null))
        assertFalse(thinkSupported("gemma-flash", "xhigh"))
        assertTrue(thinkSupported("gemma-flash", "medium"))
        assertEquals("Kapalı", thinkLabel("none"))
        assertEquals("Maks", thinkLabel("xhigh"))
        assertEquals("—", thinkLabel("bilinmeyen"))
    }

    @Test fun `oturum defteri sırayı korur ve budar`() {
        var book = SessionChoiceBook()
        book = sessionChoiceBookPut(book, "s1", SessionChoice("p", "m1", "high"))
        book = sessionChoiceBookPut(book, "s2", SessionChoice("p", "m2", "low"))
        // Aynı oturum tekrar yazılınca başa gelir, kayıt ikiye çıkmaz.
        book = sessionChoiceBookPut(book, "s1", SessionChoice("p", "m1b", "medium"))
        assertEquals(listOf("s1", "s2"), book.order)
        assertEquals("m1b", book.choices["s1"]?.model)
        assertEquals("medium", book.choices["s1"]?.think)
    }

    @Test fun `boş kayıt defteri değiştirmez`() {
        val book = SessionChoiceBook(order = listOf("s1"), choices = mapOf("s1" to SessionChoice("p", "m", "high")))
        assertEquals(book, sessionChoiceBookPut(book, "", SessionChoice("p", "m2", null)))
        assertEquals(book, sessionChoiceBookPut(book, "s2", SessionChoice()))
    }

    @Test fun `son kullanılan modeller en son seçimi başa alır`() {
        assertEquals(listOf("b", "a"), withRecentModel(listOf("a"), "b"))
        assertEquals(listOf("a"), withRecentModel(listOf("a"), "a"))
        assertEquals(listOf("a"), withRecentModel(listOf("a"), "  "))
    }
}