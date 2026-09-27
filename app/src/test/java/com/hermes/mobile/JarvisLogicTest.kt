package com.hermes.mobile

import com.hermes.mobile.assistant.JarvisLogic
import com.hermes.mobile.assistant.JarvisPhase
import com.hermes.mobile.assistant.RecognizerPicker
import com.hermes.mobile.assistant.TtsCatalog
import com.hermes.mobile.ui.segmentIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jarvis sesli asistan: sesle fark edilecek her karar burada kilitli. */
class JarvisLogicTest {

    @Test
    fun `durdurma sozleri kisa ve tek basina`() {
        assertTrue(JarvisLogic.isStopPhrase("Teşekkürler"))
        assertTrue(JarvisLogic.isStopPhrase("tamam teşekkürler"))
        assertTrue(JarvisLogic.isStopPhrase("Kapat."))
        assertTrue(JarvisLogic.isStopPhrase("sağ ol"))
        assertFalse(JarvisLogic.isStopPhrase("teşekkürler ama yarın için de alarm kur"))
        assertFalse(JarvisLogic.isStopPhrase("kapatma saatini söyle bana lütfen şimdi"))
        assertFalse(JarvisLogic.isStopPhrase(""))
    }

    @Test
    fun `ekran sorulari algilanir digerleri gonderilmez`() {
        assertTrue(JarvisLogic.needsScreen("ekranda ne var"))
        assertTrue(JarvisLogic.needsScreen("bunu özetle"))
        assertTrue(JarvisLogic.needsScreen("Bu sayfada ne yazıyor"))
        assertTrue(JarvisLogic.needsScreen("bu mesaja ne cevap vereyim"))
        assertFalse(JarvisLogic.needsScreen("yarın hava nasıl"))
        assertFalse(JarvisLogic.needsScreen("annemi ara"))
        val q = JarvisLogic.withScreen("bunu özetle", "Başlık\nMetin", "com.android.chrome")
        assertTrue(q.contains("com.android.chrome") && q.endsWith("bunu özetle"))
        assertEquals("soru", JarvisLogic.withScreen("soru", "  ", "x"))
    }

    @Test
    fun `cumle bolucu akisi bekletmeden cumle verir`() {
        val sp = JarvisLogic.SentenceSplitter(minChars = 10)
        assertEquals(emptyList<String>(), sp.push("Yarın hava parçalı bul"))
        assertEquals(listOf("Yarın hava parçalı bulutlu."), sp.push("utlu. En yük"))
        assertEquals(emptyList<String>(), sp.push("sek 22 derece"))
        assertEquals("En yüksek 22 derece", sp.flush())
        assertNull(sp.flush())
    }

    @Test
    fun `cumle bolucu sayilari ve kisaltmalari bolmez kisalari birlestirir`() {
        val sp = JarvisLogic.SentenceSplitter(minChars = 20)
        val out = sp.push("Fiyat 3.5 TL, vb. ürünler var. Evet. Tamam, sonra görüşürüz. ")
        assertEquals(listOf("Fiyat 3.5 TL, vb. ürünler var.", "Evet. Tamam, sonra görüşürüz."), out)
    }

    @Test
    fun `sesli okuma icin temizlik`() {
        assertEquals("Merhaba, dünya", JarvisLogic.speakable("**Merhaba** · dünya"))
        assertEquals("Bak bağlantı", JarvisLogic.speakable("Bak https://x.com/a?b=1"))
        assertEquals("Liste. bir. iki", JarvisLogic.speakable("Liste\n- bir\n- iki"))
        assertEquals("Tamam", JarvisLogic.speakable("Tamam 👍"))
    }

    @Test
    fun `oturum 30 dk icinde yeniden kullanilir`() {
        assertTrue(JarvisLogic.sessionReusable(1_000, 1_000 + 29 * 60_000L, "s1"))
        assertFalse(JarvisLogic.sessionReusable(1_000, 1_000 + 31 * 60_000L, "s1"))
        assertFalse(JarvisLogic.sessionReusable(1_000, 2_000, null))
    }

    @Test
    fun `surekli sohbet bir sessiz denemeden sonra durur`() {
        assertTrue(JarvisLogic.shouldListenAgain(true, 0))
        assertFalse(JarvisLogic.shouldListenAgain(true, 1))
        assertFalse(JarvisLogic.shouldListenAgain(false, 0))
    }

    @Test
    fun `hitap yonergesi`() {
        assertTrue(JarvisLogic.voicePrefix("efendim").contains("\"efendim\""))
        assertFalse(JarvisLogic.voicePrefix(" ").contains("hitap"))
    }

    @Test
    fun `taniyici siralamasi kendimizi disarida birakir google once`() {
        val r = RecognizerPicker.rank(
            listOf("com.hermes.mobile.v3", "com.samsung.android.bixby.agent", "com.google.android.googlequicksearchbox", "x.y"),
            self = "com.hermes.mobile.v3",
        )
        assertEquals(listOf("com.google.android.googlequicksearchbox", "com.samsung.android.bixby.agent", "x.y"), r)
    }

    @Test
    fun `kitt tarayici dusunurken supurur dinlerken ortadan acilir`() {
        // Düşünürken en parlak nokta süpürme konumunda.
        assertTrue(segmentIntensity(JarvisPhase.Thinking, 0.3f, 0.3f, 0f, 0.2f) > 0.99f)
        assertTrue(segmentIntensity(JarvisPhase.Thinking, 0.8f, 0.3f, 0f, 0.2f) < 0.01f)
        // Sessizken yalnız orta yanar; ses yükselince kenarlar da yanar.
        assertTrue(segmentIntensity(JarvisPhase.Listening, 0.5f, 0f, 0f, 0.2f) > 0.9f)
        assertTrue(segmentIntensity(JarvisPhase.Listening, 0.95f, 0f, 0f, 0.2f) < 0.1f)
        assertTrue(segmentIntensity(JarvisPhase.Listening, 0.95f, 0f, 1f, 0.2f) > 0.3f)
        assertEquals(0.2f, segmentIntensity(JarvisPhase.Idle, 0.1f, 0f, 0f, 0.2f), 1e-6f)
    }

    @Test
    fun `telefon ses adlari okunur hale gelir`() {
        assertEquals("Ses MFS · cihazda", TtsCatalog.voiceLabel("tr-tr-x-mfs-local", false))
        assertEquals("Ses EFS · internetle", TtsCatalog.voiceLabel("tr-tr-x-efs-network", true))
        assertEquals("Varsayılan · cihazda", TtsCatalog.voiceLabel("tr-TR-language", false))
    }
}
