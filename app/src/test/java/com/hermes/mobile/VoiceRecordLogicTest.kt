package com.hermes.mobile

import com.hermes.mobile.data.VoiceApiEndpoints
import com.hermes.mobile.data.VoiceRecordLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-11 — kayıt durum makinesi (bas-konuş).
 *
 * Sözleşme: ses yükleme **<= 60 sn** → kayıt 60 sn'de kendiliğinden durur ve
 * yükleme başlar; parmak hâlâ basılı olsa bile. 0,8 sn'den kısa basış kazara
 * sayılır, hiç yüklenmez.
 */
class VoiceRecordLogicTest {

    private val t: (String, String) -> String = { tr, _ -> tr }

    private val idle = VoiceRecordLogic.State()

    @Test
    fun `bastan kayit baslar ve zaman damgasi tutulur`() {
        val s = VoiceRecordLogic.start(idle, nowMs = 1_000)
        assertEquals(VoiceRecordLogic.Phase.Recording, s.phase)
        assertEquals(1_000L, s.startedAtMs)
        assertEquals(0L, s.elapsedMs)
        assertTrue(s.recording)
    }

    @Test
    fun `kayit surerken ikinci basis yok sayilir`() {
        val s1 = VoiceRecordLogic.start(idle, 1_000)
        val s2 = VoiceRecordLogic.start(s1, 5_000)
        assertEquals(1_000L, s2.startedAtMs)
        assertTrue(s2.recording)
    }

    @Test
    fun `hatali durumdan yeniden baslanabilir`() {
        val failed = VoiceRecordLogic.failed(VoiceRecordLogic.State(), "mikrofon yok")
        val s = VoiceRecordLogic.start(failed, 42)
        assertEquals(VoiceRecordLogic.Phase.Recording, s.phase)
        assertEquals(null, s.message)
    }

    @Test
    fun `sure tazelenir ve 60 sn tavaninda durur`() {
        val s = VoiceRecordLogic.tick(VoiceRecordLogic.start(idle, 0), 12_000)
        assertEquals(12_000L, s.elapsedMs)
        val clamped = VoiceRecordLogic.tick(s, 99_000)
        assertEquals(VoiceApiEndpoints.MAX_RECORD_MS, clamped.elapsedMs)
    }

    @Test
    fun `kayit yokken tik bir sey yapmaz`() {
        assertEquals(idle, VoiceRecordLogic.tick(idle, 5_000))
        assertEquals(
            VoiceRecordLogic.Phase.Transcribing,
            VoiceRecordLogic.tick(
                VoiceRecordLogic.State(VoiceRecordLogic.Phase.Transcribing),
                5_000,
            ).phase,
        )
    }

    @Test
    fun `60 sn dolunca kendiliginden durur`() {
        val s = VoiceRecordLogic.start(idle, 0)
        assertFalse(VoiceRecordLogic.autoStopReached(s, 59_999))
        assertTrue(VoiceRecordLogic.autoStopReached(s, 60_000))
        assertTrue(VoiceRecordLogic.autoStopReached(s, 61_500))
    }

    @Test
    fun `kayit yokken otomatik durma olmaz`() {
        assertFalse(VoiceRecordLogic.autoStopReached(idle, 60_000))
    }

    @Test
    fun `uzun basista yukleme karari cikar`() {
        val s = VoiceRecordLogic.start(idle, 0)
        val (next, action) = VoiceRecordLogic.release(VoiceRecordLogic.tick(s, 4_000), 4_000)
        assertEquals(VoiceRecordLogic.ReleaseAction.Upload, action)
        assertEquals(VoiceRecordLogic.Phase.Transcribing, next.phase)
        assertEquals(4_000L, next.elapsedMs)
        assertTrue(next.busy)
    }

    @Test
    fun `kisa basis atilir`() {
        val s = VoiceRecordLogic.start(idle, 0)
        val (next, action) = VoiceRecordLogic.release(VoiceRecordLogic.tick(s, 300), 300)
        assertEquals(VoiceRecordLogic.ReleaseAction.Discard, action)
        assertEquals(VoiceRecordLogic.Phase.Idle, next.phase)
    }

    @Test
    fun `tam sinir 800 ms yuklemeye gider`() {
        val s = VoiceRecordLogic.start(idle, 0)
        val (_, action) = VoiceRecordLogic.release(s, VoiceRecordLogic.MIN_MILLIS)
        assertEquals(VoiceRecordLogic.ReleaseAction.Upload, action)
        val (_, below) = VoiceRecordLogic.release(s, VoiceRecordLogic.MIN_MILLIS - 1)
        assertEquals(VoiceRecordLogic.ReleaseAction.Discard, below)
    }

    @Test
    fun `kayit yokken birakma hicbir sey yapmaz`() {
        val (next, action) = VoiceRecordLogic.release(idle, 4_000)
        assertEquals(VoiceRecordLogic.ReleaseAction.Ignore, action)
        assertEquals(idle, next)
    }

    @Test
    fun `metinlestirme surerken birakma yok sayilir`() {
        val busy = VoiceRecordLogic.State(VoiceRecordLogic.Phase.Transcribing)
        val (next, action) = VoiceRecordLogic.release(busy, 4_000)
        assertEquals(VoiceRecordLogic.ReleaseAction.Ignore, action)
        assertEquals(VoiceRecordLogic.Phase.Transcribing, next.phase)
    }

    @Test
    fun `basarili metinlestirme durumu sifirlar`() {
        val done = VoiceRecordLogic.transcribed(VoiceRecordLogic.State(VoiceRecordLogic.Phase.Transcribing))
        assertEquals(VoiceRecordLogic.Phase.Idle, done.phase)
        assertEquals(null, done.message)
    }

    @Test
    fun `hata mesaji tasinir`() {
        val f = VoiceRecordLogic.failed(idle, "Ses ucu reddetti (403)")
        assertEquals(VoiceRecordLogic.Phase.Failed, f.phase)
        assertEquals("Ses ucu reddetti (403)", f.message)
    }

    @Test
    fun `iptal kaydi temizler`() {
        val s = VoiceRecordLogic.cancelled()
        assertEquals(VoiceRecordLogic.Phase.Idle, s.phase)
        assertEquals(0L, s.elapsedMs)
    }

    @Test
    fun `sayaç etiketi dakika formatinda`() {
        assertEquals("0:00", VoiceRecordLogic.timerLabel(0))
        assertEquals("0:00", VoiceRecordLogic.timerLabel(999))
        assertEquals("0:01", VoiceRecordLogic.timerLabel(1_000))
        assertEquals("0:07", VoiceRecordLogic.timerLabel(7_000))
        assertEquals("0:59", VoiceRecordLogic.timerLabel(59_400))
        assertEquals("1:00", VoiceRecordLogic.timerLabel(60_000))
        // Tavan aşılsa da etiket 1:00'da kalır.
        assertEquals("1:00", VoiceRecordLogic.timerLabel(120_000))
    }

    @Test
    fun `ilerleme 0 ile 1 arasinda`() {
        assertEquals(0f, VoiceRecordLogic.progress(0), 0.0001f)
        assertEquals(0.5f, VoiceRecordLogic.progress(30_000), 0.0001f)
        assertEquals(1f, VoiceRecordLogic.progress(90_000), 0.0001f)
    }

    @Test
    fun `kayit dosya adi zaman damgali`() {
        assertEquals("kayit-1700000000000.ogg", VoiceRecordLogic.fileName(1_700_000_000_000))
    }

    @Test
    fun `kayit satiri dinliyorum ve metne cevriliyor der`() {
        val rec = VoiceRecordLogic.State(VoiceRecordLogic.Phase.Recording)
        assertTrue(VoiceRecordLogic.recordHint(rec, t).contains("Dinliyorum"))
        val tr = VoiceRecordLogic.State(VoiceRecordLogic.Phase.Transcribing)
        assertTrue(VoiceRecordLogic.recordHint(tr, t).contains("Metne çevriliyor"))
        assertEquals("", VoiceRecordLogic.recordHint(idle, t))
        assertTrue(
            VoiceRecordLogic.recordHint(
                VoiceRecordLogic.State(VoiceRecordLogic.Phase.Failed),
                t,
            ).contains("başarısız"),
        )
    }

    @Test
    fun `sozlesme siniri 60 sn`() {
        assertEquals(60_000L, VoiceApiEndpoints.MAX_RECORD_MS)
    }
}
