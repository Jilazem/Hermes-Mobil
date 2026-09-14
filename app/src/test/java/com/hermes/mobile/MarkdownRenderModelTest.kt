package com.hermes.mobile

import com.hermes.mobile.ui.LIST_LEVEL_INDENT_DP
import com.hermes.mobile.ui.LIST_MARK_GUTTER_DP
import com.hermes.mobile.ui.MdBlock
import com.hermes.mobile.ui.MdGroup
import com.hermes.mobile.ui.groupBlocks
import com.hermes.mobile.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-3 FR-002/FR-003 — render modeli: devam satırları maddeye birleşir
 * (hanging indent alanı doğru üretilir) ve bitişik maddeler sıkı grupta,
 * bloklar ayrı grupta toplanır. Compose'suz saf test.
 */
class MarkdownRenderModelTest {

    // ── FR-002: hanging indent — devam satırı madde metnine birleşir ──

    /** İki satırlık tire maddesi: TEK Bullet, text devamla birleşik. */
    @Test
    fun bulletDevamSatiriMaddeyeBirlesir() {
        val blocks = parseMarkdown(
            """
            - ilk madde çok uzun olduğu için saracak ve devam
              devam satırı tire taşımiyor sola kaymamalı
            """.trimIndent(),
        )
        val item = blocks.single()
        assertTrue("tek madde olmalı", item is MdBlock.Bullet)
        val text = (item as MdBlock.Bullet).text
        assertTrue("devam satırı birleşmeli", text.contains("\n"))
        assertTrue(
            "devam satırı ayrı blok ÜRETMEMELİ (sola kaymanın kökü buydu)",
            text.startsWith("ilk madde"),
        )
        assertTrue(text.lines().size == 2)
        // Render tarafı bu tek bloğu tek satır-gövde Text olarak çizer:
        // devam satırı işaret sütunu (LIST_MARK_GUTTER_DP) kadar içeriden,
        // yani madde metni hizasından sarar.
        assertEquals(0, item.levelIndentDp)
        assertTrue(LIST_MARK_GUTTER_DP in 18..28)
    }

    /** İki satırlık numaralı madde: aynı sözleşme. */
    @Test
    fun numberedDevamSatiriMaddeyeBirlesir() {
        val blocks = parseMarkdown(
            """
            1. birinci adım uzun bir cümle ile saracak devam
               satırı numara taşımadığı için maddeye aittir
            2. ikinci adım
            """.trimIndent(),
        )
        assertEquals(2, blocks.size)
        val first = blocks[0] as MdBlock.Numbered
        assertEquals(1, first.number)
        assertEquals(2, first.text.lines().size)
        assertTrue(first.text.contains("satırı numara"))
        val second = blocks[1] as MdBlock.Numbered
        assertEquals(2, second.number)
        assertEquals(1, second.text.lines().size)
    }

    /** Devam satırı yeni bir maddeyi BÖLMEZ; ama yeni tire yeni madde açar. */
    @Test
    fun ardIsikIkiMaddeAyriKorunur() {
        val blocks = parseMarkdown("- bir\n- iki")
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is MdBlock.Bullet })
    }

    /** İç içe madde girintisi katlama sabitinden üretilir. */
    @Test
    fun icIceMaddeGirintisi() {
        val blocks = parseMarkdown("  - iç madde")
        val b = blocks.single() as MdBlock.Bullet
        assertEquals(1, b.indent)
        assertEquals(LIST_LEVEL_INDENT_DP, b.levelIndentDp)
    }

    /** Blok işaretçisiyle başlayan devam-ADAY satırı yeni blok açar. */
    @Test
    fun devamSatiriBlokIsaretiyleBaslarsaBirlesmez() {
        val blocks = parseMarkdown("- madde başı\n- ikinci madde")
        assertEquals(2, blocks.size)
    }

    /** Paragraf davranışı bozulmadı: boş satıra kadar birleşir. */
    @Test
    fun paragrafBirlesmesiKorundu() {
        val blocks = parseMarkdown("satır a\nsatır b\n\nboşluktan sonra")
        assertEquals(2, blocks.size)
        val p = blocks[0] as MdBlock.Paragraph
        assertEquals("satır a\nsatır b", p.text)
    }

    // ── FR-003: bitişik maddeler sıkı grup, bloklar arası kırılım ──

    @Test
    fun bitisikMaddelerTekTightGrup() {
        val groups = groupBlocks(
            parseMarkdown("- a\n- b\n- c"),
        )
        assertEquals(1, groups.size)
        val g = groups.single()
        assertTrue("liste Tight olmalı", g is MdGroup.Tight)
        assertEquals(3, (g as MdGroup.Tight).items.size)
    }

    @Test
    fun bloklarArasiKirilimSoloGrupUretir() {
        val groups = groupBlocks(
            parseMarkdown("giriş paragrafı\n\n- a\n- b\n\nkapanış"),
        )
        // Solo(paragraf) → Tight(a,b) → Solo(paragraf)
        assertEquals(3, groups.size)
        assertTrue(groups[0] is MdGroup.Solo)
        assertTrue(groups[1] is MdGroup.Tight)
        assertTrue(groups[2] is MdGroup.Solo)
    }

    /** Madde bloktan SONRA paragraf ancak BOŞ SATIRLA ayrılır (CommonMark
     *  lazy continuation: boş satırsız düz satır son maddeye birleşir —
     *  tur-3 davranışının kendisi). Boş satırlı senaryo grup kırar. */
    @Test
    fun listeSonuParagraflaKirilir() {
        val groups = groupBlocks(
            parseMarkdown("- a\n- b\n\nsonraki paragraf"),
        )
        assertEquals(2, groups.size)
        assertTrue(groups[0] is MdGroup.Tight)
        assertTrue(groups[1] is MdGroup.Solo)
    }

    /** Lazy continuation: boş satırsız düz satır son maddeye BİRLEŞİR. */
    @Test
    fun bosSatirsizDuzSatirLazimDevamdir() {
        val blocks = parseMarkdown("- a\n- b\nlazım devam satırı")
        assertEquals(2, blocks.size)
        val b = blocks[1] as MdBlock.Bullet
        assertTrue(b.text.contains("lazım devam satırı"))
    }

    /** Tek madde grup değil Solo'dur (boşluk maliyeti yok ama Tight anlamı yok). */
    @Test
    fun tekMaddeSolo() {
        val groups = groupBlocks(parseMarkdown("- yalnız madde"))
        assertEquals(1, groups.size)
        assertTrue(groups.single() is MdGroup.Solo)
    }

    /** Kod bloğu madde sanılmaz. */
    @Test
    fun kodBloguListeDegildir() {
        val blocks = parseMarkdown("```kotlin\nval a = 1\n```\n")
        assertTrue(blocks.single() is MdBlock.Code)
        val g = groupBlocks(blocks).single()
        assertTrue(g is MdGroup.Solo)
    }

    /** Tur-2 K1 akışı: akan yarım kod çiti bloğa dönüşür (regresyon değil). */
    @Test
    fun yarimKodCitiBlokUretir() {
        val blocks = parseMarkdown("```python\nprint(1)")
        val code = blocks.single() as MdBlock.Code
        assertEquals("python", code.language)
        assertFalse(blocks.isEmpty())
    }
}
