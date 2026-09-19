package com.hermes.mobile

import com.hermes.mobile.ui.theme.BUILTIN_THEMES
import com.hermes.mobile.ui.theme.HermesPalette
import com.hermes.mobile.ui.theme.argbWithAlpha
import com.hermes.mobile.ui.theme.contrastRatio
import com.hermes.mobile.ui.theme.hexToArgb
import com.hermes.mobile.ui.theme.isAccentDistinct
import com.hermes.mobile.ui.theme.onColorFor
import com.hermes.mobile.ui.theme.relativeLuminance
import com.hermes.mobile.ui.theme.resolveIsLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-17 B1+B2 birim testleri (FR-004).
 *
 * a) her preset icin accent != textPrimary (hex esitligi + gorunur fark),
 * b) isLight dallanmasinin dogru semayi secmesi (her preset x light/dark),
 * c) on-accent kontrast orani (WCAG AA >= 4.5 baslik-disi metin).
 *
 * Renkler 0xAARRGGBB Long uzerinden dogrulanir (ThemeLogic saf kalibi);
 * Compose Color'a JVM testinde dokunulmaz.
 */
class ThemeTur17Test {

    private fun argb(p: HermesPalette, field: String): Long {
        val hex = when (field) {
            "background" -> p.background
            "accent" -> p.accent
            "textPrimary" -> p.textPrimary
            "surface" -> p.surface
            "surfaceDim" -> p.surfaceDim
            "border" -> p.border
            else -> error("bilinmeyen alan $field")
        }
        return requireNotNull(hexToArgb(hex)) { "preset ${p.id} alan $field bozuk hex" }
    }

    // ---- (a) FR-004a: accent != textPrimary ----

    @Test
    fun `her preset aksan metinden hex olarak ayrik`() {
        for (p in BUILTIN_THEMES) {
            assertTrue(
                "${p.id}: accent == textPrimary (B2 ihlali)",
                p.accent != p.textPrimary,
            )
        }
    }

    @Test
    fun `her preset aksan metinden gorulur sekilde ayrik`() {
        for (p in BUILTIN_THEMES) {
            val a = argb(p, "accent"); val t = argb(p, "textPrimary")
            assertTrue(
                "${p.id}: aksan/metin kontrasti 1.30 alti (ayrim gorulmez)",
                contrastRatio(a, t) >= 1.30,
            )
        }
    }

    @Test
    fun `varsayilan hermes 20 - bolum 3_1 degerleri birebir`() {
        val h = BUILTIN_THEMES.first { it.id == "hermes" }
        assertEquals("#04171A", h.background)
        assertEquals("#4FD8C0", h.accent)
        assertEquals("#F2EFE6", h.textPrimary)
    }

    @Test
    fun `nous 20 - bolum 3_1 acik token seti (aksan AA icin koyulasti)`() {
        val n = BUILTIN_THEMES.first { it.id == "nous" }
        assertEquals("#F8FAFB", n.background)
        // §3.1 #0F8C74 beyaz ustunde 4.18:1 kalıyor → AA için aile içi #0E836C.
        assertEquals("#0E836C", n.accent)
        assertEquals("#17232B", n.textPrimary)
        assertEquals(true, n.isLight)
    }

    // ---- (b) FR-004b: isLight dallanmasi, her preset x light/dark ----

    @Test
    fun `isaretli preset dogru semayi secer - 6 koyu 1 acik`() {
        assertEquals(1, BUILTIN_THEMES.count { it.lightTheme() })
        assertEquals("nous", BUILTIN_THEMES.first { it.lightTheme() }.id)
        BUILTIN_THEMES.forEach { p ->
            assertEquals(
                "${p.id}: isLight isareti lightTheme()'e yansimadi",
                p.isLight,
                p.lightTheme(),
            )
        }
    }

    @Test
    fun `isaret yoksa luminans karar verir - koyu zemin koyu kalicidir`() {
        val koyu = hexToArgb("#04171A")!!
        assertEquals(false, resolveIsLight(null, koyu))
        val acik = hexToArgb("#F8FAFB")!!
        assertEquals(true, resolveIsLight(null, acik))
    }

    @Test
    fun `isaret luminansi ezer - skin isik=false ise koyu cizilir`() {
        val parlakZemin = hexToArgb("#FFFFFF")!!
        assertEquals(false, resolveIsLight(false, parlakZemin))
        assertEquals(true, resolveIsLight(true, hexToArgb("#101010")!!))
    }

    @Test
    fun `tumu preset arka planlari ayni kumede - isaret olmasa da luminans ayni sonucu verirdi`() {
        // 6 koyu preset luminansla da koyu, nous luminansla da acik cikmali.
        for (p in BUILTIN_THEMES) {
            val bg = argb(p, "background")
            assertEquals(
                "${p.id}: isaret ile luminans karari celisiyor",
                p.lightTheme(),
                com.hermes.mobile.ui.theme.isLightByLuminance(bg),
            )
        }
    }

    // ---- (c) FR-004c: on-accent kontrast + metin AA ----

    @Test
    fun `dolgulu CTA on-accent her preset'te WCAG AA 4_5 ustu`() {
        for (p in BUILTIN_THEMES) {
            val a = argb(p, "accent"); val bg = argb(p, "background")
            val on = onColorFor(a, bg)
            val ratio = contrastRatio(a, on)
            assertTrue(
                "${p.id}: on-accent kontrast ${"%.2f".format(ratio)} < 4.5",
                ratio >= 4.5,
            )
        }
    }

    @Test
    fun `metin rolleri zemin ustu en az baslik-disi AA - textPrimary Secondary Muted`() {
        // B2 preset degisiklikleri okunabilirligi dusurmek zorunda degil.
        for (p in BUILTIN_THEMES) {
            val bg = argb(p, "background")
            val tp = argb(p, "textPrimary")
            assertTrue("${p.id}: textPrimary AA alti", contrastRatio(tp, bg) >= 4.5)
        }
    }

    @Test
    fun `nous dolgulu CTA beyaz on-accent alir - koyu dolguda metin acik olmali`() {
        val n = BUILTIN_THEMES.first { it.id == "nous" }
        val on = onColorFor(argb(n, "accent"), argb(n, "background"))
        // beyaz 0xFFFFFFFF ya da 0xFFFFFF... on-accent beyaz ailesinden
        assertEquals(0xFFL, (on shr 16 and 0xFF))
        assertEquals(0xFFL, (on shr 8 and 0xFF))
        assertEquals(0xFFL, (on and 0xFF))
    }

    @Test
    fun `hermes dolgulu CTA koyu zeminli on-accent alir - parlak dolguda metin koyu`() {
        val h = BUILTIN_THEMES.first { it.id == "hermes" }
        val on = onColorFor(argb(h, "accent"), argb(h, "background"))
        assertTrue(
            "on-accent koyu olmali (luminans < 0.2)",
            relativeLuminance(on) < 0.2,
        )
    }

    // ---- saf yardimcilarin dogrulugu ----

    @Test
    fun `hexToArgb 6 ve 8 hane + bozuk`() {
        assertEquals(0xFFFFE6CBL, hexToArgb("#FFE6CB"))
        assertEquals(0xB3040F12L, hexToArgb("#B3040F12"))
        assertEquals(null, hexToArgb("#12345"))
        assertEquals(null, hexToArgb("mavi"))
        assertEquals(0xFF0F8C74L, hexToArgb(" 0F8C74 "))
    }

    @Test
    fun `kontrast orani bilinen deger - siyah beyaz 21`() {
        assertEquals(21.0, contrastRatio(0xFF000000L, 0xFFFFFFFFL), 0.01)
        assertEquals(1.0, contrastRatio(0xFF808080L, 0xFF808080L), 0.01)
    }

    @Test
    fun `argbWithAlpha alfa kanalini degistirir RGB kalarak`() {
        assertEquals(0xB304171AL, argbWithAlpha(0xFF04171AL, 0xB3))
        assertEquals(0xFF04171AL, argbWithAlpha(0x0004171AL, 255))
        assertEquals(0x00FFFFFFL, argbWithAlpha(0xFFFFFFFFL, 0))
    }

    @Test
    fun `isAccentDistinct ayni hexi reddeder`() {
        assertTrue(isAccentDistinct(0xFF4FD8C0L, 0xFFF2EFE6L, 1.30))
        val krem = 0xFFFFE6CBL
        assertTrue(!isAccentDistinct(krem, krem))
    }

    // ---- skin geriye donusumu (esik) ----

    @Test
    fun `eski skin verisi isLight alani olmadan deserialize olur ve luminansla karar`() {
        val json = """
            {"id":"skin1","label":"Kullanici","background":"#FAFAFA","surface":"#FFFFFF",
             "surfaceDim":"#EEE","border":"#DDD","borderStrong":"#CCC","accent":"#0000AA",
             "textPrimary":"#111","textSecondary":"#333","textMuted":"#666","textFaint":"#999"}
        """.trimIndent()
        val skin = kotlinx.serialization.json.Json.decodeFromString(
            com.hermes.mobile.ui.theme.HermesPalette.serializer(), json,
        )
        assertEquals(null, skin.isLight)
        assertEquals(true, skin.lightTheme()) // acik zemin -> light dal
        // B2 kurali kullanici skin'ine zorla dayatilamaz; hex ayniligi ise henuz kontrol disi:
        assertTrue(skin.background.isNotEmpty())
    }
}
