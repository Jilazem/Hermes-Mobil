package com.hermes.mobile

import com.hermes.mobile.data.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tarayıcıdan kopyalanan adresler (web arayüzü `?profile=default`, `#/chat`)
 * API tabanına sorgu kısmıyla girerse tüm istekler bozuluyordu:
 * "https://host/?profile=default" + "/api/sessions".
 */
class ServerUrlCleanTest {

    private fun profil(base: String, remote: String = "") =
        ServerProfile(name = "t", baseUrl = base, token = "x", remoteUrl = remote)

    @Test
    fun `dis adresteki profile sorgusu atilir`() {
        val p = profil("http://192.168.1.101:9150", "https://hermes.winterfell07.keenetic.pro/?profile=default")
        assertEquals("https://hermes.winterfell07.keenetic.pro", p.normalizedRemote)
        assertEquals(
            listOf("http://192.168.1.101:9150", "https://hermes.winterfell07.keenetic.pro"),
            p.candidates,
        )
    }

    @Test
    fun `taban adreste sorgu ve parca atilir`() {
        assertEquals("http://192.168.1.101:9150", profil("http://192.168.1.101:9150/?profile=default").normalizedUrl)
        assertEquals("https://h.tld", profil("https://h.tld/#/chat").normalizedUrl)
        assertEquals("https://h.tld/hermes", profil("https://h.tld/hermes/?x=1").normalizedUrl)
    }

    @Test
    fun `semasiz dis adrese http eklenir, bos kalir bos`() {
        assertEquals("http://h.tld", profil("http://a", "h.tld/").normalizedRemote)
        assertEquals("", profil("http://a", "  ").normalizedRemote)
    }
}
