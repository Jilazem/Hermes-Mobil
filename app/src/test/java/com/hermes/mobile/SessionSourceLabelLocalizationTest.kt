package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.cardSubtitle
import com.hermes.mobile.ui.feedSourceLabel
import com.hermes.mobile.ui.readableTitle
import com.hermes.mobile.ui.sessionSourceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tur-4 kabul kriteri 5 — TR/EN turunda ETİKET SIZINTISI 0.
 *
 * Emülatörde İngilizce arayüzde görülen sızıntılar: "Masaüstü",
 * "Zamanlanmış görev" (kaynak rozetleri) ve "Sohbet · …" başlık yedeği.
 * Veri (oturum başlığı/önizlemesi) çevrilmez; YALNIZ arayüz etiketleri.
 */
class SessionSourceLabelLocalizationTest {

    private fun sess(id: String, source: String?) = HermesSession(id = id, source = source)

    @Test
    fun `kaynak etiketleri ingilizce`() {
        assertEquals("Desktop", feedSourceLabel("desktop", en = true))
        assertEquals("Scheduled job", feedSourceLabel("cron", en = true))
        assertEquals("Unknown source", feedSourceLabel(null, en = true))
        assertEquals("Desktop", sessionSourceLabel("desktop", en = true))
        assertEquals("Scheduled job", sessionSourceLabel("cron", en = true))
        // Dilden bağımsız etiketler
        assertEquals("Telegram", feedSourceLabel("telegram", en = true))
        assertEquals("CLI", feedSourceLabel("cli", en = true))
    }

    @Test
    fun `kaynak etiketleri turkce varsayilan`() {
        assertEquals("Masaüstü", feedSourceLabel("desktop"))
        assertEquals("Zamanlanmış görev", feedSourceLabel("cron"))
        assertEquals("Bilinmeyen kaynak", feedSourceLabel(null))
    }

    @Test
    fun `baslik yedegi dile gore`() {
        val s = sess("20260913_184051_52f76a", source = "tui")
        assertEquals("Sohbet · 13.09 18:40", readableTitle(s, SessionFlags(), emptyMap()))
        assertEquals("Chat · 13.09 18:40", readableTitle(s, SessionFlags(), emptyMap(), en = true))
    }

    @Test
    fun `kart ikincil satiri ingilizce`() {
        // Başlık GERÇEK bir konu olmalı ki ikincil satır (kaynak) çizilsin.
        val s = HermesSession(
            id = "20260913_184051_52f76a",
            source = "desktop",
            serverTitle = "Uyap Giriş",
        )
        assertEquals("Masaüstü · 13.09 18:40", cardSubtitle(s, SessionFlags(), emptyMap()))
        assertEquals("Desktop · 13.09 18:40", cardSubtitle(s, SessionFlags(), emptyMap(), en = true))
    }

    @Test
    fun `hicbir ingilizce etiket turkce sozcuk icermez`() {
        val sources = listOf("desktop", "cron", "cli", "tui", "api", "telegram", null)
        sources.forEach { src ->
            val label = feedSourceLabel(src, en = true)
            listOf("Masaüstü", "Zamanlanmış", "Bilinmeyen", "görev", "Sohbet").forEach { tr ->
                assertFalse("EN etikette TR sözcük: $label", label.contains(tr))
            }
        }
    }
}
