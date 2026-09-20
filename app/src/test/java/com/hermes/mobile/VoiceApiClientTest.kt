package com.hermes.mobile

import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.HttpVoiceTransport
import com.hermes.mobile.data.VoiceApiClient
import com.hermes.mobile.data.VoiceApiException
import com.hermes.mobile.data.VoiceSpeakLogic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Tur-11 — gerçek soketle sözleşme sınaması.
 *
 * Yerel bir HTTP sunucusu sözleşmeyi uygular (`GET /health`,
 * `POST /transcribe` multipart `audio`, `POST /synthesize` JSON `{text,engine}`
 * → `audio/ogg`); istemci bu sunucuya koşar. Böylece **yol, başlık, gövde
 * biçimi ve hata eşlemesi** varsayımla değil ölçümle doğrulanır.
 *
 * Sunucu bilinçli olarak `ServerSocket` ile elde yazıldı: Android birim test
 * derlemesinde `com.sun.net.httpserver` (jdk.httpserver modülü) classpath'te
 * yok — ölçüldü (tur-11, 32 derleme hatası).
 */
class VoiceApiClientTest {

    // ── Elde yazılmış mini HTTP sunucusu ─────────────────────────────

    private class Request(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private class Response(val code: Int, val contentType: String, val body: ByteArray) {
        constructor(code: Int, json: String) : this(
            code, "application/json", json.toByteArray(Charsets.UTF_8),
        )
    }

    private class TinyServer(private val onRequest: (Request) -> Response) {
        private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private var running = true
        val port: Int get() = socket.localPort

        fun start() {
            Thread {
                while (running) {
                    val s = runCatching { socket.accept() }.getOrNull() ?: continue
                    Thread { serve(s) }.apply { isDaemon = true }.start()
                }
            }.apply { isDaemon = true }.start()
        }

        fun stop() {
            running = false
            runCatching { socket.close() }
        }

        private fun serve(socket: Socket) {
            socket.use { s ->
                val input = s.getInputStream().buffered()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val h = readLine(input) ?: break
                    if (h.isEmpty()) break
                    val i = h.indexOf(':')
                    if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
                }
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                val body = ByteArray(len)
                var read = 0
                while (read < len) {
                    val n = input.read(body, read, len - read)
                    if (n < 0) break
                    read += n
                }
                val res = onRequest(Request(parts[0], parts[1], headers, body))
                val head = buildString {
                    append("HTTP/1.1 ").append(res.code).append(' ')
                    append(if (res.code == 200) "OK" else "ERR").append("\r\n")
                    append("Content-Type: ").append(res.contentType).append("\r\n")
                    append("Content-Length: ").append(res.body.size).append("\r\n")
                    append("Connection: close\r\n\r\n")
                }
                s.getOutputStream().apply {
                    write(head.toByteArray(Charsets.UTF_8))
                    write(res.body)
                    flush()
                }
            }
        }

        private fun readLine(input: InputStream): String? {
            val buf = StringBuilder()
            var b = input.read()
            if (b < 0) return null
            while (b >= 0) {
                if (b == 13) {
                    input.read()
                    break
                }
                if (b == 10) break
                buf.append(b.toInt().toChar())
                b = input.read()
            }
            return buf.toString()
        }
    }

    // ── Test kurulumu ────────────────────────────────────────────────

    private lateinit var server: TinyServer
    private var port = 0
    private val seen = mutableListOf<Triple<String, String, String>>()
    private val bodies = mutableListOf<String>()
    private val rawBodies = mutableListOf<ByteArray>()
    private var failCode = 0
    private val ogg = ByteArray(40) { 0x4F }

    @Before
    fun setUp() {
        failCode = 0
        server = TinyServer { req ->
            seen += Triple(
                req.method,
                req.path,
                req.headers[HermesClient.SESSION_HEADER.lowercase()].orEmpty(),
            )
            bodies += req.body.toString(Charsets.UTF_8)
            rawBodies += req.body
            when {
                failCode > 0 -> Response(failCode, """{"error":"reddedildi"}""")
                req.path == "/health" -> Response(200, HEALTH_JSON)
                req.path == "/transcribe" -> Response(200, TRANSCRIPT_JSON)
                req.path == "/synthesize" -> Response(200, "audio/ogg", ogg)
                else -> Response(404, """{"error":"not found"}""")
            }
        }
        server.start()
        port = server.port
    }

    @After
    fun tearDown() {
        server.stop()
    }

    /** Profil kimliği test başına benzersiz: `AddressHealth` süreç genelinde
     *  yaşar; 403/404 denemesi adresi devre dışı bırakır ve aynı kimlikle
     *  sonraki test o adresi atlardı (test sırası bağımlılığı). */
    private fun client(token: String = "tok-123") =
        VoiceApiClient(listOf("http://127.0.0.1:$port"), token, "voice-test-${System.nanoTime()}")

    // ── Sözleşme ─────────────────────────────────────────────────────

    @Test
    fun `health sozlesme alanlarini cozer`() = runBlocking {
        val h = client().health()
        assertTrue(h.ok)
        assertTrue(h.sttOk)
        assertEquals("acik", h.engines["kahya"])
        assertEquals("GET", seen.first().first)
        assertEquals("/health", seen.first().second)
    }

    @Test
    fun `her istekte oturum tokeni basligi gider`() = runBlocking {
        client().health()
        assertEquals("tok-123", seen.first().third)
    }

    @Test
    fun `token bos ise baslik gonderilmez`() = runBlocking {
        client(token = "").health()
        assertEquals("", seen.first().third)
    }

    @Test
    fun `transcribe multipart alan adi audio ve mime dogru`() = runBlocking {
        val text = client().transcribe(ByteArray(16), "kayit.ogg", "audio/ogg")
        assertEquals("bu bir sesli mesaj denemesidir", text)
        val body = bodies.first()
        assertTrue(body.contains("name=\"audio\""))
        assertTrue(body.contains("filename=\"kayit.ogg\""))
        assertTrue(body.contains("audio/ogg"))
        assertEquals("POST", seen.first().first)
        assertEquals("/transcribe", seen.first().second)
    }

    @Test
    fun `transcribe ogg baytlarini bozmadan yukler`() = runBlocking {
        val audio = ByteArray(1_024) { (it % 251).toByte() }
        audio[0] = 'O'.code.toByte(); audio[1] = 'g'.code.toByte()
        audio[2] = 'g'.code.toByte(); audio[3] = 'S'.code.toByte()
        client().transcribe(audio, "kayit.ogg", "audio/ogg")
        // Gövde ikili: yüklenen multipart'ın içinde ses baytları birebir duruyor mu?
        val raw = rawBodies.first()
        assertTrue("ses baytları gövdede bulunamadı", raw.contains(audio))
    }

    @Test
    fun `synthesize sozlesme govdesi ve ogg baytlari`() = runBlocking {
        val bytes = client().synthesize("Merhaba dünya", VoiceSpeakLogic.Engine.KADIN)
        assertEquals(ogg.size, bytes.size)
        val body = bodies.first()
        assertTrue(body.contains("\"text\":\"Merhaba dünya\""))
        assertTrue(body.contains("\"engine\":\"kadin\""))
        assertEquals("/synthesize", seen.first().second)
    }

    @Test
    fun `kahya motoru istegi kahya olarak gider`() = runBlocking {
        // Tur-21: Engine.DEFAULT artık YEREL (cihaz içi motor — bu HTTP ucu
        // sözleşmesi değildir). Bulut motoru kimliği KAHYA ile doğrulanır.
        client().synthesize("deneme", VoiceSpeakLogic.Engine.KAHYA)
        assertTrue(bodies.first().contains("\"engine\":\"kahya\""))
    }

    @Test
    fun `calisan adres hatirlanir`() = runBlocking {
        val c = client()
        c.health()
        assertEquals("http://127.0.0.1:$port", c.working)
    }

    @Test
    fun `403 token mesajina cevrilir`() = runBlocking {
        failCode = 403
        val e = runCatching { client().health() }.exceptionOrNull()
        assertTrue(e is VoiceApiException)
        assertTrue(e!!.message!!.contains("403"))
        assertTrue(e.message!!.contains("token"))
    }

    @Test
    fun `404 ucu yok mesajina cevrilir`() = runBlocking {
        failCode = 404
        val e = runCatching { client().synthesize("x", VoiceSpeakLogic.Engine.KAHYA) }
            .exceptionOrNull()
        assertTrue(e!!.message!!.contains("404"))
        assertTrue(e.message!!.contains("voice_api"))
    }

    @Test
    fun `iki adresli istemci calisan adrese duser`() = runBlocking {
        val c = VoiceApiClient(
            listOf("http://127.0.0.1:1", "http://127.0.0.1:$port"),
            "tok-123",
            "voice-test-fallback-${System.nanoTime()}",
        )
        assertTrue(c.health().ok)
        assertEquals("http://127.0.0.1:$port", c.working)
    }

    @Test
    fun `transport arayuzu ayni yolu kullanir`() = runBlocking {
        val t = HttpVoiceTransport(client())
        assertEquals("bu bir sesli mesaj denemesidir", t.transcribe(ByteArray(4), "k.ogg", "audio/ogg"))
        assertTrue(t.synthesize("merhaba", VoiceSpeakLogic.Engine.KAHYA).isNotEmpty())
        assertEquals("http://127.0.0.1:$port", t.working)
    }

    @Test
    fun `sentez zaman asimi guvenlik payiyla 420 sn`() = runBlocking {
        // tur-12 canlı ölçümü: soğuk /synthesize 297,5 sn (173-187 sn iddiasının
        // üzerinde) → 300 sn tavanında yalnız ~2,5 sn pay kalıyordu. tur-12b:
        // tavan 420 sn. Kayıt (60 sn) ve transcribe (60/90 sn) DEĞİŞMEZ.
        assertEquals(420_000L, com.hermes.mobile.data.VoiceApiEndpoints.SYNTH_TIMEOUT_MS)
        assertEquals(60_000L, com.hermes.mobile.data.VoiceApiEndpoints.MAX_RECORD_MS)
        assertTrue(client().health().ok)
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    private companion object {
        const val HEALTH_JSON =
            """{"ok":true,"stt":"acik","engines":{"kahya":"acik","chatterbox":"kapali","kadin":"kapali"}}"""
        const val TRANSCRIPT_JSON = """{"text":"bu bir sesli mesaj denemesidir","lang":"tr"}"""
    }
}
