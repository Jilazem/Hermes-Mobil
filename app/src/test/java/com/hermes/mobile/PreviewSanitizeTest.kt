package com.hermes.mobile

import com.hermes.mobile.ui.cutAtWord
import com.hermes.mobile.ui.isGenericIdentityTitle
import com.hermes.mobile.ui.isMachineNoise
import com.hermes.mobile.ui.meaningfulPreview
import com.hermes.mobile.ui.topicFromPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-4 kusur A/B — kart önizlemesi ARTIK ham makine çıktısı gösteremez.
 *
 * Emülatörde görülen gerçek örnekler (ekran görüntüsü kanıtı):
 *   {"status": "success", "output": "=== ESBLESME: /Users/gokhan… len 11050…"}
 *   [IMPORTANT: You are running as a scheduled cron job. DELIVER…]
 *
 * Kural: JSON/tool çıktısı, cron-sistem promptu ve markdown çiti ATILIR;
 * ilk anlamlı insan satırı gösterilir; hiç yoksa önizleme BOŞ kalır
 * (bir önceki anlamlı mesaja düşme kararı çağıran katmanda: `liveFeed`).
 */
class PreviewSanitizeTest {

    // ---- A) JSON / tool çıktısı -------------------------------------------

    @Test
    fun `ham json ciktisi onizleme olmaz`() {
        val raw = """{"status": "success", "output": "=== ESBLESME: /Users/gokhan/rapor len 11050"}"""
        assertTrue(isMachineNoise(raw))
        assertEquals("", meaningfulPreview(raw))
        assertEquals("", topicFromPreview(raw))
    }

    @Test
    fun `json dizi de makine ciktisidir`() {
        assertTrue(isMachineNoise("""[{"tool": "execute_code", "ok": true}]"""))
        assertEquals("", meaningfulPreview("""[{"tool": "execute_code", "ok": true}]"""))
    }

    @Test
    fun `tool sonucu govdesi ayiklanir ama metin varsa kurtarilir`() {
        val raw = """
            {"status": "success", "output": "dosya yazildi"}
            Rapor hazir, kontrol edebilirsin.
        """.trimIndent()
        // İlk satır ham JSON; ayıklama sonrası ilk anlamlı cümle kalır.
        assertEquals("Rapor hazir, kontrol edebilirsin.", meaningfulPreview(raw))
    }

    // ---- B) Cron / sistem promptu -----------------------------------------

    @Test
    fun `cron sistem mesaji onizlemede gosterilmez`() {
        val raw = "[IMPORTANT: You are running as a scheduled cron job. DELIVER this to the user.]"
        assertTrue(isMachineNoise(raw))
        assertEquals("", meaningfulPreview(raw))
    }

    @Test
    fun `sistem promptu isaretleri ayiklanir`() {
        assertTrue(isMachineNoise("You are Hermes, a personal AI agent."))
        assertTrue(isMachineNoise("<system>rol: uzman</system>"))
        assertTrue(isMachineNoise("""{"tool_call_id": "abc", "result": "ok"}"""))
    }

    @Test
    fun `cron oturumunun ikinci satiri kurtarilir`() {
        val raw = """
            [IMPORTANT: You are running as a scheduled cron job. DELIVER]
            Pavo saglik kontrolu tamamlandi.
        """.trimIndent()
        assertEquals("Pavo saglik kontrolu tamamlandi.", meaningfulPreview(raw))
    }

    // ---- Markdown / düzleştirme -------------------------------------------

    @Test
    fun `markdown susleri temizlenir`() {
        assertEquals("Başlık metni", meaningfulPreview("## Başlık metni"))
        assertEquals("madde", meaningfulPreview("- madde"))
        assertEquals("kod", meaningfulPreview("```kotlin\nkod"))
        assertEquals("sıra", meaningfulPreview("1. sıra"))
    }

    @Test
    fun `satir sonlari tek satira iner`() {
        assertEquals("ilk satir", meaningfulPreview("ilk satir\nikinci satir"))
    }

    @Test
    fun `bos ve yalniz noktalama girdisi bos doner`() {
        assertEquals("", meaningfulPreview(null))
        assertEquals("", meaningfulPreview("   "))
        assertEquals("", meaningfulPreview("---"))
        assertEquals("", meaningfulPreview("```"))
    }

    // ---- Kırpma ------------------------------------------------------------

    @Test
    fun `kelime sinirinda kirpilir`() {
        val out = cutAtWord("bir iki uc dort bes alti yedi sekiz dokuz on", 20)
        assertTrue("kabarcık taşı eklenir: $out", out.endsWith("…"))
        assertTrue(out.length <= 21)
        assertFalse(out.contains("  "))
    }

    @Test
    fun `kisa metin kirpilmaz`() {
        assertEquals("kisa metin", cutAtWord("kisa metin", 40))
    }

    @Test
    fun `konu 40 karakter civarinda kirpilir`() {
        val long = "Kamulaştırma raporundaki trees sayisini kontrol et ve duzelt"
        val topic = topicFromPreview(long)
        assertTrue("konu ~40 karakter: $topic", topic.length <= 41)
        assertTrue(topic.endsWith("…"))
    }

    // ---- Kimlik/kanal adı (kusur C) ---------------------------------------

    @Test
    fun `kisi ve kanal adlari konu degildir`() {
        assertTrue(isGenericIdentityTitle("Gökhan Uzman"))
        assertTrue(isGenericIdentityTitle("Gökhan Uzman".lowercase()))
        assertTrue(isGenericIdentityTitle("telegram"))
        assertTrue(isGenericIdentityTitle("desktop telegram"))
        assertTrue(isGenericIdentityTitle("Telegram Desktop"))
        assertTrue(isGenericIdentityTitle("default"))
        assertTrue(isGenericIdentityTitle("—"))
        assertTrue(isGenericIdentityTitle(null))
    }

    @Test
    fun `gercek konu basliklari korunur`() {
        assertFalse(isGenericIdentityTitle("Bilirkişi Yılmaz Altın İfade Hataları"))
        assertFalse(isGenericIdentityTitle("Korkuteli yukarıkaraman 112 ada 7 parsel"))
        assertFalse(isGenericIdentityTitle("Uyap Giriş"))
        assertFalse(isGenericIdentityTitle("Session List"))
    }
}
