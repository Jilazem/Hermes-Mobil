package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.SessionMenuActions
import com.hermes.mobile.ui.displayTitle
import com.hermes.mobile.ui.sessionMenuActions
import com.hermes.mobile.ui.visibleSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Oturum eylem menüsü saf mantık: arşiv ekle/çıkar/filtre, arşivli oturum
 * aramada da görünmezlik, uzun basış menüsü içeriği ve başlık çözümleri.
 */
class SessionArchiveFilterTest {

    private fun sess(id: String, active: Boolean) =
        HermesSession(id = id, displayName = id, startedAt = 1_700_000_000.0, endedAt = if (active) null else 1_700_000_060.0)

    private val sessions = listOf(
        sess("a", active = false),
        sess("b", active = false),
        sess("c", active = true),
    )

    // ── Arşiv ekle/çıkar ───────────────────────────────

    @Test
    fun arşivEkle(): Unit {
        val flags = SessionFlags()
        val archived = flags.copy(archived = flags.archived + "a")
        assertTrue("a" in archived.archived)
        assertEquals(1, archived.archived.size)
    }

    @Test
    fun arşivCikar(): Unit {
        val flags = SessionFlags(archived = setOf("a", "b"))
        val unarchived = flags.copy(archived = flags.archived - "a")
        assertEquals(setOf("b"), unarchived.archived)
        assertFalse("a" in unarchived.archived)
    }

    // ── Arşiv filtresi ──────────────────────────────────

    @Test
    fun arşivliOturumGizli(): Unit {
        val flags = SessionFlags(archived = setOf("a"))
        val shown = visibleSessions(sessions, flags, showArchived = false)
        assertEquals(listOf("b", "c"), shown.map { it.id })
    }

    @Test
    fun arşivliOturumGoster(): Unit {
        val flags = SessionFlags(archived = setOf("a"))
        val shown = visibleSessions(sessions, flags, showArchived = true)
        assertEquals(setOf("a", "b", "c"), shown.map { it.id }.toSet())
    }

    @Test
    fun arşivliOturumAramadaDaGorunmez(): Unit {
        val flags = SessionFlags(archived = setOf("a"))
        val base = visibleSessions(sessions, flags, showArchived = false)
        val found = base.filter { it.title.contains("a", true) }
        assertFalse("a" in found.map { it.id })
    }

    @Test
    fun gizliOturumArşivFiltresindenBagimsizDisari(): Unit {
        val flags = SessionFlags(archived = setOf("b"), hidden = setOf("a"))
        val shown = visibleSessions(sessions, flags, showArchived = false)
        // 'a' gizli, 'b' arşivde → yalnız 'c' gösterilir.
        assertEquals(listOf("c"), shown.map { it.id })
        val allShown = visibleSessions(sessions, flags, showArchived = true)
        // 'a' gizli olduğu için 'b' ve 'c' gösterilir; gizlilik arşiv
        // filtresinden bağımsızdır.
        assertEquals(setOf("b", "c"), allShown.map { it.id }.toSet())
    }

    // ── Menü içeriği ─────────────────────────────────────

    @Test
    fun bitmisOturumdaDurdurVBudaAcik(): Unit {
        val menu = sessionMenuActions(sess("a", active = false), SessionFlags())
        assertTrue(menu.rename)
        assertTrue(menu.stop)
        assertTrue(menu.compress)
        assertTrue(menu.archive)
        assertFalse(menu.isArchived)
    }

    @Test
    fun calisanOturumdaYalnizRenameVesabitle(): Unit {
        val menu = sessionMenuActions(sess("c", active = true), SessionFlags())
        assertTrue(menu.rename)
        assertFalse(menu.stop)
        assertFalse(menu.compress)
        assertFalse(menu.archive)
        assertFalse(menu.delete)
    }

    @Test
    fun arşivliOturumdaEtiketDegisir(): Unit {
        val flags = SessionFlags(archived = setOf("a"))
        val menu = sessionMenuActions(sess("a", active = false), flags)
        assertTrue(menu.isArchived)
        assertTrue(menu.archive)
        val default = SessionMenuActions()
        assertEquals(false, default.isArchived)
    }

    // ── Başlık ────────────────────────────────────────────

    @Test
    fun yenidenAdlandirmabaSlkBaslik(): Unit {
        val flags = SessionFlags(renames = mapOf("a" to "Özel Ad"))
        assertEquals("Özel Ad", displayTitle(sess("a", active = false), flags))
    }

    @Test
    fun yenidenAdlandirmaYoksaSunucuBaslikAlinir(): Unit {
        val session = HermesSession(id = "x", displayName = "Telegram sohbeti")
        assertEquals("Telegram sohbeti", displayTitle(session, SessionFlags()))
    }

    @Test
    fun bayraGsizBaslikOturumAdiOlarakKullanilir(): Unit {
        val session = HermesSession(id = "cron_abc123")
        assertEquals("cron_abc123", displayTitle(session, SessionFlags()))
    }

    private fun dummy(): SessionMenuActions = SessionMenuActions()
}
