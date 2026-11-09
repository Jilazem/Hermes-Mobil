package com.hermes.mobile

import com.hermes.mobile.data.KeepalivePolicy
import com.hermes.mobile.data.SocketTuning
import com.hermes.mobile.data.WsBackoff
import com.hermes.mobile.data.WsCloseCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B3 (v6.3) — WS yaşam döngüsü: keepalive (25 sn ping, 2 kayıp), backoff
 * (1→2→4→8→15, 15'te tavan, deneme sınırsız) ve close-code eşlemesi
 * (1006→ağ koptu, 4401/1008→token reddedildi, 1011→sunucu hatası).
 */
class WsLifecycleTest {

    // ── Backoff dizisi ──────────────────────────────────────────────────────

    @Test
    fun `backoff dizisi 1 2 4 8 15`() {
        assertEquals(1000L, WsBackoff.delayMs(1))
        assertEquals(2000L, WsBackoff.delayMs(2))
        assertEquals(4000L, WsBackoff.delayMs(3))
        assertEquals(8000L, WsBackoff.delayMs(4))
        assertEquals(15000L, WsBackoff.delayMs(5))
    }

    @Test
    fun `backoff 15 saniyede tavan yapar`() {
        assertEquals(15000L, WsBackoff.delayMs(6))
        assertEquals(15000L, WsBackoff.delayMs(10))
        assertEquals(15000L, WsBackoff.delayMs(100))
    }

    @Test
    fun `backoff deneme sayisi her zaman calisir`() {
        // Deneme sayısı SIFIRLANMAZ (sınırsız) — yalnız gecikme tavanlanır.
        assertEquals(1000L, WsBackoff.delayMs(0))
    }

    @Test
    fun `yeniden baglaniyor metni deneme ve gecikme gosterir`() {
        assertEquals("Yeniden bağlanıyor (1. deneme, 1 sn)", WsBackoff.describe(1))
        assertEquals("Yeniden bağlanıyor (2. deneme, 2 sn)", WsBackoff.describe(2))
        assertEquals("Yeniden bağlanıyor (3. deneme, 4 sn)", WsBackoff.describe(3))
        assertEquals("Yeniden bağlanıyor (5. deneme, 15 sn)", WsBackoff.describe(5))
    }

    // ── Close-code eşlemesi ─────────────────────────────────────────────────

    @Test
    fun `close code 1006 ag koptu`() {
        assertEquals("Ağ koptu (1006)", WsCloseCodes.describe(1006))
    }

    @Test
    fun `close code 4401 ve 1008 token reddedildi`() {
        assertEquals("Token reddedildi (4401)", WsCloseCodes.describe(4401))
        assertEquals("Token reddedildi (1008)", WsCloseCodes.describe(1008))
    }

    @Test
    fun `close code 1011 sunucu hatasi`() {
        assertEquals("Sunucu hatası (1011)", WsCloseCodes.describe(1011))
    }

    @Test
    fun `normal kapanis kodlari`() {
        assertEquals("Bağlantı kapandı", WsCloseCodes.describe(1000))
        assertEquals("Sunucu kapanıyor", WsCloseCodes.describe(1001))
        assertEquals("Bağlantı kapandı (kod 4999)", WsCloseCodes.describe(4999))
    }

    @Test
    fun `token reddi yeniden denemeyi keser`() {
        assertTrue(WsCloseCodes.isFatalForRetry(4401))
        assertTrue(WsCloseCodes.isFatalForRetry(1008))
        assertFalse(WsCloseCodes.isFatalForRetry(1006))
        assertFalse(WsCloseCodes.isFatalForRetry(1011))
        assertFalse(WsCloseCodes.isFatalForRetry(1000))
    }

    // ── Keepalive: 25 sn ping, ping zaman aşımı, 2 kayıp → kırp ─────────────

    @Test
    fun `keepalive araligi 25 sn - ping zaman asimi 8 sn`() {
        assertEquals(25_000L, KeepalivePolicy.PING_INTERVAL_MS)
        assertEquals(8_000L, KeepalivePolicy.PONG_TIMEOUT_MS)
        // Tek kaynak: SocketTuning ile KeepalivePolicy aynı zaman aşımını paylaşır.
        assertEquals(KeepalivePolicy.PONG_TIMEOUT_MS, SocketTuning.GATEWAY_PONG_TIMEOUT_MS)
    }

    @Test
    fun `tek kayip baglantiyi kesmez iki kayip keser`() {
        val p = KeepalivePolicy()
        assertFalse("1. kayıp: bağlantı kesilmez", p.onMissed())
        assertTrue("2. kayıp: bağlantı kapatılır", p.onMissed())
    }

    @Test
    fun `cevap gelince kayip sayaci sifirlanir`() {
        val p = KeepalivePolicy()
        assertFalse(p.onMissed())
        p.onAnswered()
        assertFalse("cevap sonrası tek kayıp yeterli değil", p.onMissed())
    }

    @Test
    fun `reset sayaclari temizler`() {
        val p = KeepalivePolicy()
        p.onMissed()
        p.onMissed()
        p.reset()
        assertEquals(0, p.consecutiveMisses)
        assertFalse(p.onMissed())
    }
}