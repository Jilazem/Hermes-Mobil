package com.hermes.mobile

import com.hermes.mobile.assistant.JarvisLogic
import com.hermes.mobile.assistant.WakeWordLogic
import com.hermes.mobile.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Seslenme kelimesi ayarı + sesli Hermes komutu (tur25.3).
 *
 * TETİKLEYİCİ MODEL SABİTTİR: openWakeWord `hey_jarvis_v0.1` yalnız
 * [WakeWordLogic.TRIGGER_PHRASE] sesini tanır. Ayar (`AppSettings.wakeWord`)
 * komutu ayırmak içindir; tetikleyiciden ayrı bir kelimeye ayarlanırsa
 * uygulama o kelimeyi duysanız bile UYANMAZ. Varsayılan bu yüzden
 * tetikleyicinin kendisidir.
 */
class WakeWordSettingTest {

    @Test
    fun `varsayilan ayar gercek tetikleyici kelimeyle ayni`() {
        // Ayar tetikleyiciden ayrı giderse tetikleyici yine "hey jarvis"tir:
        // kullanıcı "hey kitt" yazarsa uygulama hiç uyanmaz.
        assertEquals(WakeWordLogic.TRIGGER_PHRASE, AppSettings().wakeWord)
    }

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
