package com.hermes.mobile.data

import com.hermes.mobile.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

/**
 * Artemis LAN kapısı (iş #1020): release'de açık kalan düz HTTP + kimliksiz
 * istemci kapatılıyor.
 *
 * Politika ([ArtemisClient.enforce], inşa + her istek öncesi iki kapı):
 *  - **strict / release** (`BuildConfig.ARTEMIS_STRICT = true`): yalnız
 *    `https://` + private/loopback host + zorunlu token; `http://` ya da
 *    kimliksiz istek [ArtemisPolicyViolation] ile, ağa tek bayt çıkmadan
 *    reddedilir.
 *  - **debug**: mevcut davranış korunur — private LAN'da düz `http` +
 *    kimliksiz istek çalışır.
 *
 * Token her iki kipte de yalnız `Authorization: Bearer` başlığında taşınır;
 * URL'de iz bırakılmaz (bkz. PhoneBridgeTokenHeaderTest).
 *
 * Testler `:app:testDebugUnitTest` ve `:app:testReleaseUnitTest` altında aynı
 * kaynaktan koşar; strict/lenient kararı bu yüzden açık parametreden verilir,
 * sürüm bağlantısı ayrı bir testle sabitlenir.
 */
class ArtemisLanGateTest {

    private val gizli = "art_7c41e9d2b8a04f6e9d3c5a1b7e2f4086"

    // ── release (strict): http reddi ─────────────────────────────────────

    @Test
    fun `release bayragiyla http istemci inşa edilemez`() {
        assertPolicyViolation("http://192.168.1.40:8000", gizli, strict = true)
    }

    @Test
    fun `release bayragiyla http istek aga cikmadan reddedilir`() {
        // İstek kurulumu OkHttp'ye varmadan politikaya düşer: reddedilen
        // istek nesnesi hiç oluşmaz (Request yok = soket yok).
        try {
            ArtemisClient.buildRequest("http://192.168.1.40:8000", gizli, strict = true, "GET", "/api/status", null)
            fail("http istek strict kipte kurulabildi")
        } catch (e: ArtemisPolicyViolation) {
            assertTrue("hata gerekçesinde https zorunluluğu yok: ${e.message}", e.message!!.contains("https"))
        }
    }

    // ── release (strict): kimliksiz reddi ────────────────────────────────

    @Test
    fun `release bayragiyla kimliksiz istemci inşa edilemez`() {
        assertPolicyViolation("https://192.168.1.40:8443", null, strict = true)
        assertPolicyViolation("https://192.168.1.40:8443", "", strict = true)
        assertPolicyViolation("https://192.168.1.40:8443", "   ", strict = true)
    }

    @Test
    fun `release bayragiyla kimliksiz istek aga cikmadan reddedilir`() {
        try {
            ArtemisClient.buildRequest("https://127.0.0.1:8443", "", strict = true, "POST", "/api/run", null)
            fail("kimliksiz istek strict kipte kurulabildi")
        } catch (e: ArtemisPolicyViolation) {
            assertTrue("hata gerekçesinde token zorunluluğu yok: ${e.message}", e.message!!.contains("token"))
        }
    }

    // ── release (strict): https + token kabulü ───────────────────────────

    @Test
    fun `release bayragiyla https ve token kabul edilir`() {
        val client = ArtemisClient("https://127.0.0.1:8443", gizli, strict = true)
        assertEquals("https://127.0.0.1:8443", client.base)

        val req = ArtemisClient.buildRequest(client.base, gizli, strict = true, "GET", "/api/devices", null)
        assertEquals("Bearer $gizli", req.header("Authorization"))
        val url = req.url.toString()
        assertEquals("https korunmadı: $url", "https", req.url.scheme)
        assertFalse("URL'de token adı var: $url", url.contains("token"))
        assertFalse("URL'de sır var: $url", url.contains(gizli))
    }

    @Test
    fun `release bayragiyla dis ag adresi hala reddedilir`() {
        // Private/loopback şartı strict'te de korunur (eski davranışın üstüne
        // yalnız https + token eklendi, host kısıtı kalkmadı).
        assertPolicyViolation("https://example.com:8443", gizli, strict = true)
        assertPolicyViolation("https://8.8.8.8:8443", gizli, strict = true)
    }

    // ── debug (lenient): mevcut davranış korunur ──────────────────────────

    @Test
    fun `debug kipinde http ve kimliksiz istemci kabul edilir`() {
        val client = ArtemisClient("http://192.168.1.40:8000", null, strict = false)
        assertEquals("http://192.168.1.40:8000", client.base)

        val req = ArtemisClient.buildRequest(client.base, "", strict = false, "GET", "/api/status", null)
        assertNull("kimliksiz istekte Authorization başlığı çıkmamalı", req.header("Authorization"))
    }

    @Test
    fun `debug kipinde http istek canli yerel sunucuya gercekten gider`() {
        // "Mevcut davranış korunur" iddiasının canlı kanıtı: düz http + tek
        // başlıksız istek, gerçek soket üzerinden başarıyla tamamlanıyor.
        MiniHttpServer().use { server ->
            val url = "http://127.0.0.1:${server.port}"
            val ok = runBlocking { ArtemisClient(url, null, strict = false).ping() }
            assertTrue("debug kipinde http istek başarısız", ok)
            assertNull("kimliksiz istekte beklenmedik Authorization", server.auth.get())
        }
    }

    @Test
    fun `debug kipinde token varsa Bearer basliginda gider url temiz kalir`() {
        MiniHttpServer().use { server ->
            val url = "http://127.0.0.1:${server.port}"
            val ok = runBlocking { ArtemisClient(url, gizli, strict = false).ping() }
            assertTrue("istemci isteği tamamlayamadı", ok)
            assertEquals("Bearer $gizli", server.auth.get())
            assertFalse(
                "istek satırında sır var: ${server.satir.get()}",
                server.satir.get().orEmpty().contains(gizli),
            )
        }
    }

    // ── sürüm bayrağı bağlantısı ─────────────────────────────────────────

    @Test
    fun `surum bayragi build tipine baglidir ve varsayilan kip onu izler`() {
        // testDebugUnitTest → DEBUG=true/STRICT=false,
        // testReleaseUnitTest → DEBUG=false/STRICT=true.
        assertEquals(!BuildConfig.DEBUG, BuildConfig.ARTEMIS_STRICT)

        if (BuildConfig.ARTEMIS_STRICT) {
            assertPolicyViolation("http://192.168.1.40:8000", gizli, strict = BuildConfig.ARTEMIS_STRICT)
        } else {
            // Varsaylan strict argümanı bu sürümde http'yi kabul etmeli.
            ArtemisClient("http://192.168.1.40:8000", null, strict = BuildConfig.ARTEMIS_STRICT)
        }
    }

    // ── yardımcılar ──────────────────────────────────────────────────────

    private fun assertPolicyViolation(url: String, token: String?, strict: Boolean) {
        try {
            ArtemisClient(url, token, strict)
            fail("politika ihlali kabul edildi: url=$url strict=$strict token=${token != null}")
        } catch (e: ArtemisPolicyViolation) {
            assertTrue("boş politika gerekçesi", !e.message.isNullOrBlank())
        }
    }
}

/**
 * Ham soket üzerinde tek isteklik en küçük HTTP sunucusu — canlı kanıt için.
 * (com.sun.net.httpserver test derleme sınıf yolunda olmadığından el ile.)
 */
private class MiniHttpServer : AutoCloseable {
    private val ss = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = ss.localPort
    val auth = AtomicReference<String?>(null)
    val satir = AtomicReference<String?>(null)

    private val thread = Thread {
        while (!ss.isClosed) {
            try {
                ss.accept().use { soket ->
                    val r = soket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    satir.set(r.readLine())
                    while (true) {
                        val line = r.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) {
                            auth.set(line.substringAfter(":").trim())
                        }
                    }
                    val body = """{"status":"ok"}""".toByteArray()
                    soket.getOutputStream().apply {
                        write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(),
                        )
                        write(body)
                        flush()
                    }
                }
            } catch (_: Exception) {
                if (ss.isClosed) break
            }
        }
    }.apply { isDaemon = true; start() }

    override fun close() {
        runCatching { ss.close() }
        thread.join(1000)
    }
}