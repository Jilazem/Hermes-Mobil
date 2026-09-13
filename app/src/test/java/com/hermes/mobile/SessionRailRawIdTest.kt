package com.hermes.mobile

import com.hermes.mobile.ui.railLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * FR-001 ray savunması: `SessionRail` etiketi çözümlenmiş başlıktan gelir;
 * çözüm zinciri bir şekilde ham oturum id'sini sızdırırsa ray "20"/"CR" gibi
 * anlamsız iki harf DEĞİL "?" gösterir. (Boss şikâyeti: 20260913_184051_52f76a
 * gibi ham id'ler ekranda anlaşılmaz başlık olarak görünüyordu.)
 */
class SessionRailRawIdTest {

    @Test
    fun `ham id ray etiketi olmaz soru isareti olur`() {
        assertEquals("?", railLabel("20260913_184051_52f76a"))
        assertEquals("?", railLabel("20260913_184051"))
        assertEquals("?", railLabel("cron_2e4ea303c123_20260911_221601"))
        assertEquals("?", railLabel("CRON_2E4EA303C123"))
    }

    @Test
    fun `cozumlenmis basliklar normal etiket alir`() {
        // readaleTitle çıktısı okunaklıysa ilk iki harf normal işler.
        assertEquals("TU", railLabel("TUI · 13.09 18:40"))
        assertEquals("HE", railLabel("hermes-gunluk-yedek-02 · 11.09 22:16"))
        assertEquals("OT", railLabel("Oturum"))
        // "20"-ya benzeyen ama ham id OLMAYAN kısa metinler ezilmez.
        assertNotEquals("?", railLabel("2026 yılı planı"))
        assertNotEquals("?", railLabel("20 - Depo"))
    }

    @Test
    fun `surec ici kisa id de ezilmez ama anlamliyse kalir`() {
        // 8 hex süreç içi id ham kalıp DEĞİL (readableTitle zaten çözüyor);
        // çözülmemiş hali rayda yine de başlık olmasın diye "?" düşmesi
        // beklenmez — zincir bunu daha üstte engeller. Burada tek kanıt:
        // kalıp deseni yanlışlıkla kısa hex'i yakalamıyor.
        assertNotEquals("?", railLabel("538fa088"))
    }
}
