package com.hermes.mobile

import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.EmaAudioLogic
import com.hermes.mobile.data.EmaSpeechChoice
import com.hermes.mobile.data.toVoicePrefs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * İş #1021 K2 — SES/EMA kök neden + onarım birim testleri (APK derleme YOK).
 *
 * Kapatılan gerçek hatalar:
 * 1) Yedek ses WAV'ında sabit `offset = 44`: başlığı LIST/INFO bloğuyla uzayan
 *    dosyada metadata baytları hoparlöre yazılıyordu (bozuk/çatlak ses).
 * 2) 16-bit PCM parçaları tek bayt sınırında bölünüyordu → sonraki örneğin
 *    baytları kayıyor (cızırtı). Parçalar artık çift sınırda.
 * 3) EMA hattı hata verince `allowAndroidFallback = false` yüzünden hiç ses
 *    çıkmıyordu; ayrıca kullanıcıya "EMA / Sistem" seçimi sunulmuyordu.
 * 4) Örnek hızı değişince (EMA 48000 ↔ yedek Piper 22050) çıkış hattı yeniden
 *    kurulmuyordu → ses hızı/uzunluğu bozuk.
 */
class K2VoiceRepairTest {

    // ---- WAV yardımcıları -------------------------------------------------

    private fun wav(rate: Int, samples: ShortArray, extraHeaderBytes: ByteArray = ByteArray(0)): ByteArray {
        val dataLen = samples.size * 2
        val body = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { body.putShort(it) }
        val out = ByteBuffer.allocate(44 + extraHeaderBytes.size + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + extraHeaderBytes.size + dataLen).put("WAVE".toByteArray())
        out.put("fmt ".toByteArray()).putInt(16)
        out.putShort(1)               // PCM
        out.putShort(1)               // mono
        out.putInt(rate)
        out.putInt(rate * 2)          // byte rate
        out.putShort(2)               // block align
        out.putShort(16)              // bits
        out.put(extraHeaderBytes)
        out.put("data".toByteArray()).putInt(dataLen)
        out.put(body.array())
        return out.array()
    }

    /** "LIST" + boyut + gövde — gerçek Piper WAV'larında görülen ek blok. */
    private fun listChunk(payload: String): ByteArray {
        val p = payload.toByteArray()
        val out = ByteBuffer.allocate(8 + p.size + (p.size % 2)).order(ByteOrder.LITTLE_ENDIAN)
        out.put("LIST".toByteArray()).putInt(p.size).put(p)
        if (p.size % 2 == 1) out.put(0)
        return out.array()
    }

    // ---- 1) WAV gövdesi ve örnek hızı ------------------------------------

    @Test
    fun `duz wav govdesi ve hizi dogru okunur`() {
        val samples = shortArrayOf(0, 100, -100, 32767, -32768)
        val pcm = EmaAudioLogic.pcmFromWav(wav(22050, samples))
        assertNotNull("düz WAV çözümlenemedi", pcm)
        assertEquals(22050, pcm!!.rate)
        assertEquals(samples.size * 2, pcm.bytes.size)
        val back = ByteBuffer.wrap(pcm.bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val got = ShortArray(back.remaining()).also { back.get(it) }
        assertArrayEquals(samples, got)
    }

    @Test
    fun `liste bloklu wavda metadata ses diye calinmaz`() {
        val samples = shortArrayOf(1000, -1000, 2000, -2000)
        val list = listChunk("INFOISFT Lavf")
        val file = wav(48000, samples, list)
        val pcm = EmaAudioLogic.pcmFromWav(file)!!
        // Sabit 44 ofseti kullanan eski kod burada metadata baytlarını yazardı.
        val naive = file.copyOfRange(44, file.size)
        assertFalse("test kurgusu geçersiz: ek blok yok", naive.contentEquals(pcm.bytes))
        assertEquals("örnek hızı başlıktan okunmalı", 48000, pcm.rate)
        assertEquals(samples.size * 2, pcm.bytes.size)
        val back = ByteBuffer.wrap(pcm.bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val got = ShortArray(back.remaining()).also { back.get(it) }
        assertArrayEquals(samples, got)
    }

    @Test
    fun `cozulemeyen wav sessiz cop uretmez`() {
        assertNull(EmaAudioLogic.pcmFromWav(ByteArray(0)))
        assertNull(EmaAudioLogic.pcmFromWav(ByteArray(44) { 0x11 }))
    }

    // ---- 2) Parçalama hizası ---------------------------------------------

    @Test
    fun `parcalar cift bayt sinirinda kesilir`() {
        // Tek baytlık giriş: son yarım örnek atılır, hiçbir parça tek kalmaz.
        val pcm = ByteArray(4099) { it.toByte() }
        val chunks = EmaAudioLogic.evenChunks(pcm, 4096)
        assertEquals(2, chunks.size)
        chunks.forEach { assertEquals("tek baytlık parça 16-bit örneği böler", 0, it.size % 2) }
        assertEquals(pcm.size - 1, chunks.sumOf { it.size })
        // Birleştirme kayıpsız ve sıralı (yalnız son bayt düşer).
        val joined = ByteArray(pcm.size - 1)
        var off = 0
        chunks.forEach { it.copyInto(joined, off); off += it.size }
        assertArrayEquals(pcm.copyOf(pcm.size - 1), joined)
    }

    @Test
    fun `cift bayt girişte kayip yok`() {
        val pcm = ByteArray(4096) { it.toByte() }
        val chunks = EmaAudioLogic.evenChunks(pcm, 4096)
        assertEquals(1, chunks.size)
        assertArrayEquals(pcm, chunks[0])
    }

    @Test
    fun `tek boy istegi bir eksige iner`() {
        val chunks = EmaAudioLogic.evenChunks(ByteArray(100), 33)
        assertTrue(chunks.isNotEmpty())
        chunks.dropLast(1).forEach { assertEquals(32, it.size) }
        chunks.forEach { assertEquals(0, it.size % 2) }
    }

    @Test
    fun `bos pcm bos liste doner`() {
        assertTrue(EmaAudioLogic.evenChunks(ByteArray(0), 4096).isEmpty())
    }

    // ---- 3) Kare ve boşaltma süresi --------------------------------------

    @Test
    fun `kare ve bosaltma suresi hiza gore hesaplanir`() {
        assertEquals(0, EmaAudioLogic.frames(1))
        assertEquals(2048, EmaAudioLogic.frames(4096))
        // 48000 Hz'de 48000 kare = 1000 ms + pay.
        assertEquals(11_000L, EmaAudioLogic.drainDeadlineMs(48_000, 48_000, 10_000))
        // 22050 Hz'de aynı kare sayısı daha uzun sürer (eski sabit süre keserdi).
        assertTrue(EmaAudioLogic.drainDeadlineMs(48_000, 22_050, 10_000) > 11_000L)
        // Bozuk/0 hız çökmemeli.
        assertEquals(10_000L, EmaAudioLogic.drainDeadlineMs(0, 0, 10_000))
    }

    // ---- 4) "EMA / Sistem" seçimi ----------------------------------------

    @Test
    fun `sistem secimi emayi devre disi birakir`() {
        assertEquals(EmaSpeechChoice.Route.SYSTEM, EmaSpeechChoice.route("sistem", true))
        assertEquals(EmaSpeechChoice.Route.SYSTEM, EmaSpeechChoice.route(" SISTEM ", true))
        assertEquals("Sistem", EmaSpeechChoice.label(EmaSpeechChoice.Route.SYSTEM))
    }

    @Test
    fun `ema varsayilan ve yapilandirilmissa ema`() {
        assertEquals(EmaSpeechChoice.Route.EMA, EmaSpeechChoice.route("ema", true))
        assertEquals(EmaSpeechChoice.Route.EMA, EmaSpeechChoice.route("", true))
        assertEquals(EmaSpeechChoice.Route.EMA, EmaSpeechChoice.route("bilinmeyen", true))
        assertEquals("EMA", EmaSpeechChoice.label(EmaSpeechChoice.Route.EMA))
    }

    @Test
    fun `ema yapilandirilmadiysa sessizlik yok sistem sesi`() {
        assertEquals(EmaSpeechChoice.Route.SYSTEM, EmaSpeechChoice.route("ema", false))
        assertEquals("ses: Sistem (EMA yapılandırılmadı)", EmaSpeechChoice.describe("ema", false))
        assertEquals("ses: Sistem (telefon TTS)", EmaSpeechChoice.describe("sistem", true))
        assertEquals("ses: EMA", EmaSpeechChoice.describe("ema", true))
    }

    // ---- 5) Ayar taşıma --------------------------------------------------

    @Test
    fun `emaPlayback varsayilani ema ve VoicePrefs tasir`() {
        assertEquals("ema", AppSettings().emaPlayback)
        val s = AppSettings().copy(emaPlayback = EmaSpeechChoice.SYSTEM)
        assertEquals("sistem", s.emaPlayback)
        assertEquals("sistem", s.toVoicePrefs().emaPlayback)
        // Ayar diğer ses alanlarını bozmamalı.
        assertEquals(AppSettings().assistantAutoRead, s.assistantAutoRead)
    }
}
