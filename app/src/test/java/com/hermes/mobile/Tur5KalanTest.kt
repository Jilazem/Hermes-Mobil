package com.hermes.mobile

import com.hermes.mobile.ui.SourceIcon
import com.hermes.mobile.ui.sessionCounter
import com.hermes.mobile.ui.skeletonRowCount
import com.hermes.mobile.ui.sourceIcon
import com.hermes.mobile.ui.visibleUserMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-5 KALAN maddeleri: sayaç (KALAN-6), kaynak ikonu (KALAN-5),
 * iskelet yer tutucu kuralı (KALAN-1).
 */
class Tur5KalanTest {

    // ── KALAN-6 / FR-004: tek ve tutarlı sayaç ───────────────────────────

    @Test
    fun `sayac tek ifade - once toplam sonra acik`() {
        assertEquals("26 oturum · 14 açık", sessionCounter(total = 26, open = 14))
        assertEquals("26 sessions · 14 open", sessionCounter(total = 26, open = 14, en = true))
    }

    @Test
    fun `eski iki-adli ifade kalmadi`() {
        val text = sessionCounter(total = 26, open = 14)!!
        assertFalse("'açık kayıt' adı bırakılmamalı — güven kırığıydı", text.contains("açık kayıt"))
        assertFalse("'toplam' ikinci ad olarak kalmamalı", text.contains("toplam"))
        assertEquals("sayı bir kez 'oturum' diye anılır", 1, text.split("oturum").size - 1)
    }

    @Test
    fun `acik yoksa ikinci sayi yazilmaz`() {
        assertEquals("26 oturum", sessionCounter(total = 26, open = 0))
        assertEquals("26 sessions", sessionCounter(total = 26, open = 0, en = true))
    }

    @Test
    fun `liste bosken sayac hic yazilmaz`() {
        assertNull(sessionCounter(total = 0, open = 0))
        assertNull(sessionCounter(total = 0, open = 5))
    }

    @Test
    fun `acik sayisi toplami asmaz`() {
        assertEquals("4 oturum · 4 açık", sessionCounter(total = 4, open = 9))
    }

    // ── KALAN-5: kaynak ikonu ────────────────────────────────────────────

    @Test
    fun `kaynak ikonlari bilinen adlar icin cozulur`() {
        assertEquals(SourceIcon.Telegram, sourceIcon("telegram"))
        assertEquals(SourceIcon.Schedule, sourceIcon("cron"))
        assertEquals(SourceIcon.Desktop, sourceIcon("desktop"))
        assertEquals(SourceIcon.Terminal, sourceIcon("cli"))
        assertEquals(SourceIcon.Api, sourceIcon("api_server"))
        assertEquals("büyük harf/boşluk normalize edilir", SourceIcon.Telegram, sourceIcon("  Telegram "))
    }

    @Test
    fun `taninmayan ic kaynak adi icin ikon cizilmez`() {
        assertNull("ham iç ad ekrana düşmez", sourceIcon("spark"))
        assertNull(sourceIcon("bilinmeyen-ic-ad"))
        assertNull(sourceIcon(null))
        assertNull("boş kaynakta rozet yok", sourceIcon("   "))
    }

    @Test
    fun `kaynak ikonu ve kaynak etiketi ayni sozlukten gelir`() {
        // İkon çizilen her kaynağın okunur etiketi de olmalı (ekran okuyucu
        // contentDescription'ı bu etiketten besleniyor).
        for (src in listOf("telegram", "whatsapp", "cron", "cli", "web", "api", "desktop")) {
            assertTrue("$src için ikon yok", sourceIcon(src) != null)
            assertTrue("$src için etiket boş", com.hermes.mobile.ui.feedSourceLabel(src).isNotBlank())
        }
    }

    // ── KALAN-1: iskelet yer tutucu kuralı ───────────────────────────────

    @Test
    fun `yuklenirken ve liste bosken iskelet cizilir`() {
        assertEquals(3, skeletonRowCount(loading = true, loadedItems = 0))
        assertEquals(4, skeletonRowCount(loading = true, loadedItems = 0, placeholder = 4))
    }

    @Test
    fun `veri gelince ya da yuklenmiyorken iskelet cizilmez`() {
        assertEquals(0, skeletonRowCount(loading = true, loadedItems = 7))
        assertEquals(0, skeletonRowCount(loading = false, loadedItems = 0))
    }

    // ── Kusur I (tur-5, turda bulundu): dökümde sistem istemi ────────────

    @Test
    fun `cron sistem istemi dokumde balon olarak cizilmez`() {
        val cron = "[IMPORTANT: You are running as a scheduled cron job. DELIVERY: Your final " +
            "response will be automatically delivered to the user — do NOT use send_message."
        assertFalse("ham sistem istemi ekrana düşmez", visibleUserMessage(cron))
        assertFalse(visibleUserMessage("[SYSTEM] sen bir asistansın"))
        assertFalse("ham JSON da kullanıcı mesajı değil", visibleUserMessage("{\"status\": \"success\"}"))
    }

    @Test
    fun `gercek kullanici mesaji gorunur kalir`() {
        assertTrue(visibleUserMessage("Uyapa giriş yapmam gereken dosya var mı?"))
        assertTrue("insan cümlesi yasaklı değil", visibleUserMessage("IMPORTANT konuları listele"))
    }
}
