package com.hermes.mobile

import com.hermes.mobile.data.ConnectionState
import com.hermes.mobile.data.GatewayReady
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/**
 * İş #1021 KÖK NEDEN 3 — `gateway.ready` el sıkışması.
 *
 * Gerçek gateway (tui_gateway/ws.py) 101'den hemen sonra tek bir olay yazar:
 *   {"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready",…}}
 * APK bu olayı beklemeden `Open` diyordu; hazır olmayan sokete istek gidiyor,
 * kullanıcı sessiz düşüş görüyordu. Bu testler HAM soket dublörüyle ölçer:
 *  1) ready gelirse → Open (ve token SORGUDA, başlıkta değil zorunlu değil),
 *  2) ready gelmezse → somut Error ("gateway.ready" geçer), sessiz kalmaz,
 *  3) yanlış token → 403 el sıkışması Error'a dönüşür.
 */
class GatewayReadyTest {

    private val token = "test-token-1021"

    @Test
    fun `gateway ready gelince baglanti Open olur ve token sorguda gider`() {
        FakeWsServer(token, sendReady = true).use { server ->
            val client = GatewayWsClient(
                ServerProfile(name = "dublor", baseUrl = "http://127.0.0.1:${server.port}", token = token),
            )
            try {
                client.connect()
                val son = await(client) { it is ConnectionState.Open }
                assertTrue("ready sonrası Open beklenirdi, gelen: $son", son is ConnectionState.Open)
                val satir = server.requestLine.get().orEmpty()
                assertTrue("token sorguda yok: $satir", satir.contains("token=$token"))
                assertTrue("gateway.ready bekleniyordu ama bağlantı hazır sayıldı", GatewayReady.isReady("gateway.ready"))
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `gateway ready gelmezse baglanti ACIK kalir yalniz teshis uyarisi yazilir`() {
        FakeWsServer(token, sendReady = false).use { server ->
            val client = GatewayWsClient(
                ServerProfile(name = "dublor", baseUrl = "http://127.0.0.1:${server.port}", token = token),
                readyTimeoutMs = 600,
            )
            try {
                client.connect()
                // J1: 101 yeterli — Open hemen gelmeli, ready karesi beklenmez.
                val ilk = await(client, timeoutMs = 4_000) { it is ConnectionState.Open }
                assertTrue("101'den sonra Open beklenirdi, gelen: $ilk", ilk is ConnectionState.Open)
                // Uyarı süresi geçtikten sonra da AÇIK kalmalı (eski sunucu senaryosu).
                Thread.sleep(1_200)
                val son = client.connection.value
                assertTrue("ready gelmedi diye bağlantı düşmemeli, gelen: $son", son is ConnectionState.Open)
                val uyari = GatewayReady.missedMessage(600)
                assertTrue("uyarı gateway.ready'den söz etmiyor: $uyari", uyari.contains("gateway.ready"))
                assertTrue("uyarı bağlantının açık kaldığını söylemiyor: $uyari", uyari.contains("Bağlantı açık"))
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `yanlis token 403 el sikismasi Error olur`() {
        FakeWsServer(token, sendReady = true).use { server ->
            val client = GatewayWsClient(
                ServerProfile(name = "dublor", baseUrl = "http://127.0.0.1:${server.port}", token = "yanlis-token"),
            )
            try {
                client.connect()
                val son = await(client, timeoutMs = 5_000) { it is ConnectionState.Error }
                assertTrue("Error beklenirdi, gelen: $son", son is ConnectionState.Error)
                val mesaj = (son as ConnectionState.Error).reason
                assertTrue("403 raporda yok: $mesaj", mesaj.contains("403") || mesaj.contains("Token reddedildi"))
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `ready olayi disindaki olaylar Open ilan etmez`() {
        assertFalse(GatewayReady.isReady("message.delta"))
        assertFalse(GatewayReady.isReady("session.info"))
        assertFalse(GatewayReady.isReady(null))
        assertEquals("gateway.ready", GatewayReady.EVENT_TYPE)
    }

    private fun await(
        client: GatewayWsClient,
        timeoutMs: Long = 4_000,
        kosul: (ConnectionState) -> Boolean,
    ): ConnectionState {
        val bitis = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < bitis) {
            val s = client.connection.value
            if (kosul(s)) return s
            Thread.sleep(25)
        }
        return client.connection.value
    }

    /** Ham soket WS dublörü: token sorguda değilse 403; doğruysa 101 (+ isteğe bağlı ready). */
    private class FakeWsServer(
        private val expected: String,
        private val sendReady: Boolean,
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = serverSocket.localPort
        val requestLine = AtomicReference<String?>(null)

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
            requestLine.set(satir)
            var key: String? = null
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i <= 0) continue
                if (line.substring(0, i).trim().lowercase() == "sec-websocket-key") {
                    key = line.substring(i + 1).trim()
                }
            }
            val sorgu = satir.split(" ").getOrNull(1).orEmpty()
            val out = socket.getOutputStream()
            if (key == null || !sorgu.contains("token=$expected")) {
                val body = "forbidden"
                out.write(
                    ("HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\n" +
                        "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                        .toByteArray(Charsets.ISO_8859_1)
                )
                out.flush()
                return
            }
            out.write(
                ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
                    "Connection: Upgrade\r\nSec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n")
                    .toByteArray(Charsets.ISO_8859_1)
            )
            if (sendReady) out.write(textFrame(READY_JSON))
            out.flush()
            // Soketi açık tut: istemci ready'yi beklerken kapanmasın.
            runCatching { socket.getInputStream().read() }
        }

        private fun textFrame(payload: String): ByteArray {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            require(bytes.size < 126)
            return byteArrayOf(0x81.toByte(), bytes.size.toByte()) + bytes
        }

        override fun close() = runCatching { serverSocket.close() }.let { }

        private fun acceptKey(key: String): String = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
        )
    }

    private companion object {
        const val READY_JSON =
            """{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":true}}}"""
    }
}
