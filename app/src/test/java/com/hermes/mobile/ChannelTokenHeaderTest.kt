package com.hermes.mobile

import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.LiveVoiceClient
import com.hermes.mobile.data.ServerFileDownload
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.ui.downloadUrl
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Kanal kanal token taşıma sözleşmesi (iş #1021 B2):
 *  - /api/ws (GatewayWsClient)
 *  - /live röle (LiveVoiceClient)
 *  - /api/files/download (ServerFileDownload + FileLinks.downloadUrl)
 *
 * Her kanal için aynı üç iddia:
 *  1) token İSTEK BAŞLIĞINDA (`Authorization: ***),
 *  2) URL/istek satırında sır YOK (erişim logları, vekiller),
 *  3) yetkisiz/yanlış kimlik REDDEDİLİYOR (401 — gerçek el sıkışma ya da
 *     gerçek HTTP yanıtı üzerinden, dublür sunucu politikası uygular).
 */
class ChannelTokenHeaderTest {

    private val gizli = "hs_b1_test_token_517e"

    // ── 1) /api/ws — GatewayWsClient ────────────────────────────────────

    @Test
    fun `gateway ws - token sorguda ve baslikta`() {
        val profile = ServerProfile(name = "t", baseUrl = "http://192.168.140.20:9150", token = gizli)
        val req = GatewayWsClient(profile).openRequest()
        // Sunucu WS upgrade'inde tokeni YALNIZCA ?token= sorgusundan okur
        // (web_server_chat.py `_ws_auth_ok`); sorgu yoksa el sıkışma 403.
        assertEquals("sorguda token yok", gizli, req.url.queryParameter("token"))
        assertEquals("/api/ws", req.url.encodedPath)
        // Başlık HTTP/REST kanalları için korunur.
        assertEquals("Bearer $gizli", req.header("Authorization"))
    }

    @Test
    fun `gateway ws - gercek el sikismasi sorgu tokeniyle 101 acar`() {
        val profile = ServerProfile(name = "t", baseUrl = "ws://127.0.0.1:1", token = gizli)
        val orijinal = GatewayWsClient(profile).openRequest()
        WsQueryPolicyServer(gizli).use { server ->
            server.acceptOne()
            val builder = okhttp3.HttpUrl.Builder()
                .scheme("http").host("127.0.0.1").port(server.port)
                .addPathSegments("api/ws")
            for (name in orijinal.url.queryParameterNames) {
                orijinal.url.queryParameterValues(name).forEach { builder.addQueryParameter(name, it) }
            }
            val req = orijinal.newBuilder().url(builder.build()).build()
            val opened = CountDownLatch(1)
            val code = AtomicReference<Int?>(null)
            val ws = OkHttpClient().newWebSocket(req, listener(opened, code))
            assertTrue("el sıkışma tamamlanmadı", opened.await(5, TimeUnit.SECONDS))
            assertEquals("sorgu tokeniyle 101 alınamadı", 101, code.get())
            assertEquals("Bearer $gizli", server.authorization.get())
            assertTrue(
                "istek satırında token yok: ${server.requestLine.get()}",
                server.requestLine.get().orEmpty().contains("token=$gizli"),
            )
            ws.cancel()
        }
    }

    @Test
    fun `gateway ws - yalniz baslikli eski istek reddedilir`() {
        // KÖK NEDEN kilidi: eski B2 davranışı (yalnız Authorization, sorgu yok)
        // gerçek sunucuda 403 alır. Dublür aynı politikayı uygular.
        WsQueryPolicyServer(gizli).use { server ->
            server.acceptOne()
            val done = CountDownLatch(1)
            val code = AtomicReference<Int?>(null)
            val ws = OkHttpClient().newWebSocket(
                okhttp3.Request.Builder()
                    .url("ws://127.0.0.1:${server.port}/api/ws")
                    .header("Authorization", "Bearer $gizli")
                    .build(),
                listener(done, code),
            )
            assertTrue("yanıt gelmedi", done.await(5, TimeUnit.SECONDS))
            assertEquals("başlık-only istek kabul edilmemeli", 403, code.get())
            ws.cancel()
        }
    }

    // ── 2) /live röle — LiveVoiceClient ────────────────────────────────

    @Test
    fun `live relay - token baslikta url temiz`() {
        val c = LiveVoiceClient(
            relayBase = "ws://192.168.140.20:9170",
            hermesToken = gizli,
            liveModel = "gemini-2.5-flash",
        )
        val req = c.openRequest()
        assertEquals("Bearer $gizli", req.header("Authorization"))
        val url = req.url.toString()
        assertFalse("URL'de token adı var: $url", url.contains("token"))
        assertFalse("URL'de sır var: $url", url.contains(gizli))
        // Kimlik dışı ayarlar sorguda kalmaya devam etmeli.
        assertTrue("model parametresi kayboldu: $url", url.contains("model=gemini-2.5-flash"))
    }

    @Test
    fun `live relay - gercek el sikismasinda baslik gider satir temiz`() {
        val c = LiveVoiceClient(relayBase = "ws://127.0.0.1:1", hermesToken = gizli)
        WsPolicyServer(gizli).use { server ->
            server.acceptOne()
            // İstemcinin ürettiği gerçek isteği dublüre yönlendir:
            // yalnız adres farklı, başlık istemcinin kendi mantığından gelir.
            val orijinal = c.openRequest()
            val builder = okhttp3.HttpUrl.Builder()
                .scheme("http").host("127.0.0.1").port(server.port)
                .addPathSegments("live")
            for (name in orijinal.url.queryParameterNames) {
                orijinal.url.queryParameterValues(name).forEach { builder.addQueryParameter(name, it) }
            }
            val req = orijinal.newBuilder().url(builder.build()).build()
            val opened = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>(null)
            val ws = OkHttpClient().newWebSocket(req, listener(opened, AtomicReference(), null, failure))
            assertTrue("el sıkışma tamamlanmadı", opened.await(5, TimeUnit.SECONDS))
            assertEquals("Bearer $gizli", server.authorization.get())
            val satir = server.requestLine.get().orEmpty()
            assertFalse("istek satırında token var: $satir", satir.contains("token"))
            ws.cancel()
        }
    }

    @Test
    fun `live relay - yanlis token 401 ile reddedilir`() {
        WsPolicyServer(gizli).use { server ->
            server.acceptOne()
            val done = CountDownLatch(1)
            val code = AtomicReference<Int?>(null)
            val req = okhttp3.Request.Builder()
                .url(server.url.replace("/phone", "/live"))
                .header("Authorization", "Bearer yanlis-token")
                .build()
            val ws = OkHttpClient().newWebSocket(req, listener(done, code))
            assertTrue("yanıt gelmedi", done.await(5, TimeUnit.SECONDS))
            assertEquals("yetkisiz istek reddedilmedi", 401, code.get())
            ws.cancel()
        }
    }

    // ── 3) /api/files/download — ServerFileDownload ────────────────────

    @Test
    fun `dosya indirme - token baslikta url temiz yol kodlanmis`() {
        val req = ServerFileDownload.request(
            "http://192.168.140.20:9150/", gizli, "/media/user/EX/R/rapor ozet.pdf",
        )
        assertEquals("Bearer $gizli", req.header("Authorization"))
        val url = req.url.toString()
        assertFalse("URL'de token var: $url", url.contains("token"))
        assertFalse("URL'de sır var: $url", url.contains(gizli))
        // Yol tam kodlanmalı — boşluk ve eğik çizgi dahil.
        assertTrue("yol tam kodlanmamış: $url", url.contains("path=%2Fmedia%2Fuser%2FEX%2FR%2Frapor+ozet.pdf"))
        // FileLinks.downloadUrl de artsık token taşımıyor.
        val uiUrl = downloadUrl("http://192.168.140.20:9150", "/media/user/EX/R/x.md")
        assertFalse("UI URL'sinde token var: $uiUrl", uiUrl.contains("token"))
    }

    @Test
    fun `dosya indirme - baslikta dogru token 200 ve dosya iner`() {
        HttpPolicyServer(gizli).use { server ->
            val target = File.createTempFile("hm-indir", ".txt")
            runBlocking {
                ServerFileDownload.downloadToFile(
                    OkHttpClient(), "http://127.0.0.1:${server.port}", gizli,
                    "/media/user/EX/R/kanit.txt", target,
                )
            }
            assertEquals("Bearer $gizli", server.lastAuthorization.get())
            assertFalse(
                "istek satırında token var",
                server.lastRequestLine.get().orEmpty().contains("token"),
            )
            assertEquals("icerik-testi-1021", target.readText())
        }
    }

    @Test
    fun `dosya indirme - yanlis token 401 ile reddedilir`() {
        HttpPolicyServer(gizli).use { server ->
            val target = File.createTempFile("hm-red", ".txt")
            val hata = runCatching {
                runBlocking {
                    ServerFileDownload.downloadToFile(
                        OkHttpClient(), "http://127.0.0.1:${server.port}", "yanlis-token",
                        "/media/user/EX/R/kanit.txt", target,
                    )
                }
            }.exceptionOrNull()
            assertTrue("yetkisiz indirme hata vermedi: $hata", hata is IllegalStateException)
            assertTrue((hata as IllegalStateException).message.orEmpty().contains("401"))
        }
    }

    // ── yardımcılar ─────────────────────────────────────────────────────

    private fun listener(
        latch: CountDownLatch,
        code: AtomicReference<Int?>,
        onFailureCode: AtomicReference<Int?>? = null,
        failure: AtomicReference<Throwable?>? = null,
    ) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            code.set(response.code)
            latch.countDown()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            failure?.set(t)
            (onFailureCode ?: code).set(response?.code)
            latch.countDown()
        }
    }

    /**
     * WS el sıkışma dublürü: başlıkta doğru `Authorization: Bearer` yoksa
     * 401, varsa 101. Telefon köprüsü testindekiyle aynı politika.
     */
    private class WsPolicyServer(private val expected: String) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val requestLine = AtomicReference<String?>(null)
        val authorization = AtomicReference<String?>(null)
        val url: String get() = "ws://127.0.0.1:${serverSocket.localPort}/phone"
        val port: Int get() = serverSocket.localPort

        fun acceptOne() {
            Thread {
                runCatching { serverSocket.accept().use { serve(it) } }
            }.apply { isDaemon = true }.start()
        }

        private fun serve(socket: Socket) {
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val satir = input.readLine() ?: return
            requestLine.set(satir)
            var key: String? = null
            var auth: String? = null
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i <= 0) continue
                when (line.substring(0, i).trim().lowercase()) {
                    "sec-websocket-key" -> key = line.substring(i + 1).trim()
                    "authorization" -> auth = line.substring(i + 1).trim()
                }
            }
            authorization.set(auth)
            val out = socket.getOutputStream()
            if (auth == "Bearer $expected" && key != null) {
                out.write(
                    ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
                        "Connection: Upgrade\r\nSec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n")
                        .toByteArray(Charsets.ISO_8859_1)
                )
                out.flush()
                socket.getInputStream().read()
            } else {
                val body = "unauthorized"
                out.write(
                    ("HTTP/1.1 401 Unauthorized\r\nContent-Type: text/plain\r\n" +
                        "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                        .toByteArray(Charsets.ISO_8859_1)
                )
                out.flush()
            }
        }

        override fun close() = runCatching { serverSocket.close() }.let { }

        companion object {
            fun acceptKey(key: String): String = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
            )
        }
    }

    /**
     * Gerçek gateway WS politikasının dublürü: token YALNIZCA `?token=` sorgusundan
     * okunur; sorgu yoksa/yanlışsa 403. (web_server_chat.py `_ws_auth_ok` ile aynı.)
     */
    private class WsQueryPolicyServer(private val expected: String) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val requestLine = AtomicReference<String?>(null)
        val authorization = AtomicReference<String?>(null)
        val port: Int get() = serverSocket.localPort

        fun acceptOne() {
            Thread {
                runCatching { serverSocket.accept().use { serve(it) } }
            }.apply { isDaemon = true }.start()
        }

        private fun serve(socket: Socket) {
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val satir = input.readLine() ?: return
            requestLine.set(satir)
            var key: String? = null
            var auth: String? = null
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i <= 0) continue
                when (line.substring(0, i).trim().lowercase()) {
                    "sec-websocket-key" -> key = line.substring(i + 1).trim()
                    "authorization" -> auth = line.substring(i + 1).trim()
                }
            }
            authorization.set(auth)
            val sorgu = satir.split(" ").getOrNull(1).orEmpty()
            val tokenOk = sorgu.contains("token=$expected")
            val out = socket.getOutputStream()
            if (tokenOk && key != null) {
                out.write(
                    ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
                        "Connection: Upgrade\r\nSec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n")
                        .toByteArray(Charsets.ISO_8859_1)
                )
                out.flush()
                socket.getInputStream().read()
            } else {
                val body = "forbidden"
                out.write(
                    ("HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\n" +
                        "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                        .toByteArray(Charsets.ISO_8859_1)
                )
                out.flush()
            }
        }

        override fun close() = runCatching { serverSocket.close() }.let { }

        private fun acceptKey(key: String): String = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
        )
    }

    /** HTTP indirme dublürü: doğru başlık 200 + gövde, aksi 401 (ham soket). */    private class HttpPolicyServer(private val expected: String) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = serverSocket.localPort
        val lastAuthorization = AtomicReference<String?>(null)
        val lastRequestLine = AtomicReference<String?>(null)

        init {
            Thread {
                while (!serverSocket.isClosed) {
                    runCatching { serverSocket.accept().use { serve(it) } }
                }
            }.apply { isDaemon = true }.start()
        }

        private fun serve(socket: Socket) {
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val satir = input.readLine() ?: return
            val auth = generateSequence { input.readLine() }
                .takeWhile { it.isNotEmpty() }
                .firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()
            val yol = satir.split(" ").getOrNull(1).orEmpty()
            if (yol.startsWith("/api/files/download")) {
                lastRequestLine.set(yol)
                lastAuthorization.set(auth)
            }
            val ok = yol.startsWith("/api/files/download") && auth == "Bearer $expected"
            val body = if (ok) "icerik-testi-1021" else "unauthorized"
            socket.getOutputStream().write(
                ("HTTP/1.1 ${if (ok) "200 OK" else "401 Unauthorized"}\r\n" +
                    "Content-Type: text/plain\r\nContent-Length: ${body.length}\r\n" +
                    "Connection: close\r\n\r\n$body").toByteArray(Charsets.ISO_8859_1)
            )
            socket.getOutputStream().flush()
        }

        override fun close() = runCatching { serverSocket.close() }.let { }
    }
}
