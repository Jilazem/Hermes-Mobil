package com.hermes.mobile

import com.hermes.mobile.data.FullControl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K1 madde 1 — `tap` başarısızlık mesajı ekranda GÖRÜNEN etiketleri döker
 * (phone-harness "raises with what IS visible" deseni).
 */
class TapLabelsTest {

    @Test
    fun `bulunamadi mesaji gorunen etiketleri listeler`() {
        val msg = FullControl.tapNotFound(
            "\"Tamam\" metin/açıklaması",
            listOf("Ayarlar", "Hesaplar", "Yardım"),
        )
        assertTrue(msg, msg.contains("Ekranda bulunamadı: \"Tamam\" metin/açıklaması."))
        assertTrue(msg, msg.contains("Ekranda görünenler: Ayarlar | Hesaplar | Yardım."))
        assertTrue(msg, msg.endsWith("Önce screen.dump ile ekranı oku."))
    }

    @Test
    fun `etiket yoksa ek cikmaz ama mesaj yine aciklayicidir`() {
        val msg = FullControl.tapNotFound("id=com.paket:id/dugme", emptyList())
        assertFalse(msg, msg.contains("Ekranda görünenler"))
        assertTrue(msg, msg.endsWith("Önce screen.dump ile ekranı oku."))
    }

    @Test
    fun `etiket listesi alti ile sinirlanir`() {
        val s = FullControl.visibleSuffix(List(10) { "etiket-$it" })
        assertTrue(s, s.contains("etiket-5"))
        assertFalse(s, s.contains("etiket-6"))
        // Jest reddi mesajına da aynı ek gelir.
        assertTrue(FullControl.visibleSuffix(listOf("Ana sayfa")).contains("Ana sayfa"))
    }
}