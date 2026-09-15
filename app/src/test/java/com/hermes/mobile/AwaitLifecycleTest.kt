package com.hermes.mobile

import com.hermes.mobile.data.AwaitLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-10 / F1 — `AwaitReplyService` start/stop yarışı.
 *
 * Gerçek cihaz kanıtı (SM-S918B, 14:09:32, session fb2e7567):
 * `ForegroundServiceDidNotStartInTimeException`. Bu testler karar makinesinin
 * çökmeyi üreten üç dizilişi de kapattığını gösterir.
 */
class AwaitLifecycleTest {

    private fun life(clock: () -> Long = { 0L }) = AwaitLifecycle(clock)

    @Test
    fun `bos durumda stop hicbir sey yapmaz`() {
        val l = life()
        assertEquals(AwaitLifecycle.Action.NONE, l.setAwaiting(false))
        assertFalse(l.isConfirmed())
        assertFalse(l.isStartPending())
    }

    @Test
    fun `ilk start servisi baslatir ve onay bekler`() {
        val l = life()
        assertEquals(AwaitLifecycle.Action.START, l.setAwaiting(true))
        assertTrue(l.isStartPending())
        assertFalse(l.isConfirmed())
        assertTrue(l.awaiting())
    }

    @Test
    fun `cift start ikinci bir startForegroundService uretmez`() {
        val l = life()
        assertEquals(AwaitLifecycle.Action.START, l.setAwaiting(true))
        assertEquals(AwaitLifecycle.Action.NONE, l.setAwaiting(true))
        assertEquals(AwaitLifecycle.Action.NONE, l.setAwaiting(true))
        l.onForegroundConfirmed()
        assertEquals(AwaitLifecycle.Action.NONE, l.setAwaiting(true))
    }

    @Test
    fun `onay sonrasi stop stopService ister`() {
        val l = life()
        l.setAwaiting(true)
        assertFalse(l.onForegroundConfirmed())   // durdurma istenmiyor
        assertTrue(l.isConfirmed())
        assertEquals(AwaitLifecycle.Action.STOP, l.setAwaiting(false))
        assertFalse(l.isConfirmed())
        // Aynı anda gelen ikinci stop yok sayılır (servis zaten gitti).
        assertEquals(AwaitLifecycle.Action.NONE, l.setAwaiting(false))
    }

    /**
     * KRİTİK senaryo: `startForegroundService` verildi, onay gelmeden yanıt
     * geldi. Eski kod burada `stopService` çağırıyordu → çökme. Yeni kod
     * stop'u bekleterek servisin `startForeground`'u tamamlamasını sağlar.
     */
    @Test
    fun `onay beklerken gelen stop stopService cagirmaz`() {
        val l = life()
        l.setAwaiting(true)
        assertEquals(AwaitLifecycle.Action.NONE, l.setAwaiting(false))
        assertTrue(l.isStartPending())
        assertFalse(l.isConfirmed())
        // Servis yine de sözleşmeyi yerine getirir ve KENDİNİ durdurur.
        assertTrue(l.onForegroundConfirmed())
        assertFalse(l.isConfirmed())
        assertFalse(l.isStartPending())
    }

    /** Geç start yarışı: stop'tan SONRA gelen start yeni servis başlatmaz. */
    @Test
    fun `stop sonrasi gec start yeni servis baslatmaz`() {
        val l = life()
        l.setAwaiting(true)
        l.onForegroundConfirmed()
        l.setAwaiting(false)
        l.onServiceDestroyed()
        // Aynı anda gelen eski "devam et" isteği: burada START normaldir
        // (yeni bir bekleme başlıyor), ama servis gelince hemen kapanmaz.
        assertEquals(AwaitLifecycle.Action.START, l.setAwaiting(true))
        assertFalse(l.onForegroundConfirmed())
    }

    @Test
    fun `durdurma istegi onaydan once iptal edilirse servis calismaya devam eder`() {
        val l = life()
        l.setAwaiting(true)
        l.setAwaiting(false)        // pendingStop
        l.setAwaiting(true)         // tekrar "bekliyorum" → iptal
        assertFalse(l.onForegroundConfirmed())
        assertTrue(l.isConfirmed())
    }

    @Test
    fun `onay sonrasi bekleyen durdurma yok`() {
        val l = life()
        l.setAwaiting(true)
        l.onForegroundConfirmed()
        l.setAwaiting(false)
        assertFalse(l.isConfirmed())
        assertFalse(l.isStartPending())
        // Servis yok edilince tüm bayraklar temizlenir.
        l.onServiceDestroyed()
        assertFalse(l.awaiting())
    }

    @Test
    fun `servis yok edilince durum bastan baslar`() {
        val l = life()
        l.setAwaiting(true)
        l.onForegroundConfirmed()
        l.onServiceDestroyed()
        assertFalse(l.awaiting())
        assertFalse(l.isConfirmed())
        assertEquals(AwaitLifecycle.Action.START, l.setAwaiting(true))
    }

    @Test
    fun `zaman asimi bayragi serbest birakir`() {
        var now = 1_000L
        val l = AwaitLifecycle { now }
        l.setAwaiting(true)
        now += 1_000L
        assertFalse(l.onStartTimeout(3_000L))     // henüz erken
        assertTrue(l.isStartPending())
        now += 2_500L
        assertTrue(l.onStartTimeout(3_000L))      // temizlendi
        assertFalse(l.isStartPending())
        // Sonraki istek yeniden başlatabilir (sessiz kilitlenme yok).
        assertEquals(AwaitLifecycle.Action.START, l.setAwaiting(true))
    }

    @Test
    fun `onay geldiyse zaman asimi temizlemez`() {
        val l = AwaitLifecycle { 0L }
        l.setAwaiting(true)
        l.onForegroundConfirmed()
        assertFalse(l.onStartTimeout(0L))
        assertTrue(l.isConfirmed())
    }

    @Test
    fun `onay sonrasi desen - start stop start dongusu`() {
        val l = life()
        assertEquals(AwaitLifecycle.Action.START, l.setAwaiting(true))
        assertFalse(l.onForegroundConfirmed())
        assertEquals(AwaitLifecycle.Action.STOP, l.setAwaiting(false))
        // Servis kapandıktan sonra yeni bekleme yeni start ister.
        assertEquals(AwaitLifecycle.Action.START, l.setAwaiting(true))
        assertFalse(l.onForegroundConfirmed())
        assertTrue(l.isConfirmed())
    }
}
