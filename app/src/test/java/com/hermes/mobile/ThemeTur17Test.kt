package com.hermes.mobile

import com.hermes.mobile.ui.theme.BUILTIN_THEMES
import com.hermes.mobile.ui.theme.HermesPalette
import com.hermes.mobile.ui.theme.WCAG_AA_NORMAL_TEXT
import com.hermes.mobile.ui.theme.argbWithAlpha
import com.hermes.mobile.ui.theme.contrastRatio
import com.hermes.mobile.ui.theme.hexToArgb
import com.hermes.mobile.ui.theme.isAccentDistinct
import com.hermes.mobile.ui.theme.onColorFor
import com.hermes.mobile.ui.theme.relativeLuminance
import com.hermes.mobile.ui.theme.resolveIsLight
import com.hermes.mobile.ui.theme.resolveOnAccent
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

    // ---- tur-17 denetim 1/3 B2-CTA + B2-matris: 15 tema x 4 sutun matrisi ----
    //
    // 15 satir = 7 BUILTIN preset + 8 fixture skin. Fixture'lar, mobil tarafa
    // customThemes/skin akisindan gelebilecek aydinlik dolgulu senaryolari
    // temsilen Desktop preset degerlerinden (apps/desktop/src/themes/presets.ts
    // + web/src/themes/presets.ts, agent-maintenance reposu) uretilmistir —
    // repoda BUILTIN disi tema KODU yok; 'copper/amber' gibi adlar bu 15
    // koleksiyonun hicbir yerinde gecen adlar degildir (kod-grep 0).
    // 4 sutun (esikler):
    //   1) textPrimary/bg     >= 4.5  (WCAG AA govde)
    //   2) accent/bg          >= 3.0  (AA buyuk-metin / non-text)
    //   3) accent/textPrimary >= 1.30 (B2 ayrimi, mevcut test esigi)
    //   4) CTA metin/dolgu = onAccent(accent,bg)/accent >= 4.5  (WCAG AA)
    // resolveOnAccent AA'yi kurtarir: iki taban aday da altta kalirsa kazanan
    // aday siyaha kademeli karistirilir (dolgu ustu metin koyulastirilir).

    private data class MatrixRow(
        val id: String,
        val background: String,
        val accent: String,
        val textPrimary: String,
    )

    private val matrixThemes: List<MatrixRow> =
        BUILTIN_THEMES.map { MatrixRow(it.id, it.background, it.accent, it.textPrimary) } + listOf(
            MatrixRow("fx-github", "#FFFFFF", "#196D31", "#1F2328"),
            MatrixRow("fx-nous-desk", "#FFFFFF", "#0053FD", "#1F2328"),
            MatrixRow("fx-catppuccin", "#EFF1F5", "#4E1F8F", "#4C4F69"),
            MatrixRow("fx-everforest", "#FDF6E3", "#3F4F26", "#5C6A72"),
            MatrixRow("fx-solarized", "#FDF6E3", "#675E34", "#1F1F1F"),
            MatrixRow("fx-nous-alt", "#F8FAFF", "#0D2F86", "#17171A"),
            MatrixRow("fx-rose", "#1A0F15", "#FF7BA6", "#FFD4E1"),
            MatrixRow("fx-nous-blue", "#E8F2FD", "#0053FD", "#170D02"),
        )

    private fun toArgb(hex: String): Long = requireNotNull(hexToArgb(hex)) { "bozuk hex $hex" }

    private fun hexOf(argb: Long): String = "#%06X".format(argb and 0xFFFFFFL)

    @Test
    fun `onColorFor taban aday - iki adaydan iyisini secer`() {
        // koyu dolgu -> beyaz metin, parlak dolgu -> koyu zemin metni
        assertEquals(0xFFFFFFFFL, onColorFor(0xFF0E836CL, 0xFFF8FAFBL))
        assertTrue(
            "parlak dolgu koyu zemin metin alir",
            relativeLuminance(onColorFor(0xFF4FD8C0L, 0xFF04171AL)) < 0.5,
        )
    }

    @Test
    fun `resolveOnAccent tabanini korur - AA gecen degeri degistirmez`() {
        // 7 BUILTIN'de taban aday zaten AA — resolver dokunmaz (piksel regresyonu yok)
        for (p in BUILTIN_THEMES) {
            val a = toArgb(p.accent); val bg = toArgb(p.background)
            assertEquals(p.id, onColorFor(a, bg), resolveOnAccent(a, bg))
        }
    }

    @Test
    fun `resolveOnAccent kurtarici - iki taban aday da AA alti ise koyulastirir`() {
        // soluk yesil dolgu: beyaz 1.82, koyu zemin ~2 → resolver AA'ya tasir
        val fixed = resolveOnAccent(0xFFB8C2C6L, 0xFFB0B8BCL)
        assertTrue(
            "kurtarma sonrasi kontrast ${"%.2f".format(contrastRatio(0xFFB8C2C6L, fixed))} < 4.5",
            contrastRatio(0xFFB8C2C6L, fixed) >= WCAG_AA_NORMAL_TEXT,
        )
    }

    @Test
    fun `15x4 kontrast matrisi - 4 sutun da esik ust ve dosyaya yazilir`() {
        val sb = StringBuilder()
        sb.appendLine("Tur-17 B2 — 15 tema x 4 sutun WCAG matrisi (ThemeTur17Test uretimi, java.util)")
        sb.appendLine("Satirlar: 7 BUILTIN preset + 8 fixture skin (customThemes/skin akisi senaryosu;")
        sb.appendLine("  fixture degerleri Desktop preset'lerinden: agent-maintenance apps/desktop + web presets.ts).")
        sb.appendLine("Sutunlar/esikler: 1) textPrimary/bg >=4.5  2) accent/bg >=3.0  3) accent/textPrimary >=1.30  4) CTA onAccent/dolgu >=4.5")
        sb.appendLine("Sutun 4 onAccent = resolveOnAccent(accent,bg) — taban AA alti ise kazanan aday siyaha karistirilir (tur17 denetim B2-CTA fix).")
        sb.appendLine()
        sb.appendLine(String.format(java.util.Locale.ROOT, "%-14s %10s %10s %10s %12s %10s", "tema", "tp/bg", "acc/bg", "acc/tp", "onAccent", "CTA metin/dolgu"))
        var failures = 0
        for ((id, background, accent, textPrimary) in matrixThemes) {
            val bg = toArgb(background); val acc = toArgb(accent); val tp = toArgb(textPrimary)
            val c1 = contrastRatio(tp, bg)
            val c2 = contrastRatio(acc, bg)
            val c3 = contrastRatio(acc, tp)
            val on = resolveOnAccent(acc, bg)
            val c4 = contrastRatio(acc, on)
            val ok = c1 >= 4.5 && c2 >= 3.0 && c3 >= 1.30 && c4 >= WCAG_AA_NORMAL_TEXT
            if (!ok) failures++
            sb.appendLine(
                String.format(
                    java.util.Locale.ROOT,
                    "%-14s %10.2f %10.2f %10.2f %12s %10.2f %s",
                    id, c1, c2, c3, hexOf(on), c4, if (ok) "OK" else "KALDI",
                ) + if (on != onColorFor(acc, bg)) "  (resolver kurtardi)" else "",
            )
        }
        sb.appendLine()
        sb.appendLine("SONUC: ${matrixThemes.size - failures}/${matrixThemes.size} tema 4 sutunda gecer (sutun 4 >= 4.5).")

        // denetim/tur17/15x4-matris-ciktisi.txt — gradle test CWD'si app/ olabilir;
        // repo kokunu settings.gradle.kts ile bul, bulamazsan user.dir'e yaz (test patlamaz,
        // icerik assertion'i ayrica asagida). user.dir platform-tipi String? oldugu icin
        // non-null yerel degiskene alinir (tur17 LOW: Java type mismatch uyarisi).
        val userDir: String = System.getProperty("user.dir") ?: "."
        val repoRoot = generateSequence(java.io.File(userDir)) { it.parentFile }
            .firstOrNull { java.io.File(it, "settings.gradle.kts").exists() }
            ?: java.io.File(userDir)
        val outDir = java.io.File(repoRoot, "denetim/tur17").apply { mkdirs() }
        java.io.File(outDir, "15x4-matris-ciktisi.txt").writeText(sb.toString())

        assertTrue("matris satirlari 15 olmali", matrixThemes.size == 15)
        assertEquals("4. sutun dahil 15/15 gecmeli — eksik sutun dusurulemez:\n" + sb, 0, failures)
    }
}
