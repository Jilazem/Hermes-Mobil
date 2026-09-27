package com.hermes.mobile

import com.hermes.mobile.assistant.JarvisLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Seslenme kelimesi ayarı + sesli Hermes komutu (tur25.3).
 *
 * Tetikleyici TFLite modeli `hey_jarvis_v0.1`; kullanıcının ayardaki kelimesi
 * STT sonrası **doğrulama** ve komuttan soyma için kullanılır. Buradaki
 * sözleşme: farklı yazımlar eşleşir, komut soyulur, komut yoksa null döner.
 */
class WakeWordSettingTest {

    @Test
    fun `seslenme kelimesi basinda ise komut soyulur`() {
        assertEquals("hava durumu", JarvisLogic.stripWake("hey kitt hava durumu", "hey kitt"))
        assertEquals("hava durumu", JarvisLogic.stripWake("Hey KITT, hava durumu", "hey kitt"))
        // Türkçe katlama: büyük I/ı, noktalama, fazla boşluk.
        assertEquals("yap", JarvisLogic.stripWake("KİTT yap", "kitt"))
    }

    @Test
    fun `kelime baslamiyorsa null — normal soru ayara karismaz`() {
        assertNull(JarvisLogic.stripWake("hava durumu", "hey kitt"))
        assertNull(JarvisLogic.stripWake("kedi ne yiyor", "hey kitt"))
    }

    @Test
    fun `yalniz seslenme kelimesi kalirsa komut yok`() {
        assertNull(JarvisLogic.stripWake("hey kitt", "hey kitt"))
        assertNull(JarvisLogic.stripWake("hey kitt...", "hey kitt"))
    }

    @Test
    fun `bos ayar hicbir seyi kirpmaz`() {
        assertNull(JarvisLogic.stripWake("her sey", ""))
    }

    @Test
    fun `buna cevap verme slash komutuna doner`() {
        assertEquals(
            "/steer buna cevap verme",
            JarvisLogic.voiceSlash("buna cevap verme"),
        )
        assertEquals("/steer devam et", JarvisLogic.voiceSlash("devam et"))
    }

    @Test
    fun `ayri dal ac branche doner`() {
        assertEquals("/branch", JarvisLogic.voiceSlash("ayri dal ac"))
        assertEquals("/branch", JarvisLogic.voiceSlash("Ayrı Dal Aç"))
    }

    @Test
    fun `geri al undo komutuna doner`() {
        assertEquals("/undo", JarvisLogic.voiceSlash("geri al"))
    }

    @Test
    fun `duyulan metin slash komutu degilse null`() {
        assertNull(JarvisLogic.voiceSlash("hava durumu nasil"))
        assertNull(JarvisLogic.voiceSlash(""))
    }
}
