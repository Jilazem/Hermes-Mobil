package com.hermes.mobile

import com.hermes.mobile.data.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-09-14 emülatör denetimi (SC-4): Sunucu ekle formunda adres alanı
 * eskiden "http://" ile doluydu; kullanıcı adresi başına yazınca
 * "httphttp://host" oluştu, toWs() "httpws://" üretti ve okhttp
 * openSocket'te FATAL çökertti. normalizedUrl artık kirli şemayı temizler.
 */
class DirtySchemeTest {

    private fun norm(baseUrl: String) =
        ServerProfile(name = "t", baseUrl = baseUrl, token = "x").normalizedUrl

    @Test
    fun `kirli httphttp temizlenir`() {
        assertEquals(
            "http://192.168.1.101:9150",
            norm("httphttp://192.168.1.101:9150"),
        )
    }

    @Test
    fun `kirli httphttps temizlenir - son yazilan sema esas`() {
        // Alan "http://" doluydu, kullanıcı "https://…" yazdı: httphttps://…
        assertEquals("https://tun.example", norm("httphttps://tun.example"))
        // Ters karışımda sondaki 'http' esas alınır:
        assertEquals("http://tun.example", norm("httpshttp://tun.example"))
    }

    @Test
    fun `sonundaki kirli sema kalintisi ve kirmalar temizlenir`() {
        assertEquals(
            "http://192.168.1.101:9150",
            norm("httphttp://192.168.1.101:9150://"),
        )
    }

    @Test
    fun `temiz sema aynen kalir - buyuk harf HTTPS downgrade olmaz`() {
        assertEquals("http://a:1", norm("http://a:1"))
        assertEquals("https://a:1", norm("https://a:1"))
        assertEquals("HTTPS://Tunnel.Example", norm("HTTPS://Tunnel.Example"))
    }

    @Test
    fun `semasiz adres http oneki alir`() {
        assertEquals("http://10.0.2.2:9199", norm("10.0.2.2:9199"))
    }

    @Test
    fun `bos alan bos kalir`() {
        assertEquals("", norm("   "))
    }
}
