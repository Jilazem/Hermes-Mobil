package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.JevBadgeLogic
import com.hermes.mobile.ui.drawerRows
import com.hermes.mobile.data.SessionFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-21 — JEV rozet karar katmanı (saf, boş-safe).
 *
 * Kural: gateway alanı GELMİYORSA hiçbir rozet çizilmez — uydurma renk
 * FAIL sebebidir. Ayrıca DrawerRow hattında REST `jev` alanının satıra
 * taşındığı, gelmeyenin boş kaldığı test edilir.
 */
class JevBadgeTest {

    private val t: (String, String) -> String = { tr, _ -> tr }

    @Test
    fun `boş ve null — rozet YOK (uydurma yok)`() {
        assertEquals(JevBadgeLogic.Badge.None, JevBadgeLogic.parse(null))
        assertEquals(JevBadgeLogic.Badge.None, JevBadgeLogic.parse(""))
        assertEquals(JevBadgeLogic.Badge.None, JevBadgeLogic.parse("   "))
        // Bilinmeyen metin de renk UYDURMAZ.
        assertEquals(JevBadgeLogic.Badge.None, JevBadgeLogic.parse("muhtemelen-iyi"))
    }

    @Test
    fun `masaustu sozlugu renk cozulur`() {
        //jev_gate/jev_guard log sözlüğü: karar "gec" ...
        assertEquals(JevBadgeLogic.Badge.Green, JevBadgeLogic.parse("gec"))
        assertEquals(JevBadgeLogic.Badge.Green, JevBadgeLogic.parse("Gec "))
        assertEquals(JevBadgeLogic.Badge.Yellow, JevBadgeLogic.parse("log-only"))
        assertEquals(JevBadgeLogic.Badge.Yellow, JevBadgeLogic.parse("gozlem"))
        assertEquals(JevBadgeLogic.Badge.Red, JevBadgeLogic.parse("iade"))
        assertEquals(JevBadgeLogic.Badge.Red, JevBadgeLogic.parse("blocked"))
    }

    @Test
    fun `gorunurluk ve tooltip`() {
        assertFalse(JevBadgeLogic.visible(JevBadgeLogic.Badge.None))
        assertTrue(JevBadgeLogic.visible(JevBadgeLogic.Badge.Green))
        assertEquals("", JevBadgeLogic.tooltip(JevBadgeLogic.Badge.None, t))
        assertTrue(JevBadgeLogic.tooltip(JevBadgeLogic.Badge.Green, t).contains("gate"))
        assertTrue(JevBadgeLogic.tooltip(JevBadgeLogic.Badge.Red, t).contains("iade"))
    }

    @Test
    fun `istatistik seridi — veri yoksa BOS satır, gösterilmez`() {
        assertEquals("", JevBadgeLogic.summaryLine(0, 0, 0, t))
    }

    @Test
    fun `istatistik seridi — sayilar tek satirda`() {
        val s = JevBadgeLogic.summaryLine(3, 1, 2, t)
        assertTrue(s.startsWith("JEV") && s.contains("✓3") && s.contains("◐1") && s.contains("✕2"))
    }

    // ── DrawerRow hattı ────────────────────────────────────────────────

    @Test
    fun `drawer satiri — REST jev alanini tasir, yoksa bos kalir`() {
        val withJev = HermesSession(id = "s1", startedAt = 1_758_000_000.0, jev = "gec")
        val plain = HermesSession(id = "s2", startedAt = 1_758_000_001.0)
        val rows = drawerRows(
            sessions = listOf(withJev, plain),
            live = emptyList(),
            liveByDbId = emptyMap(),
            flags = SessionFlags(),
            cronNames = emptyMap(),
        )
        val r1 = rows.first { it.dbId == "s1" }
        val r2 = rows.first { it.dbId == "s2" }
        assertEquals("gec", r1.jev)
        assertEquals("", r2.jev)
        // Karar: ikincisi çizilmez, birincisi yeşil.
        assertEquals(JevBadgeLogic.Badge.Green, JevBadgeLogic.parse(r1.jev))
        assertEquals(JevBadgeLogic.Badge.None, JevBadgeLogic.parse(r2.jev))
    }
}
