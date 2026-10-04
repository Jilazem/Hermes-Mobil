package com.hermes.mobile

import com.hermes.mobile.ui.MdBlock
import com.hermes.mobile.ui.parseMarkdown
import com.hermes.mobile.ui.streamCursorSuffix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TUR-29A madde 1 — blok bazlı streaming relayout (TokenFlow deseni): akan
 * metnin tamamlanmış blokları token'dan token'a DEĞİŞMEZ (Compose skip ile
 * yalnız son blok yeniden ölçülür) ve akış imleci ham içeriğe karışmaz
 * (kopyalanan kod temiz kalır). Compose'suz saf test.
 */
class StreamingMarkdownTest {

    /** Tamamlanmış bloklar, akış büyürken bayt bayt aynı kalır. */
    @Test
    fun akisSirasindaTamamlanmisBloklarDegismez() {
        val tamam = "önce paragraf\n\n- madde 1\n- madde 2\n\n```kotlin\nval x = 1\n```\n\n"
        val akan = tamam + "yeni paragraf hâlâ büyüyor"

        val b1 = parseMarkdown(tamam)
        val b2 = parseMarkdown(akan)

        // b1'in TÜM blokları b2'nin ön ekiyle eşit: yalnız son (yeni) blok değişti.
        assertTrue(b2.size > b1.size)
        b1.forEachIndexed { i, block -> assertEquals(block, b2[i]) }
    }

    /** Tamamlanmamış kod çitinin içinde metin büyürken blok tipi korunur. */
    @Test
    fun yarimKodCitiTekBlokOlarakBuyur() {
        val once = parseMarkdown("```python\nprint('a")
        val sonra = parseMarkdown("```python\nprint('abc")

        assertEquals(1, once.size)
        assertEquals(1, sonra.size)
        assertTrue(sonra[0] is MdBlock.Code)
        assertEquals("python", (sonra[0] as MdBlock.Code).language)
    }

    /** İmleç soneki YALNIZ akan son blokta gelir; kapanmış balonlar temiz. */
    @Test
    fun imleciYalnizAkanSonBlokAlir() {
        assertEquals(" ▌", streamCursorSuffix(streaming = true, isLastBlock = true))
        assertEquals("", streamCursorSuffix(streaming = true, isLastBlock = false))
        assertEquals("", streamCursorSuffix(streaming = false, isLastBlock = true))
    }

    /** Akan kod bloğunun kopyalanan içeriği imleçle BOZULMAZ (ham kalır). */
    @Test
    fun akanKodBlogununHamIcerigiTemizKalir() {
        val blocks = parseMarkdown("```js\nconst a = 1")
        val code = blocks.filterIsInstance<MdBlock.Code>().single()

        // Render imleci block.code'a ASLA yazmaz; imleç yalnız çizim soneki.
        assertFalse(code.code.contains("▌"))
        assertEquals("const a = 1", code.code)
    }
}
