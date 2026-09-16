package com.hermes.mobile

import com.hermes.mobile.ui.formatRelative

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-14: göreli zaman insan-okur biçim ("13 sa önce", "5 dk önce").
 * Saat dilimi/bağımlı dallar tam değer yerine BiÇİM doğrular (saat dilimi
 * kaymasına dayanıklı); tarih dalı regex ile bağlanır.
 */
class RelativeTimeTur14Test {

    private fun turkceVeyaIngilizce(s: String) = s

    @Test
    fun saatDaliOnceEkiniTasir() {
        val s = formatRelative(System.currentTimeMillis() / 1000.0 - 13 * 3600)
        assertTrue("beklenen 'sa önce|h ago', geldi: $s", s.matches(Regex("13 (sa önce|h ago)")))
    }

    @Test
    fun dakikaDaliOnceEkiniTasir() {
        val s = formatRelative(System.currentTimeMillis() / 1000.0 - 5 * 60)
        assertTrue("beklenen 'dk önce|min ago', geldi: $s", s.matches(Regex("5 (dk önce|min ago)")))
    }

    @Test
    fun gunDaliOnceEkiniTasir() {
        val s = formatRelative(System.currentTimeMillis() / 1000.0 - 2 * 86_400)
        assertTrue("beklenen 'g önce|d ago', geldi: $s", s.matches(Regex("2 (g önce|d ago)")))
    }

    @Test
    fun haftalikGecmisTarihVerir() {
        val s = formatRelative(System.currentTimeMillis() / 1000.0 - 30 * 86_400)
        // gg.AA ya da gg.AA.yyyy
        assertTrue("beklenen tarih deseni, geldi: $s", s.matches(Regex("\\d{2}\\.\\d{2}(\\.\\d{4})?")))
    }

    @Test
    fun nullVeSifirCizgi() {
        assertTrue(formatRelative(null) == "—")
        assertTrue(formatRelative(0.0) == "—")
    }
}
