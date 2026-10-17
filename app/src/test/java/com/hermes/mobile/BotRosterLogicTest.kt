package com.hermes.mobile

import com.hermes.mobile.data.BotCard
import com.hermes.mobile.data.BotRosterLogic
import com.hermes.mobile.data.BotValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K18B-G1 — roster saf mantığı: doğrulama eşikleri, görüntü sırası,
 * çoğaltma adlandırma ve "bu botla devam et" hedefi.
 */
class BotRosterLogicTest {

    private fun card(
        id: String = "b1",
        name: String = "Araştırmacı",
        pinned: Boolean = false,
        profile: String = "Araştırma",
        provider: String = "nous",
        model: String = "deepseek-v4",
    ) = BotCard(
        id = id, name = name, pinned = pinned, profile = profile,
        provider = provider, model = model,
    )

    // ── Doğrulama ──────────────────────────────────────────────────────────

    @Test
    fun `bos ad reddedilir`() {
        assertEquals(BotValidationError.NAME_BLANK, BotRosterLogic.validate("   ", ""))
    }

    @Test
    fun `uzun ad ve uzun kisilik reddedilir`() {
        assertEquals(
            BotValidationError.NAME_TOO_LONG,
            BotRosterLogic.validate("x".repeat(BotRosterLogic.MAX_NAME + 1), ""),
        )
        assertEquals(
            BotValidationError.PERSONALITY_TOO_LONG,
            BotRosterLogic.validate("Bot", "y".repeat(BotRosterLogic.MAX_PERSONALITY + 1)),
        )
    }

    @Test
    fun `gecerli kart gecer`() {
        assertNull(BotRosterLogic.validate("Bot", "kısa kişilik"))
    }

    // ── Görüntü sırası ─────────────────────────────────────────────────────

    @Test
    fun `sabitlenenler ustte gelir digerleri sira korunur`() {
        val a = card(id = "a")
        val b = card(id = "b", pinned = true)
        val c = card(id = "c")
        val d = card(id = "d", pinned = true)
        assertEquals(listOf("b", "d", "a", "c"), BotRosterLogic.sortedForDisplay(listOf(a, b, c, d)).map { it.id })
    }

    // ── Çoğaltma adlandırma ────────────────────────────────────────────────

    @Test
    fun `cogaltma adi benzersizdir`() {
        assertEquals("Bot (kopya)", BotRosterLogic.duplicateName("Bot", setOf("Bot")))
        assertEquals("Bot (kopya 2)", BotRosterLogic.duplicateName("Bot", setOf("Bot", "Bot (kopya)")))
        assertEquals(
            "Bot (kopya 3)",
            BotRosterLogic.duplicateName("Bot", setOf("Bot", "Bot (kopya)", "Bot (kopya 2)")),
        )
    }

    @Test
    fun `cogaltma uzun adi kirpar ek hic tasmaz`() {
        val uzun = "x".repeat(BotRosterLogic.MAX_NAME)
        val kopya = BotRosterLogic.duplicateCopy(
            card(name = uzun), existingNames = setOf(uzun), newId = "yeni", now = 42L,
        )
        assertTrue("kopya adı sınırı aşmamalı: ${kopya.name.length}", kopya.name.length <= BotRosterLogic.MAX_NAME)
        assertTrue(kopya.name.endsWith(" (kopya)"))
    }

    @Test
    fun `cogaltma pini acik yapmaz ve kimlikleri ayirir`() {
        val kaynak = card(id = "kaynak", pinned = true)
        val kopya = BotRosterLogic.duplicateCopy(
            kaynak, existingNames = setOf(kaynak.name), newId = "kopya-id", now = 7L,
        )
        assertEquals("kopya-id", kopya.id)
        assertEquals(kaynak.name + " (kopya)", kopya.name)
        assertEquals(false, kopya.pinned)
        assertEquals(7L, kopya.createdAt)
        assertEquals(kaynak.profile, kopya.profile)
        assertEquals(kaynak.provider, kopya.provider)
        assertEquals(kaynak.model, kopya.model)
    }

    // ── "Bu botla devam et" ────────────────────────────────────────────────

    @Test
    fun `devam et ayni profil ve modeli dondurur`() {
        val t = BotRosterLogic.resumeTarget(card(profile = "Yedek Bot"))
        assertEquals("Yedek Bot", t.profile)
        assertEquals("nous", t.provider)
        assertEquals("deepseek-v4", t.model)
    }

    @Test
    fun `bos profil yonlendirici olur bos model korunur`() {
        val t = BotRosterLogic.resumeTarget(card(profile = "  ", provider = "", model = ""))
        assertNull(t.profile)
        assertEquals("", t.provider)
        assertEquals("", t.model)
    }

    // ── D-05: görünümde ham kimlik yok ─────────────────────────────────────

    @Test
    fun `kart afisi ham id icermez`() {
        val c = card(id = "20261017-abcdef12-UUID-SIZINTI")
        val afis = BotRosterLogic.cardTitle(c)
        assertTrue(afis.contains("Araştırmacı"))
        assertTrue(!afis.contains("20261017-abcdef12"))
    }
}