package com.hermes.mobile

import com.hermes.mobile.data.LocalTtsLogic
import com.hermes.mobile.data.VoiceSpeakLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tur-21 — yerel TTS karar katmanı (saf).
 *
 * Model dosyaları burada DOKUNULMAZ (hash'ler 63MB). Yalnız sözleşme ve
 * durum makinesi sınanır: dosya listesi, yüzde, fallback zinciri, silme
 * (boş dizinde 0 döner).
 */
class LocalTtsLogicTest {

    private val t: (String, String) -> String = { tr, _ -> tr }

    @Test
    fun `model dosyalari sha256 ile sabit`() {
        // sha256 64 hex karakter ve boşluk/karışık küçük harf olabilir.
        assertTrue(LocalTtsLogic.FILES.isNotEmpty())
        for (f in LocalTtsLogic.FILES) {
            assertEquals(
                "sha256 64 hex olmalı: ${f.relPath}",
                64,
                f.sha256.length,
            )
            assertTrue(
                "hex bekleniyor: ${f.relPath}",
                f.sha256.matches(Regex("^[0-9a-f]{64}$")),
            )
            assertTrue("boyut > 0 olmalı", f.bytes > 0)
        }
    }

    @Test
    fun `toplam boyat 60-70 MB bandinda`() {
        val total = LocalTtsLogic.TOTAL_BYTES
        // 63.2 MB onnx + 7 espeak dosyası ≈ 63.4 MB; 60-70 MB aralığı mantıklı.
        assertTrue("beklenen ~63MB, gerçek: $total", total in 60_000_000L..70_000_000L)
    }

    @Test
    fun `ilerleme yuzdesi 0-99 arasi kisi`() {
        assertEquals(0, LocalTtsLogic.percent(0L))
        assertEquals(0, LocalTtsLogic.percent(-5L))
        val mid = LocalTtsLogic.percent(LocalTtsLogic.TOTAL_BYTES / 2)
        assertTrue(mid in 40..59)
        // Tamamlandığında 100 DEĞİL 99 gösterilir (bar), 100 yalnız Ready fazı taşır.
        assertEquals(99, LocalTtsLogic.percent(LocalTtsLogic.TOTAL_BYTES))
        assertEquals(99, LocalTtsLogic.percent(LocalTtsLogic.TOTAL_BYTES + 999_999))
    }

    @Test
    fun `durum satirlari faz basina farkli`() {
        assertTrue(
            LocalTtsLogic.statusLine(LocalTtsLogic.State(LocalTtsLogic.Phase.Ready), t)
                .contains("hazır ✓"),
        )
        assertTrue(
            LocalTtsLogic.statusLine(LocalTtsLogic.State(LocalTtsLogic.Phase.NotInstalled), t)
                .contains("63 MB"),
        )
        val failed = LocalTtsLogic.State(LocalTtsLogic.Phase.Failed, message = "ağ yok")
        assertEquals("ağ yok", LocalTtsLogic.statusLine(failed, t))
    }

    @Test
    fun `fallback karari — yerel secili + model yoksa buluta GECMEZ`() {
        val dec = LocalTtsLogic.decideSpeak(VoiceSpeakLogic.Engine.YEREL, localReady = false, t = t)
        assertFalse("sessiz bulut geçişi YOK", dec.useLocal)
        assertNotNull("açık hata üretilmeli", dec.error)
        // Hata metri bulut motoru adını içermemeli; kullanıcı indirmeye yönlendirilmeli.
        assertTrue(dec.error!!.contains("Ayarlar") || dec.error!!.contains("bulut geçişi"))
    }

    @Test
    fun `fallback karari — yerel secili + model hazirsa yerel uretir`() {
        val dec = LocalTtsLogic.decideSpeak(VoiceSpeakLogic.Engine.YEREL, localReady = true, t = t)
        assertTrue(dec.useLocal)
        assertNull(dec.error)
    }

    @Test
    fun `fallback karari — bulut hatasi + model varsa YERELE DUSER`() {
        val err = RuntimeException("502 gateway")
        val dec = LocalTtsLogic.decideSpeak(VoiceSpeakLogic.Engine.KAHYA, localReady = true, cloudError = err, t = t)
        assertTrue("model hazırsa yerel denenecek", dec.useLocal)
        assertTrue("düşüş bayrağı", dec.fellBack)
        assertNull(dec.error)
    }

    @Test
    fun `fallback karari — bulut hatasi + model yoksa hata kalir`() {
        val err = RuntimeException("502 gateway")
        val dec = LocalTtsLogic.decideSpeak(VoiceSpeakLogic.Engine.KADIN, localReady = false, cloudError = err, t = t)
        assertFalse(dec.useLocal)
        assertNotNull(dec.error)
    }

    @Test
    fun `silme yalniz alt agaci sayar, yoksa sifir`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "t21-sil-${System.nanoTime()}")
        val deep = File(dir, "a/b").apply { mkdirs() }
        File(deep, "x.bin").writeText("x")
        val n = LocalTtsLogic.deleteModel(dir)
        assertEquals(1, n)
        assertFalse(dir.exists())
        // Zaten yok → 0, patlamaz.
        assertEquals(0, LocalTtsLogic.deleteModel(dir))
    }

    @Test
    fun `sha tutmama fail-closed`() {
        val e = runCatching {
            LocalTtsLogic.requireSha("a".repeat(64), "b".repeat(64), "deneme.bin")
        }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e!!.message.orEmpty().contains("SHA256 uyuşmadı"))
    }
}
