package com.hermes.mobile

import com.hermes.mobile.data.EndpointNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B3 (v6.3) — uç URL normalizasyonu (https/wss) tek yerde: [EndpointNormalizer].
 *
 * Brief (vd89fy / B3 md.1): `https://host` → WS `wss://host/api/ws`;
 * `http://host:9150` → WS `ws://host:9150/api/ws`; şema yoksa özel ağda http,
 * dışarıda https; sondaki `/` kırpılır; `/api/ws` tekrarlanmaz; dışarıda her
 * zaman `wss://` (cleartext dışarıya asla çıkmaz). Token WS'de `?token=` (bkz.
 * SocketTuningTest) — burada yalnız URL şekli denenir.
 */
class EndpointNormalizerTest {

    // ── 12+ normalizasyon vakası ────────────────────────────────────────────

    @Test
    fun `https host - wss api ws, port 443`() {
        val e = EndpointNormalizer.normalize("https://host")!!
        assertEquals("https://host", e.base)
        assertEquals(443, e.port)
        assertEquals("wss", e.wsScheme)
        assertEquals("wss://host/api/ws", e.apiWsUrl)
    }

    @Test
    fun `http host 9150 - ws api ws`() {
        val e = EndpointNormalizer.normalize("http://192.168.1.10:9150")!!
        assertEquals("http://192.168.1.10:9150", e.base)
        assertEquals("ws://192.168.1.10:9150/api/ws", e.apiWsUrl)
    }

    @Test
    fun `sondaki egik cizgi kirpilir`() {
        assertEquals(
            "ws://192.168.1.10:9150/api/ws",
            EndpointNormalizer.apiWsUrl("http://192.168.1.10:9150/"),
        )
    }

    @Test
    fun `api ws yazilinca ciftlenmez`() {
        assertEquals(
            "ws://192.168.1.10:9150/api/ws",
            EndpointNormalizer.apiWsUrl("http://192.168.1.10:9150/api/ws"),
        )
    }

    @Test
    fun `sorgu ve parcacik atilir`() {
        assertEquals(
            "ws://192.168.1.10:9150/api/ws",
            EndpointNormalizer.apiWsUrl("http://192.168.1.10:9150/?profile=default#/chat"),
        )
    }

    @Test
    fun `semaya ozel ag http olur`() {
        val e = EndpointNormalizer.normalize("192.168.1.10:9150")!!
        assertEquals("http", e.scheme)
        assertEquals("http://192.168.1.10:9150", e.base)
        assertEquals("ws://192.168.1.10:9150/api/ws", e.apiWsUrl)
    }

    @Test
    fun `semaya dis adres https olur`() {
        val e = EndpointNormalizer.normalize("ornek.com")!!
        assertEquals("https", e.scheme)
        assertEquals("https://ornek.com", e.base)
        assertEquals("wss://ornek.com/api/ws", e.apiWsUrl)
    }

    @Test
    fun `dis adreste http https e yukseltilir - disarida wss zorunlu`() {
        val e = EndpointNormalizer.normalize("http://ornek.com")!!
        assertEquals("https", e.scheme)
        assertEquals("wss", e.wsScheme)
        assertEquals("wss://ornek.com/api/ws", e.apiWsUrl)
    }

    @Test
    fun `ws girisi http karsiligina cevrilir`() {
        val e = EndpointNormalizer.normalize("ws://192.168.1.10:9150")!!
        assertEquals("http", e.scheme)
        assertEquals("ws://192.168.1.10:9150/api/ws", e.apiWsUrl)
    }

    @Test
    fun `wss girisi https karsiligina cevrilir`() {
        val e = EndpointNormalizer.normalize("wss://ornek.com")!!
        assertEquals("https", e.scheme)
        assertEquals("wss://ornek.com/api/ws", e.apiWsUrl)
    }

    @Test
    fun `kirli sema onarilir`() {
        assertEquals(
            "ws://192.168.1.10:9150/api/ws",
            EndpointNormalizer.apiWsUrl("httphttp://192.168.1.10:9150"),
        )
    }

    @Test
    fun `yol parcali adres yol korunur`() {
        val e = EndpointNormalizer.normalize("https://host:8443/yol")!!
        assertEquals("wss://host:8443/yol/api/ws", e.apiWsUrl)
        assertEquals(8443, e.port)
    }

    @Test
    fun `localhost ozel ag sayilir`() {
        val e = EndpointNormalizer.normalize("localhost:9150")!!
        assertEquals("http", e.scheme)
        assertEquals("http://localhost:9150", e.base)
    }

    @Test
    fun `kullanici bilgisi atilir`() {
        val e = EndpointNormalizer.normalize("http://kullanici@192.168.1.10:9150")!!
        assertEquals("http://192.168.1.10:9150", e.base)
        assertEquals("ws://192.168.1.10:9150/api/ws", e.apiWsUrl)
    }

    @Test
    fun `bos ve anlasilmaz girdi null`() {
        assertNull(EndpointNormalizer.apiWsUrl(""))
        assertNull(EndpointNormalizer.apiWsUrl("http://"))
        assertNull(EndpointNormalizer.apiWsUrl("   "))
        assertNull(EndpointNormalizer.apiWsUrl("https://"))
    }

    @Test
    fun `varsayilan https portu 443 yazilmis gibi`() {
        val e = EndpointNormalizer.normalize("https://host:443")!!
        assertEquals(443, e.port)
        assertEquals("wss://host:443/api/ws", e.apiWsUrl)
    }

    @Test
    fun `buyuk harf sema wss uretir`() {
        assertEquals("wss://Ornek.com/api/ws", EndpointNormalizer.apiWsUrl("HTTPS://Ornek.com"))
    }
}