package com.hermes.mobile

import com.hermes.mobile.data.BridgePolicy
import com.hermes.mobile.data.SocketTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-10 / F2 — soket parametreleri ve kopma sonrası iyileşme.
 *
 * Saha kanıtı: 19 `pong timeout` (ws 20 sn, köprü 40 sn penceresi) ve aynı
 * saniyede kopan kanallar (16:12:52).
 */
class SocketTuningTest {

    @Test
    fun `ping pencereleri kurallı_ws kisa kopru orta rolet uzun`() {
        assertTrue(SocketTuning.GATEWAY_PING_SECONDS < SocketTuning.BRIDGE_PING_SECONDS)
        assertTrue(SocketTuning.BRIDGE_PING_SECONDS < SocketTuning.RELAY_PING_SECONDS)
        // Rölenin uzun penceresi bilinçli (uzun `hermes_ask` turu) — 45 kalmalı.
        assertEquals(45L, SocketTuning.RELAY_PING_SECONDS)
        assertEquals(15L, SocketTuning.GATEWAY_PING_SECONDS)
        assertEquals(20L, SocketTuning.BRIDGE_PING_SECONDS)
    }

    @Test
    fun `ping penceresi sifir ya da negatif olamaz`() {
        assertTrue(SocketTuning.GATEWAY_PING_SECONDS > 0)
        assertTrue(SocketTuning.BRIDGE_PING_SECONDS > 0)
        assertTrue(SocketTuning.RELAY_PING_SECONDS > 0)
        assertTrue(SocketTuning.GATEWAY_CONNECT_SECONDS > 0)
        assertTrue(SocketTuning.BRIDGE_CONNECT_SECONDS > 0)
        assertTrue(SocketTuning.RELAY_CONNECT_SECONDS > 0)
    }

    @Test
    fun `ilk kopma denemesi hizli`() {
        val d = SocketTuning.gatewayReconnectMs(attempt = 1, msSinceLastOpen = null, unit = 0.5)
        assertEquals(SocketTuning.FIRST_RETRY_MS, d)
    }

    @Test
    fun `sarsinti sonrasi tavan 5 saniye`() {
        // 5. denemede normalde 16 sn olurdu; yakın zamanda bağlıysak 5 sn.
        val d = SocketTuning.gatewayReconnectMs(attempt = 5, msSinceLastOpen = 10_000L, unit = 0.5)
        assertEquals(SocketTuning.FAST_RECOVERY_CAP_MS, d)
    }

    @Test
    fun `uzun suredir kopuksa merdiven buyur`() {
        val d3 = SocketTuning.gatewayReconnectMs(attempt = 3, msSinceLastOpen = 600_000L, unit = 0.5)
        val d4 = SocketTuning.gatewayReconnectMs(attempt = 4, msSinceLastOpen = 600_000L, unit = 0.5)
        val d5 = SocketTuning.gatewayReconnectMs(attempt = 5, msSinceLastOpen = 600_000L, unit = 0.5)
        assertEquals("3. deneme 8 sn olmalı", 8_000L, d3)
        assertEquals("4. deneme 16 sn olmalı", 16_000L, d4)
        assertEquals("5. deneme tavanda olmalı", SocketTuning.MAX_BACKOFF_MS, d5)
        assertTrue(d5 > d4 && d4 > d3)
    }

    @Test
    fun `merdiven tavani 30 saniyeyi asmaz`() {
        val d = SocketTuning.gatewayReconnectMs(attempt = 20, msSinceLastOpen = 600_000L, unit = 0.5)
        assertEquals(SocketTuning.MAX_BACKOFF_MS, d)
    }

    @Test
    fun `sarsinti penceresinin disinda kalan sure normal merdivene doner`() {
        val inside = SocketTuning.gatewayReconnectMs(
            attempt = 5, msSinceLastOpen = SocketTuning.FLAP_WINDOW_MS - 1, unit = 0.5,
        )
        val outside = SocketTuning.gatewayReconnectMs(
            attempt = 5, msSinceLastOpen = SocketTuning.FLAP_WINDOW_MS + 1, unit = 0.5,
        )
        assertEquals(SocketTuning.FAST_RECOVERY_CAP_MS, inside)
        assertEquals(SocketTuning.MAX_BACKOFF_MS, outside)
    }

    @Test
    fun `jitter sinirlari yuzde yirmi bes`() {
        val base = 10_000L
        assertEquals(7_500L, SocketTuning.jittered(base, unit = 0.0))
        assertEquals(12_500L, SocketTuning.jittered(base, unit = 1.0))
        assertEquals(10_000L, SocketTuning.jittered(base, unit = 0.5))
        assertTrue(SocketTuning.jittered(0L, unit = 0.0) >= 1L)
    }

    @Test
    fun `jitter araligi disindaki unit kirpilir`() {
        assertEquals(7_500L, SocketTuning.jittered(10_000L, unit = -5.0))
        assertEquals(12_500L, SocketTuning.jittered(10_000L, unit = 9.0))
    }

    @Test
    fun `kopru merdiveni korunur ve sarsintida 15 sn tavan`() {
        assertEquals(BridgePolicy.backoffMs(1), SocketTuning.bridgeReconnectMs(1, null, 0.5))
        // 4. basamak normalde 30 sn; yakın zamanda bağlıysak 15 sn.
        assertEquals(15_000L, SocketTuning.bridgeReconnectMs(4, 5_000L, 0.5))
        // Uzun süredir kopuksa merdiven aynen işler (devirme fırtınası yavaşlasın).
        assertEquals(30_000L, SocketTuning.bridgeReconnectMs(4, 900_000L, 0.5))
        assertEquals(60_000L, SocketTuning.bridgeReconnectMs(5, 900_000L, 0.5))
    }

    @Test
    fun `kopru ilk basamak sifir degil`() {
        assertTrue(SocketTuning.bridgeReconnectMs(1, 5_000L, 0.0) > 0L)
    }
}
