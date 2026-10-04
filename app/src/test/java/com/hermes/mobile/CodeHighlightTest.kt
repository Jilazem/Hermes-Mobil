package com.hermes.mobile

import com.hermes.mobile.ui.CodeTokenKind
import com.hermes.mobile.ui.tokenizeCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TUR-29A madde 3 — kural tabanlı kod renklendirme (compose-highlight deseni,
 * harici bağımlılık yok). Saf tokenizör testleri: akış toleransı (yarım dizge
 * / yarım yorum güvenli) ve token sınırları.
 */
class CodeHighlightTest {

    private fun kindsOf(code: String) = tokenizeCode(code).map { it.kind }

    /** Anahtar kelime, dizge, sayı ve yorum ayrışır. */
    @Test
    fun temelTokenTurleriAyrisir() {
        val kinds = kindsOf("val x = 42 // toplam")
        assertTrue(CodeTokenKind.Keyword in kinds)
        assertTrue(CodeTokenKind.Number in kinds)
        assertTrue(CodeTokenKind.Comment in kinds)
    }

    /** Dizge içindeki anahtar kelime dizge olarak işaretlenir (tek token). */
    @Test
    fun dizgeIciSozcukBozulmaz() {
        val spans = tokenizeCode("print(\"val\")")
        val stringSpan = spans.single { it.kind == CodeTokenKind.String }
        // "print(" dizgesi içinde: tırnaklar dahil 6..11 (end exclusive)
        assertEquals(6, stringSpan.start)
        assertEquals(11, stringSpan.end)
    }

    /** Yarım dizge (akış anı): satır sonunda biter, istisna yok. */
    @Test
    fun yarimDizgeGuvenli() {
        val spans = tokenizeCode("val s = \"abc")
        assertTrue(spans.any { it.kind == CodeTokenKind.String })
        // Tüm aralıklar kod sınırları içinde.
        spans.forEach { assertTrue(it.end <= 12) }
    }

    /** Kapanmamış blok yorum EOF'a kadar boyanır, döngüye girmez. */
    @Test
    fun yarimBlokYorumGuvenli() {
        val code = "/* hâlâ açık yorum"
        val spans = tokenizeCode(code)
        val comment = spans.single { it.kind == CodeTokenKind.Comment }
        assertEquals(0, comment.start)
        assertEquals(code.length, comment.end)
    }

    /** Ek açıklama tanınır: @Composable. */
    @Test
    fun ekAciklamaTaninir() {
        assertTrue(CodeTokenKind.Annotation in kindsOf("@Composable fun x()"))
    }

    /** Kaçış karakterli dizge kapanır: "a\"b" tek dizge. */
    @Test
    fun kacisliDizgeTekToken() {
        val code = "\"a\\\"b\""
        val strings = tokenizeCode(code).filter { it.kind == CodeTokenKind.String }
        assertEquals(1, strings.size)
        assertEquals(code.length, strings[0].end)
    }
}
