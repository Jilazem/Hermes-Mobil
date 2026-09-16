package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.isPlaceholderSession
import com.hermes.mobile.ui.sourceChipLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-14: oturum listesi kart mantığı — içeriksiz kart eleme + kaynak chip'i.
 */
class SessionCardTur14Test {

    private fun session(
        id: String = "s1",
        title: String? = null,
        displayName: String? = null,
        preview: String? = null,
        messages: Int = 0,
        active: Boolean = false,
        source: String? = null,
    ) = HermesSession(
        id = id,
        source = source,
        serverTitle = title,
        displayName = displayName,
        preview = preview,
        messageCountRaw = messages,
        endedAt = if (active) null else 1000.0,
    )

    @Test
    fun tamamenBosKartYerTutucudur() {
        val s = session() // başlık yok, önizleme yok, mesaj yok, canlı değil
        assertTrue(isPlaceholderSession(s))
    }

    @Test
    fun mesajliKartYerTutucuDegildir() {
        assertFalse(isPlaceholderSession(session(messages = 3)))
    }

    @Test
    fun onizlemeliKartYerTutucuDegildir() {
        val s = session(preview = "Selam, raporu hazırla")
        assertFalse(isPlaceholderSession(s))
    }

    @Test
    fun canliKartAslaElemez() {
        val s = session(active = true)
        assertFalse(isPlaceholderSession(s))
    }

    @Test
    fun hamIdBaslikIcerikSayilmaz() {
        // Sunucu başlığı yoksa displayName==id olur; bu "başlık" konu değildir,
        // mesajı da yoksa kart boş sayılır (liste kirleticinin asıl kaynağı).
        val s = session(id = "538fa088", displayName = "538fa088", messages = 0)
        assertTrue(isPlaceholderSession(s))
    }

    @Test
    fun sayacBasligiIcerikSayilir() {
        // "Session 11" kalıbı bilgi taşıdığı için kart gizlenmez (DemoMask'sız).
        val s = session(title = "Session 11", displayName = null, messages = 0)
        assertFalse(isPlaceholderSession(s))
    }

    @Test
    fun kaynakChipEtiketleri() {
        assertEquals("TUI", sourceChipLabel("tui"))
        assertEquals("TUI", sourceChipLabel("cli"))
        assertEquals("Telegram", sourceChipLabel("telegram"))
        assertEquals("API", sourceChipLabel("api_server"))
        assertEquals("Zamanlanmış", sourceChipLabel("cron"))
        assertNull(sourceChipLabel(null))
        assertNull(sourceChipLabel("  "))
        assertEquals("ozel", sourceChipLabel("ozel"))
    }
}
