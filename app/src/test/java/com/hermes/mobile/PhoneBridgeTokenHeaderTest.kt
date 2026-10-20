package com.hermes.mobile

import com.hermes.mobile.data.PhoneBridgeService
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * WS token sızıntısı (iş #1020): kimlik **başlıkta**, URL'de sır yok.
 *
 * Sahada ölçülen sorun: uygulama `?token=…` biçimindeki WS adresini her
 * yeniden bağlanmada kuruyordu; token böylece istek satırında, yani köprü
 * erişim log'unda, ters vekil/proxy dökümünde ve yol boyunca her kayıtta tam
 * yetkili sır olarak görünüyordu. Düzeltme: `Authorization: Bearer <token>`
 * başlığı, URL temiz.
 *
 * Testler iki şeyi sabitliyor:
 *  1) İstek başlıkta token taşıyor ve URL'de hiçbir iz kalmıyor;
 *  2) Yetkisiz istek gerçekten reddediliyor (401) — kimlik yoksa ya da
 *     yanlışsa el sıkışma tamamlanmıyor.
 *
 * "Reddedilme" burada gerçek bir HTTP/WS el sıkışmasıyla kanıtlanıyor:
 * aşağıdaki [HandshakeServer] politikayı uygulayan yerel bir WS sunucu
 * dublörüdür (gerçek soket, gerçek istek satırı + başlıklar). Köprünün
 * GERÇEK işleyicisindeki aynı politika ise ayrı Python testleriyle
 * phone_bridge.py'ye karşı doğrulanıyor (bkz. MEDIUM-1-WS-TOKEN.md).
 */
class PhoneBridgeTokenHeaderTest {

    private val gizli = "hs_9f3c8a1b7e2d4056af11cc93be7710aa"

    // ── pozitif: başlıkta token, URL'de iz yok ───────────────────────────

    @Test
    fun `token baslikta tasinir url temiz kalir`() {
        val req = PhoneBridgeService.bridgeRequest("ws://192.168.1.10:9180/phone", gizli)
        assertEquals("Bearer $gizli", req.header("Authorization"))
        val url = req.url.toString()
        assertFalse("URL'de token adı var: $url", url.contains("token"))
        assertFalse("URL'de sır var: $url", url.contains(gizli))
        // OkHttp, HttpUrl'de ws/wss şemasını http/https olarak kanonikleştirir;
        // WS açılışı bu yüzden etkilenmez. Adresin kendisi değişmemeli.
        assertEquals("http://192.168.1.10:9180/phone", url)
    }

    @Test
    fun `canli el satismasinda baslik gider istek satiri temiz kalir`() {
        HandshakeServer(gizli).use { server ->
            val opened = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>(null)
            server.acceptOne()
            val ws = OkHttpClient().newWebSocket(
                PhoneBridgeService.bridgeRequest(server.url, gizli),
                listener(opened, failure, code = null),
            )
            assertTrue("el sıkışma tamamlanmadı", opened.await(5, TimeUnit.SECONDS))
            assertNull("bağlantı beklenmedik şekilde düştü: ${failure.get()}", failure.get())
            assertEquals("Bearer $gizli", server.authorization.get())
            val satir = server.requestLine.get().orEmpty()
            assertFalse("istek satırında token sorgusu var: $satir", satir.contains("token"))
            ws.cancel()
        }
    }

    // ── negatif: yetkisiz reddedilir ─────────────────────────────────────

    @Test
    fun `yanlis token 401 ile reddedilir`() {
        HandshakeServer(gizli).use { server ->
            val done = CountDownLatch(1)
            val code = AtomicReference<Int?>(null)
            server.acceptOne()
            val ws = OkHttpClient().newWebSocket(
                PhoneBridgeService.bridgeRequest(server.url, "yanlis-token"),
                listener(done, AtomicReference(null), code),
            )
            assertTrue("yanıt gelmedi", done.await(5, TimeUnit.SECONDS))
            assertEquals("yetkisiz istek reddedilmedi", 401, code.get())
            // Yanlış kimlik de URL'ye sızmamalı.
            assertFalse(
                "istek satırında token var",
                server.requestLine.get().orEmpty().contains("token"),
            )
            ws.cancel()
        }
    }

    @Test
    fun `kimliksiz istek 401 ile reddedilir`() {
        HandshakeServer(gizli).use { server ->
            val done = CountDownLatch(1)
            val code = AtomicReference<Int?>(null)
            server.acceptOne()
            // Eski davranışın isteği: başlık yok (bu da reddedilmeli).
            val req: Request = Request.Builder().url(server.url).build()
            val ws = OkHttpClient().newWebSocket(req, listener(done, AtomicReference(null), code))
            assertTrue("yanıt gelmedi", done.await(5, TimeUnit.SECONDS))
            assertEquals("kimliksiz istek reddedilmedi", 401, code.get())
            assertNull("istek beklenmedik biçimde başlık taşıdı", server.authorization.get())
            ws.cancel()
        }
    }

    private fun listener(
        latch: CountDownLatch,
        failure: AtomicReference<Throwable?>,
        code: AtomicReference<Int?>?,
    ) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            code?.set(response.code)
            latch.countDown()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            failure.set(t)
            code?.set(response?.code)
            latch.countDown()
        }
    }

    /**
     * El sıkışmayı denetleyen yerel WS sunucu dublörü.
     *
     * Gerçek TCP soketi, gerçek HTTP yükseltme isteğini okur ve köprüdekiyle
     * aynı politikayı uygular: `Authorization: Bearer <beklenen>` yoksa
     * `401 unauthorized`. Kabul ederse standart `Sec-WebSocket-Accept`
     * yanıtıyla 101 döner — istemci kodu (OkHttp) kendi yolundan geçtiği için
     * doğrulama gerçek el sıkışması üzerinden olur.
     */
    private class HandshakeServer(private val expected: String) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val requestLine = AtomicReference<String?>(null)
        val authorization = AtomicReference<String?>(null)
        val url: String get() = "ws://127.0.0.1:${serverSocket.localPort}/phone"

        /** Tek el sıkışmayı arka planda karşılar. */
        fun acceptOne() {
            val t = Thread {
                runCatching {
                    serverSocket.accept().use { serve(it) }
                }
            }
            t.isDaemon = true
            t.start()
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
                    (
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n"
                        ).toByteArray(Charsets.ISO_8859_1),
                )
                out.flush()
                // İstemci kapatana kadar soketi açık tut.
                socket.getInputStream().read()
            } else {
                val body = "unauthorized"
                out.write(
                    (
                        "HTTP/1.1 401 Unauthorized\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: ${body.length}\r\n" +
                            "Connection: close\r\n\r\n$body"
                        ).toByteArray(Charsets.ISO_8859_1),
                )
                out.flush()
            }
        }

        override fun close() {
            runCatching { serverSocket.close() }
        }

        companion object {
            /** RFC 6455 §4.2.2: base64(SHA-1(key + GUID)). */
            fun acceptKey(key: String): String {
                val digest = MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
                return Base64.getEncoder().encodeToString(digest)
            }
        }
    }
}