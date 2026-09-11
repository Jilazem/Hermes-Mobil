package com.hermes.mobile

import com.hermes.mobile.ui.railLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StreamMeter] + [SpeedFormat] + [railLabel] — hız göstergesinin ve oturum
 * rayının saf mantığı.
 *
 * Zaman sahte nanos ile enjekte edilir; böylece 500 ms pencereler, EMA
 * yumuşatma, token sayımı ve donma davranışı deterministik test edilir.
 */
class SpeedMeterTest {

    private val ms = 1_000_000L

    @Test
    fun `token sayaci yaklaşık dört karakter bir token`() {
        val m = StreamMeter()
        m.delta("Merhaba dünya!", 0L) // 14 kr → 3 token (tam bölme)
        val s = m.snapshot(100 * ms)
        assertEquals(3, s.tokens)
        assertTrue(s.active)
        assertFalse(s.finished)
    }

    @Test
    fun `bos delta saati baslatmaz`() {
        val m = StreamMeter()
        assertNull(m.delta("", 0L))
        val s = m.snapshot(5_000 * ms)
        assertFalse(s.active)
        assertEquals(0L, s.elapsedMs)
    }

    @Test
    fun `delta yalniz pencere kapaninca snapshot doner`() {
        val m = StreamMeter()
        m.delta("abcd", 0L) // saat şimdi başlar
        // Pencere dolmadan null: UI recomposition'ı bastırılıyor.
        assertNull(m.delta("abcd", 200 * ms))
        val closed = m.delta("abcd", 600 * ms)
        assertNotNull(closed)
        assertTrue(closed!!.tokensPerSecond > 0.0)
    }

    @Test
    fun `saniyede sabit akista ham hiz dogru`() {
        val m = StreamMeter()
        // Her 100 ms'de 8 karakter (2 token) → 20 t/s beklenir.
        var t = 0L
        for (i in 0 until 20) {
            m.delta("12345678", t)
            t += 100 * ms
        }
        val s = m.snapshot(t)
        // EMA ilk pencerede ham değere kurulur; 500 ms pencerelerde
        // ham hız ~20 t/s olmalı — tolerans yarım pencere kenar etkisi.
        assertTrue("hız çok düşük: ${s.tokensPerSecond}", s.tokensPerSecond > 15.0)
        assertTrue("hız çok yüksek: ${s.tokensPerSecond}", s.tokensPerSecond < 25.0)
    }

    @Test
    fun `ema yukselen hizi yavas takip eder`() {
        val m = StreamMeter()
        // İlk 2 sn 4 kr/100ms = 10 t/s; sonra 16 kr/100ms = 40 t/s.
        var t = 0L
        repeat(20) {
            m.delta("1234", t)
            t += 100 * ms
        }
        val slow = m.snapshot(t).tokensPerSecond
        assertTrue(slow in 5.0..15.0)
        repeat(20) {
            m.delta("1234567890123456", t)
            t += 100 * ms
        }
        val after = m.snapshot(t).tokensPerSecond
        // EMA alpha 0.25: henüz 40'a vurmamış ama 10'un üstüne çıkmış olmalı.
        assertTrue("EMA zıplamadı: $after", after > slow)
        assertTrue("EMA henüz yumuşatıyor: $after", after < 40.0)
    }

    @Test
    fun `finish degerleri dondurur`() {
        val m = StreamMeter()
        var t = 0L
        repeat(10) {
            m.delta("12345678", t) // 20 t/s
            t += 100 * ms
        }
        val frozen = m.finish(t)
        assertTrue(frozen.finished)
        val before = frozen.tokensPerSecond
        // Donmuş ölçerde delta yok sayılır, snapshot değişmez.
        m.delta("aaaaaaaaaa", t + 5_000 * ms)
        val later = m.snapshot(t + 5_000 * ms)
        assertEquals(before, later.tokensPerSecond, 0.0001)
        assertEquals(frozen.elapsedMs, later.elapsedMs)
        assertEquals(frozen.tokens, later.tokens)
    }

    @Test
    fun `reset yeni akis icin saati sifirlar`() {
        val m = StreamMeter()
        m.delta("merhaba dünya", 0L)
        m.finish(2_000 * ms)
        m.reset()
        val fresh = m.snapshot(10_000 * ms)
        assertFalse(fresh.finished)
        assertFalse(fresh.active)
        m.delta("yeni", 10_000 * ms)
        assertEquals(0L, m.snapshot(10_000 * ms).elapsedMs)
    }

    @Test
    fun `sure ilk deltadan itibaren sayilir`() {
        val m = StreamMeter()
        // Saat ilk delta ile başlar, ViewModel'in çağırdığı andan değil.
        m.delta("ab", 30_000 * ms)
        val s = m.snapshot(44_000 * ms)
        assertEquals(14_000L, s.elapsedMs)
    }

    @Test
    fun `hiz satiri bicimlendirmesi`() {
        // TR ondalık virgül, EN nokta; 1,2k kısaltması; m:ss saat.
        assertEquals("1,2k", SpeedFormat.compactTokens(1234, ','))
        assertEquals("1.2k", SpeedFormat.compactTokens(1234, '.'))
        assertEquals("940", SpeedFormat.compactTokens(940, ','))
        assertEquals("1,5k", SpeedFormat.compactTokens(1500, ','))
        assertEquals("0:14", SpeedFormat.clock(14_400))
        assertEquals("1:15", SpeedFormat.clock(75_000))
        assertEquals("8,4", SpeedFormat.rate(8.4, ','))
        assertEquals("24,2", SpeedFormat.rate(24.2, ','))
        assertEquals("24", SpeedFormat.rate(24.04, ','))
        assertEquals("1.2k", SpeedFormat.rate(1208.0, '.'))
    }

    @Test
    fun `ray etiketi ilk iki karakter`() {
        assertEquals("ME", railLabel("merhaba dünya"))
        assertEquals("AN", railLabel("  Ankara Projesi "))
        assertEquals("?", railLabel(""))
        assertEquals("?", railLabel("   "))
    }

    @Test
    fun `ray etiketi yerelden bagimsiz buyutur`() {
        // Character.uppercaseChar (Char.uppercaseChar) yerel ayardan
        // bağımsız tek anlamlı Unicode eşlemesidir — String.uppercase()
        // Türkçe 'i'yi locale'e göre "I" ya da "İ" yapabiliyordu.
        val label = railLabel("istanbul")
        assertEquals("IS", label)
        assertEquals("İS", railLabel("İstanbul"))
    }
}
