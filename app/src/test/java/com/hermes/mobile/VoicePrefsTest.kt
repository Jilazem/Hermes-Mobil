package com.hermes.mobile

import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.VoiceSpeakLogic
import com.hermes.mobile.data.toVoicePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-11 — ses ayarlarının varsayılanları ve tercihlere çözülmesi.
 *
 * Görev şartı: "otomatik gönder" **varsayılan KAPALI**. Tur-21: motor
 * varsayılanı **YEREL** (gizlilik — telefon verisi buluta çıkmaz).
 * Yanlış anlaşılan bir cümle kendiliğinden ajana gitmemeli.
 */
class VoicePrefsTest {

    @Test
    fun `varsayilanlar kapali gonder ve yerel motor`() {
        val s = AppSettings()
        assertFalse(s.voiceAutoSend)
        assertEquals("yerel", s.voiceEngine)
        assertEquals("", s.voiceUrl)
        assertEquals("", s.voiceLastOk)
    }

    @Test
    fun `tercihler ayardan cozulur`() {
        val p = AppSettings(
            voiceAutoSend = true,
            voiceEngine = "kadin",
            voiceUrl = "http://10.0.2.2:8174",
            voiceLastOk = "http://192.168.1.101:8174",
        ).toVoicePrefs()
        assertTrue(p.autoSend)
        assertEquals(VoiceSpeakLogic.Engine.KADIN, p.engine)
        assertEquals("http://10.0.2.2:8174", p.url)
        assertEquals("http://192.168.1.101:8174", p.lastOk)
    }

    @Test
    fun `bilinmeyen motor kimligi varsayilana duser`() {
        val p = AppSettings(voiceEngine = "yok-boyle-motor").toVoicePrefs()
        assertEquals(VoiceSpeakLogic.Engine.YEREL, p.engine)
    }

    @Test
    fun `tercihlerin varsayilani da kapali`() {
        val p = com.hermes.mobile.data.VoicePrefs()
        assertFalse(p.autoSend)
        assertEquals(VoiceSpeakLogic.Engine.YEREL, p.engine)
    }
}
