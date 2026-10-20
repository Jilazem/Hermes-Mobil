package com.hermes.mobile

import com.hermes.mobile.data.CzipAutoLogic
import com.hermes.mobile.data.CzipAutoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V5-4 CZIP AUTO — `czip oto durum` / `czip oto aç` çıktısı ayrıştırma ve
 * yeni oturumdaki güvence adımları.
 */
class CzipAutoLogicTest {

    private val durumAcik = """
        czip settings (/home/user/.hermes/profiles/cd-android/czip/ayar.json)
          esik_oran : %50   (cap)
          kalan_token: 70,000   (pack when this much room is left)
          mutlak_token: off   (pack when prompt tokens hit this count)
             262k window -> fires at 131,072 (%50)  rule: esik_oran
          kademeler : %50, %75, %90
          oto       : ON   (on = decide and pack without asking)
          oto_pasif : off
          decision gate: off
    """.trimIndent()

    private val durumKapali = """
          kademeler : %50, %75, %90
          oto       : off   (on = decide and pack without asking)
          oto_pasif : ON
    """.trimIndent()

    @Test
    fun `durum tablosunda oto ON okunur`() {
        assertEquals(CzipAutoState.ON, CzipAutoLogic.state(durumAcik))
    }

    @Test
    fun `oto satiri oto_pasif ile karismaz`() {
        // oto: off ama oto_pasif: ON — yalnız "oto" anahtarı sayilir.
        assertEquals(CzipAutoState.OFF, CzipAutoLogic.state(durumKapali))
    }

    @Test
    fun `ayar yazma ciktisi okunur`() {
        assertEquals(CzipAutoState.ON, CzipAutoLogic.state("oto -> ON"))
        assertEquals(CzipAutoState.OFF, CzipAutoLogic.state("oto -> off"))
    }

    @Test
    fun `traceback ve bos cikti UNKNOWN — varsayim uydurulmaz`() {
        val traceback = """
            Traceback (most recent call last):
              File "hkp.py", line 1342, in _main_ici
                print(...) % _a.AYAR_YOLU)
            AttributeError: module 'ayar' has no attribute 'AYAR_YOLU'
        """.trimIndent()
        assertEquals(CzipAutoState.UNKNOWN, CzipAutoLogic.state(traceback))
        assertEquals(CzipAutoState.UNKNOWN, CzipAutoLogic.state(""))
        assertEquals(CzipAutoState.UNKNOWN, CzipAutoLogic.state("Czip error: baglanti yok"))
    }

    @Test
    fun `acikken ek komut yok`() {
        assertEquals(emptyList<String>(), CzipAutoLogic.ensureCommands(durumAcik))
    }

    @Test
    fun `kapaliyken ve taninmadiginda acma komutu gider`() {
        assertEquals(listOf(CzipAutoLogic.ENABLE_COMMAND), CzipAutoLogic.ensureCommands(durumKapali))
        assertEquals(listOf(CzipAutoLogic.ENABLE_COMMAND), CzipAutoLogic.ensureCommands(""))
    }

    @Test
    fun `komutlar czip ailesiyle birebir eslesir`() {
        // czip oto durum <-> /czip-auto durum ; czip oto ac <-> /czip-auto ac
        assertEquals("/czip-auto durum", CzipAutoLogic.STATUS_COMMAND)
        assertEquals("/czip-auto ac", CzipAutoLogic.ENABLE_COMMAND)
    }

    @Test
    fun `bildirim satiri durumu acik soyler`() {
        assertTrue(CzipAutoLogic.noticeTr(true).contains("AÇIK"))
        assertTrue(CzipAutoLogic.noticeEn(true).contains("ON"))
        assertTrue(CzipAutoLogic.noticeTr(false).contains("czip oto durum"))
    }
}