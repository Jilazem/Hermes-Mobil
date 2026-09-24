package com.hermes.mobile

import com.hermes.mobile.ui.DrawerRow
import com.hermes.mobile.ui.HomeFilter
import com.hermes.mobile.ui.czipHandoffPrompt
import com.hermes.mobile.ui.czipPackPath
import com.hermes.mobile.ui.homeFeed
import com.hermes.mobile.ui.homeGreeting
import com.hermes.mobile.ui.isLongSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedLogicTest {

    private fun row(
        key: String,
        t: Double,
        working: Boolean = false,
        pinned: Boolean = false,
        archived: Boolean = false,
        dot: String = "",
        title: String = "Konu $key",
    ) = DrawerRow(
        key = key, liveId = "", dbId = key, title = title, preview = "önizleme $key",
        epochSeconds = t, dot = dot, working = working, pinned = pinned,
        archived = archived, current = false, liveSession = null,
    )

    private val sources = mapOf("c" to "cron", "a" to "telegram", "b" to "cli")
    private val sourceOf: (DrawerRow) -> String? = { sources[it.dbId] }

    @Test
    fun `siralama sabit sonra calisan sonra en yeni`() {
        val rows = listOf(row("a", 100.0), row("b", 300.0), row("c", 200.0, working = true), row("d", 50.0, pinned = true))
        assertEquals(listOf("d", "c", "b", "a"), homeFeed(rows, HomeFilter.All, "", sourceOf).map { it.key })
    }

    @Test
    fun `arsivlenen duvarda gorunmez`() {
        val rows = listOf(row("a", 1.0), row("b", 2.0, archived = true))
        assertEquals(listOf("a"), homeFeed(rows, HomeFilter.All, "", sourceOf).map { it.key })
    }

    @Test
    fun `suzgecler canli sabit zamanlanmis sohbet`() {
        val rows = listOf(row("a", 1.0, dot = "green"), row("b", 2.0, pinned = true, dot = "grey"), row("c", 3.0))
        assertEquals(listOf("a"), homeFeed(rows, HomeFilter.Live, "", sourceOf).map { it.key })
        assertEquals(listOf("b"), homeFeed(rows, HomeFilter.Pinned, "", sourceOf).map { it.key })
        assertEquals(listOf("c"), homeFeed(rows, HomeFilter.Scheduled, "", sourceOf).map { it.key })
        assertEquals(setOf("a", "b"), homeFeed(rows, HomeFilter.Chats, "", sourceOf).map { it.key }.toSet())
    }

    @Test
    fun `arama baslik ve onizlemede`() {
        val rows = listOf(row("a", 1.0, title = "Fatura hesabı"), row("b", 2.0))
        assertEquals(listOf("a"), homeFeed(rows, HomeFilter.All, "fatura", sourceOf).map { it.key })
        assertEquals(listOf("b"), homeFeed(rows, HomeFilter.All, "önizleme b", sourceOf).map { it.key })
    }

    @Test
    fun `selamlama saate gore`() {
        assertEquals("Günaydın", homeGreeting(8, en = false))
        assertEquals("İyi akşamlar", homeGreeting(20, en = false))
        assertEquals("Up late?", homeGreeting(2, en = true))
    }

    @Test
    fun `czip paket yolu ciktidan cikar`() {
        val out = "📦 Oturum paketlendi: X\n   120 ilet | ...\n   paket: /root/.hermes/session-packs/x-20260924-101010.hkp\n"
        assertEquals("/root/.hermes/session-packs/x-20260924-101010.hkp", czipPackPath(out))
        assertNull(czipPackPath("❌ /czip hatasi: bulunamadi"))
        assertTrue(czipHandoffPrompt("/p.hkp", "T").contains("czip oku /p.hkp"))
        assertTrue(isLongSession(200))
        assertFalse(isLongSession(199))
    }
}
