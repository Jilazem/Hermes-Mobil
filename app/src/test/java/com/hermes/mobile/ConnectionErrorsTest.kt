package com.hermes.mobile

import com.hermes.mobile.data.ConnectionErrors
import com.hermes.mobile.data.ConnectionTest
import com.hermes.mobile.data.HermesApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * İş #1021 J1 — "sunucuya bağlanamıyorum" regresyonunun teşhis katmanı.
 *
 * Kök neden: bağlantı katmanı tüm hataları aynı belirsiz cümleye indiriyordu;
 * token yanlış mı, adres mi ölü, DNS mi çözülmüyor, ayırt edilemiyordu.
 * Bu testler sınıflandırmanın ve kullanıcı cümlesinin doğruluğunu ölçer.
 */
class ConnectionErrorsTest {

    @Test
    fun `http 401 token hatasi olarak ayrilir`() {
        val e = HermesApiException(401, "/api/sessions/stats", "unauthorized")
        assertEquals("HTTP 401", ConnectionErrors.code(e))
        val c = ConnectionErrors.describe(e)
        assertTrue("token reddi cümlesi yok: $c", c.contains("Token reddedildi"))
        assertTrue("kod görünmüyor: $c", c.contains("401"))
    }

    @Test
    fun `http 404 adres ucu hatasi olarak ayrilir`() {
        val c = ConnectionErrors.describe(HermesApiException(404, "/api/status", "not found"))
        assertTrue("404 cümlesi yok: $c", c.contains("404"))
        assertFalse("404 token hatası sayılmamalı: $c", c.contains("Token reddedildi"))
    }

    @Test
    fun `ag hatalari tek tek siniflandirilir`() {
        assertEquals("DNS", ConnectionErrors.code(UnknownHostException("hermes.local")))
        assertEquals("CONN_REFUSED", ConnectionErrors.code(ConnectException("refused")))
        assertEquals("TIMEOUT", ConnectionErrors.code(SocketTimeoutException("timeout")))
        assertEquals("TLS", ConnectionErrors.code(SSLHandshakeException("cert")))
        assertEquals("IO", ConnectionErrors.code(IOException("boom")))
    }

    @Test
    fun `sarmalanmis hata kok nedene iner`() {
        val sarili = IOException("outer", ConnectException("refused"))
        assertEquals("CONN_REFUSED", ConnectionErrors.code(sarili))
        assertTrue(ConnectionErrors.describe(sarili).contains("Bağlantı reddedildi"))
    }

    @Test
    fun `kullanici cumlesi bos ve tekrarli degil`() {
        val c = ConnectionErrors.describe(UnknownHostException("x"))
        assertTrue(c.isNotBlank())
        assertTrue("DNS kodu yok: $c", c.contains("DNS"))
        assertTrue("Türkçe cümle yok: $c", c.contains("çözülemedi"))
    }

    @Test
    fun `baglanti testi satiri kod gecikme ve ayrinti icerir`() {
        val ok = ConnectionTest(true, 200, 43, "Bağlantı sağlam · model: glm-5.3", "HTTP 200")
        val satir = ok.line()
        assertTrue(satir.contains("HTTP 200"))
        assertTrue(satir.contains("43 ms"))
        assertTrue(satir.contains("glm-5.3"))

        val hata = ConnectionTest(false, 401, 12, "Token reddedildi (HTTP 401)", "HTTP 401")
        assertTrue(hata.line().contains("Token reddedildi"))

        val ag = ConnectionTest(false, null, 8000, "Sunucu zamanında yanıt vermedi", "TIMEOUT")
        assertTrue("ağ hatasında teknik kod görünmeli: ${ag.line()}", ag.line().contains("TIMEOUT"))
    }
}
