package com.hermes.mobile

import com.hermes.mobile.data.SavedPromptsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Kayıtlı prompt deposu — kaydet/güncelle/sil ve kalıcılık.
 *
 * Store'un `File` kurucusu sınamada kullanılıyor: Android Context'ü olmadan,
 * geçici dizin üzerinde gerçek JSON yığınıyla çalışıyor (SessionFlagsStore'un
 * en iyi çaba ilkesi burada da sınanıyor — bozuk dosya akışı kırmamalı).
 */
class SavedPromptsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = SavedPromptsStore(tmp.newFolder())

    @Test
    fun `kaydet yukle ile doner`() {
        val s = store()
        val p = s.kaydet("Rapor özeti", "Son raporu özetle")
        val liste = s.yukle()
        assertEquals(1, liste.size)
        assertEquals(p.id, liste.first().id)
        assertEquals("Rapor özeti", liste.first().label)
        assertEquals("Son raporu özetle", liste.first().text)
    }

    @Test
    fun `etiket bos ise ilk satirdan turetilir`() {
        val s = store()
        val p = s.kaydet("   ", "  Çok satırlı\nistek metni  ")
        assertEquals("Çok satırlı", p.label)
        assertEquals("Çok satırlı\nistek metni", p.text)
    }

    @Test
    fun `etiket uzunsa ilk satirin kirpilmis hali kalir`() {
        val s = store()
        val uzun = "x".repeat(50)
        val p = s.kaydet("", uzun)
        assertEquals(32, p.label.length)
    }

    @Test
    fun `guncelle kaydi yerinde degistirir`() {
        val s = store()
        val a = s.kaydet("a", "birinci")
        s.kaydet("b", "ikinci")
        s.guncelle(a.id, "A", "birinci v2")
        val liste = s.yukle()
        // Sıra korunur: güncelleme yerini değiştirmez.
        assertEquals(listOf(a.id, liste[1].id), liste.map { it.id })
        assertEquals("A", liste[0].label)
        assertEquals("birinci v2", liste[0].text)
    }

    @Test
    fun `olmayan id guncelle ve sil sessiz gecilir`() {
        val s = store()
        s.kaydet("x", "metin")
        s.guncelle("yok-id", "z", "z")
        s.sil("yok-id")
        assertEquals(1, s.yukle().size)
    }

    @Test
    fun `sil kaydi kaldirir`() {
        val s = store()
        val a = s.kaydet("a", "bir")
        s.kaydet("b", "iki")
        s.sil(a.id)
        val liste = s.yukle()
        assertEquals(1, liste.size)
        assertEquals("b", liste.first().label)
    }

    @Test
    fun `yeni store ayni dizinden geri okur`() {
        val dir = tmp.newFolder()
        SavedPromptsStore(dir).kaydet("kalici", "diskte kalsın")
        // Ayni dizine bakan ikinci örnek — kalıcılık sınanıyor.
        val ikinci = SavedPromptsStore(dir)
        assertEquals(listOf("kalici"), ikinci.yukle().map { it.label })
    }

    @Test
    fun `bozuk dosya akisi kirmez, ilk kaydet yigini kurar`() {
        val dir = tmp.newFolder()
        java.io.File(dir, "saved-prompts.json").writeText("{bozuk json!!")
        val s = SavedPromptsStore(dir)
        assertTrue(s.yukle().isEmpty())
        s.kaydet("kurtarma", "metin")
        assertEquals(1, s.yukle().size)
    }
}
