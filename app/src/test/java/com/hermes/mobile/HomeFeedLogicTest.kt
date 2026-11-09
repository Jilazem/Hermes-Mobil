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

    private val sources = mapOf(
        "c" to "cron",
        "a" to "telegram",
        "b" to "cli",
        "s" to "system",
        "d" to "user",
        "e" to "user",
    )
    private val sourceOf: (DrawerRow) -> String? = { sources[it.dbId] }

    @Test
    fun `siralama sabit sonra calisan sonra en yeni`() {
        val rows = listOf(row("a", 100.0), row("b", 300.0), row("e", 200.0, working = true), row("d", 50.0, pinned = true))
        assertEquals(listOf("d", "e", "b", "a"), homeFeed(rows, HomeFilter.Chats, "", sourceOf).map { it.key })
    }

    @Test
    fun `tum islemler yalniz devam eden isler`() {
        // v7.8.1: Varsayılan "Tümü" listesi kullanıcının açtığı tüm konuları ve sabitlenenleri gösterir;
        // cron / sistem bildirimleri ana akışa sızmaz.
        val rows = listOf(row("a", 1.0, dot = "green"), row("b", 2.0, pinned = true), row("c", 3.0))
        assertEquals(listOf("b", "a"), homeFeed(rows, HomeFilter.All, "", sourceOf).map { it.key })
        // Cron görevi tek başınayken ana akışta görünmez (Zamanlanmış sekmesindedir).
        assertEquals(emptyList<String>(), homeFeed(listOf(row("c", 3.0)), HomeFilter.All, "", sourceOf).map { it.key })
    }

    @Test
    fun `arsivlenen duvarda gorunmez`() {
        val rows = listOf(row("a", 1.0), row("b", 2.0, archived = true))
        assertEquals(listOf("a"), homeFeed(rows, HomeFilter.Chats, "", sourceOf).map { it.key })
    }

    @Test
    fun `bildirim kaynaklari is ve sohbet listesine karismaz`() {
        // cron + system = bildirim kaynağı: yalnız "Zamanlanmış"ta (ve Bildirimler sekmesinde).
        val rows = listOf(row("a", 1.0, dot = "green"), row("c", 3.0), row("s", 4.0))
        assertEquals(listOf("a"), homeFeed(rows, HomeFilter.Chats, "", sourceOf).map { it.key })
        assertEquals(listOf("c"), homeFeed(rows, HomeFilter.Scheduled, "", sourceOf).map { it.key })
        assertEquals(emptyList<String>(), homeFeed(rows, HomeFilter.All, "", sourceOf).map { it.key }.filter { it == "c" || it == "s" })
    }

    @Test
    fun `calisan cron isi zamanlanmis sekmesinde ve sistem acikken gorunur`() {
        // v7.8.1: Çalışan bir zamanlanmış görev "Zamanlanmış" sekmesinde ve showSystemAndSubagents açıkken görünür;
        // ana akışa izinsiz sızmaz.
        val rows = listOf(row("c", 3.0, working = true))
        assertEquals(listOf("c"), homeFeed(rows, HomeFilter.Scheduled, "", sourceOf).map { it.key })
        assertEquals(listOf("c"), homeFeed(rows, HomeFilter.All, "", sourceOf, showSystemAndSubagents = true).map { it.key })
        assertEquals(emptyList<String>(), homeFeed(rows, HomeFilter.All, "", sourceOf, showSystemAndSubagents = false).map { it.key })
    }

    @Test
    fun `suzgecler sabit zamanlanmis sohbet`() {
        val rows = listOf(row("a", 1.0, dot = "green"), row("b", 2.0, pinned = true, dot = "grey"), row("c", 3.0))
        assertEquals(listOf("b"), homeFeed(rows, HomeFilter.Pinned, "", sourceOf).map { it.key })
        assertEquals(listOf("c"), homeFeed(rows, HomeFilter.Scheduled, "", sourceOf).map { it.key })
        assertEquals(setOf("a", "b"), homeFeed(rows, HomeFilter.Chats, "", sourceOf).map { it.key }.toSet())
    }

    @Test
    fun `arama baslik ve onizlemede`() {
        val rows = listOf(row("a", 1.0, title = "Fatura hesabı"), row("b", 2.0))
        assertEquals(listOf("a"), homeFeed(rows, HomeFilter.Chats, "fatura", sourceOf).map { it.key })
        assertEquals(listOf("b"), homeFeed(rows, HomeFilter.Chats, "önizleme b", sourceOf).map { it.key })
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
