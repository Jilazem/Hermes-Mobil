package com.hermes.mobile

import com.hermes.mobile.data.ConnectionState
import com.hermes.mobile.data.GatewayLink
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * İş #1021 — "iki ekran iki kaynak" hatasının regresyon kilidi.
 *
 * Ayarlar > Sunucular ve sohbet ekranı AYNI StateFlow'u okumak zorunda:
 * `GatewayLink.connection`, bağlı olan tek `GatewayWsClient.connection`'ın
 * aynasıdır. Bağlı istemci yokken `Idle` döner (asla "bağlı" değil).
 */
class GatewayLinkSingleSourceTest {

    private fun profile() = ServerProfile(
        name = "tek-kaynak",
        baseUrl = "http://127.0.0.1:1",
        token = "test-token-1021",
    )

    @Test
    fun `bagli istemcinin durumu tek kaynaktan aynalanir`() {
        val gw = GatewayWsClient(profile())
        GatewayLink.bind(gw)
        try {
            assertSame("bağlı istemci kimliği değişti", gw, GatewayLink.client)
            // İki ekranın okuduğu değer birebir aynı olmalı.
            assertEquals(gw.connection.value, GatewayLink.connection.value)
            assertEquals(ConnectionState.Idle, GatewayLink.connection.value)
        } finally {
            GatewayLink.release(gw)
        }
        assertNull("bırakılan istemci hâlâ bağlı", GatewayLink.client)
        assertEquals(ConnectionState.Idle, GatewayLink.connection.value)
    }

    @Test
    fun `gec teardown yeni soketi silmez`() {
        val eski = GatewayWsClient(profile())
        val yeni = GatewayWsClient(profile())
        GatewayLink.bind(eski)
        GatewayLink.bind(yeni)
        // Eski ViewModel'in geç teardown'ı yeni bağlantıyı koparmamalı.
        GatewayLink.release(eski)
        assertSame("yeni istemci düşürüldü", yeni, GatewayLink.client)
        GatewayLink.release(yeni)
        assertNull(GatewayLink.client)
    }
}
