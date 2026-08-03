package com.hermes.mobile

import com.hermes.mobile.data.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Röle adresi türetmesi. Sessizce bozulabilecek bir yer: yanlış türetme
 * kullanıcıya "sonsuz yeniden bağlanılıyor" olarak görünüyor, yığında hata
 * bırakmıyor. Ev ağı ile dışarısı iki ayrı yol kullanıyor —
 * LAN'da doğrudan 9170, dışarıda 443 üzerinden `/live-relay`.
 */
class ServerProfileRelayTest {

    private fun profil(url: String, relay: String = "") =
        ServerProfile(name = "t", baseUrl = url, token = "x", relayUrl = relay)

    @Test
    fun `ev agindan dogrudan role portuna gider`() {
        assertEquals("ws://192.168.1.10:9170", profil("http://192.168.1.10:9150").effectiveRelayUrl)
        assertEquals("ws://10.0.0.20:9170", profil("http://10.0.0.20:9150").effectiveRelayUrl)
        assertEquals("ws://172.16.0.3:9170", profil("http://172.16.0.3:9150").effectiveRelayUrl)
        assertEquals("ws://localhost:9170", profil("http://localhost:9150").effectiveRelayUrl)
    }

    @Test
    fun `disaridan ters vekil yolunu kullanir`() {
        // Port yazılmamalı: TLS'i bulut vekili 443'te sonlandırıyor.
        assertEquals(
            "wss://hermes.ornek.tld/live-relay",
            profil("https://hermes.ornek.tld").effectiveRelayUrl,
        )
    }

    @Test
    fun `disaridan acik port verilmisse korunur`() {
        // Kullanıcı standart olmayan bir port kullanıyorsa düşürmemeliyiz.
        assertEquals(
            "wss://hermes.ornek.tld:8443/live-relay",
            profil("https://hermes.ornek.tld:8443").effectiveRelayUrl,
        )
    }

    @Test
    fun `elle girilen adres her seyi ezer`() {
        assertEquals(
            "ws://192.168.1.50:9999",
            profil("https://hermes.ornek.tld", relay = "ws://192.168.1.50:9999/").effectiveRelayUrl,
        )
    }

    @Test
    fun `cozumlenemeyen adres bos doner`() {
        // Boş dönmek çağıranın hata gösterebilmesi için gerekli; uydurma bir
        // adres döndürmek sonsuz yeniden bağlanma demek olurdu.
        assertEquals("", profil("bu bir adres degil").effectiveRelayUrl)
    }

    @Test
    fun `172 blogunun disi ozel sayilmaz`() {
        // 172.15 ve 172.32 RFC1918 dışı — yanlışlıkla LAN sayılırsa dışarıdan
        // erişimde yanlış yola gider.
        assertEquals("wss://172.15.0.1/live-relay", profil("https://172.15.0.1").effectiveRelayUrl)
        assertEquals("wss://172.32.0.1/live-relay", profil("https://172.32.0.1").effectiveRelayUrl)
    }
}
