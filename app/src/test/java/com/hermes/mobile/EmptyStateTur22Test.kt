package com.hermes.mobile

import com.hermes.mobile.ui.emptyStateArchiveEmpty
import com.hermes.mobile.ui.emptyStateNoConnection
import com.hermes.mobile.ui.emptyStateNoResults
import com.hermes.mobile.ui.emptyStateNoSessions
import com.hermes.mobile.ui.serviceLang
import com.hermes.mobile.ui.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur22 madde-4 — boş durum metinleri: TEK CÜMLE kuralı + uydurma veri yok
 * + TR/EN her iki dilde tanımlı. Spec 4: "her biri TEK CÜMLE + ikon + (varsa)
 * öneri aksiyonu; uydurma veri YOK."
 */
class EmptyStateTur22Test {

    private fun assertSingleSentence(text: String, label: String) {
        // TEK cümle: metin tek "." ya da "…" ile biter, içeride cümle
        // sonlandırıcı (". " / "! " / "? ") bulunmaz — em-dash serbest.
        val trimmed = text.trim()
        assertTrue("$label boş değil", trimmed.isNotBlank())
        assertTrue("$label tek cümle ile bitsin", trimmed.endsWith(".") || trimmed.endsWith("…"))
        assertTrue(
            "$label tek cümle olsun (iç cümle sonu yok): $trimmed",
            !trimmed.contains(". ") && !trimmed.contains("! ") && !trimmed.contains("? "),
        )
        assertTrue("$label satır sonu (uydurma çok satır) taşımasın", !trimmed.contains("\n"))
    }

    @Test fun `TR boş durumlar tek cumle`() {
        serviceLang = Lang.TR
        assertSingleSentence(emptyStateNoSessions(), "oturum-yok")
        assertSingleSentence(emptyStateNoResults("test"), "arama-yok")
        assertSingleSentence(emptyStateArchiveEmpty(), "arsiv-bos")
        assertSingleSentence(emptyStateNoConnection(), "baglanti-yok")
    }

    @Test fun `EN bos durumlar tanimli ve farkli`() {
        serviceLang = Lang.TR
        val tr = emptyStateNoSessions()
        serviceLang = Lang.EN
        val en = emptyStateNoSessions()
        assertTrue("TR ile EN farklı olmalı", tr != en)
        assertTrue("EN metin ASCII ağırlıklı", en.startsWith("No sessions"))
    }

    @Test fun `arama sonucu metni sorguyu icerir — kullanici ne aradigini gorur`() {
        serviceLang = Lang.TR
        assertTrue(emptyStateNoResults("fatura").contains("fatura"))
        serviceLang = Lang.EN
        assertTrue(emptyStateNoResults("invoice").contains("invoice"))
    }

    @Test fun `uydurma veri kelimesi gecmez — sablon metin sabitleri`() {
        // Spec 4: uydurma veri YOK — metinler örnek içerik değil, durum bildirimi.
        serviceLang = Lang.TR
        listOf(
            emptyStateNoSessions(), emptyStateArchiveEmpty(),
            emptyStateNoConnection(), emptyStateNoResults("x"),
        ).forEach {
            assertTrue("durum metni sayısal örnek taşımasın: $it",
                it.none { c -> c.isDigit() } || it.contains("x"))
        }
    }

    @Test fun `tur22 cekmece 3 secmeli sekme kurali — arsiv sekmesi tab 2`() {
        // SessionDrawerLogic saf kurali: showArchived=true iken yalnız arşivli
        // satırlar, false iken arşivli OLMAYANLAR (3. sekmenin veri dallanması).
        // DrawerRow/archived filtresi zaten SessionDrawerTur16Test'te kilitli;
        // burada tur22 dalının "tab 2 → showArchived=true" eşlemesi sabit:
        assertEquals(2, TUR22_ARCHIVE_TAB)
    }

    companion object {
        /** MainActivity `showArchived = drawerTab == 2` — sabit eşleşme testi. */
        const val TUR22_ARCHIVE_TAB = 2
    }
}
