package com.hermes.mobile

import com.hermes.mobile.data.ConnectionState
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * v6.4 — WS kendini iptal döngüsü onarımı (Görev Hattı #1023-466290).
 *
 * Sahada ölçülen döngü: uygulama soketi ~1 sn'de bir kapatıp yeniden açıyordu
 * (22.433 aç/kapa çifti/gün, medyan ömür 0,97 sn, kod 1006). Kök neden: iptal
 * edilmiş ESKİ soketin gecikmeli `onFailure`'ı yeni soketi kapatıyor ve her
 * hata ayrı bir yeniden bağlanma işi planlıyordu (çift plan → çift açılış).
 *
 * Bu testler sahte `WebSocketListener`/`WebSocket` ile ağ olmadan ölçer:
 *  A) 3 iptal edilmiş soketin gecikmeli onFailure'ı → YALNIZ 1 reconnect planı,
 *  B) açık (canlı) soket varken tekrar connect() soketi iptal etmez,
 *  C) onOpen reconnectAttempts'i hemen sıfırlamaz; 30 sn kararlı
 *     açıktan sonra sıfırlar (üretim eşiği, kısaltılmaz).
 */
class GatewayReconnectTest {

    private val token = "test-token-1024"

    private fun profile() = ServerProfile(
        name = "sahte",
        baseUrl = "http://127.0.0.1:9", // ağa çıkılmaz: fabrika sahte soket döndürür
        token = token,
    )

    private class FakeSocket(private val req: Request) : WebSocket {
        override fun request(): Request = req
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = true
        override fun send(bytes: ByteString): Boolean = true
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() = Unit
    }

    private class FakeFactory {
        val listeners = mutableListOf<WebSocketListener>()
        val sockets = mutableListOf<FakeSocket>()
        val calls: Int get() = listeners.size

        fun create(req: Request, listener: WebSocketListener): WebSocket {
            val s = FakeSocket(req)
            listeners += listener
            sockets += s
            return s
        }
    }

    private fun openResponse(req: Request): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(101)
            .message("Switching Protocols")
            .build()

    @Test
    fun `uc iptal edilmis soketin gecikmeli onFailure'i yalniz 1 reconnect planlar`() {
        val fab = FakeFactory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = GatewayWsClient(
            profile(),
            readyTimeoutMs = 300,
            scopeOverride = scope,
            webSocketFactory = { req, l -> fab.create(req, l) },
        )
        try {
            // Üç ayrı açılış → üç ayrı soket nesli. Her açılış öncekini iptal eder;
            // iptal edilen soketin onFailure'ı OkHttp'de GECİKMELİ gelir.
            client.connect(fresh = true)
            client.connect(fresh = true)
            client.connect(fresh = true)
            assertEquals("üç soket açılmalıydı", 3, fab.calls)

            val iptal = IOException("Socket closed")
            fab.listeners[0].onFailure(fab.sockets[0], iptal, null)
            fab.listeners[1].onFailure(fab.sockets[1], iptal, null)

            assertEquals(
                "eski nesiller reconnect PLANLAMAMALI",
                0,
                client.reconnectScheduleCount,
            )
            assertTrue(
                "güncel soket hâlâ bağlanıyor olmalı, gelen: ${client.connection.value}",
                client.connection.value is ConnectionState.Connecting,
            )

            // GÜNCEL nesil (3. soket) gerçekten öldü → tek plan.
            fab.listeners[2].onFailure(fab.sockets[2], IOException("timeout"), null)

            assertEquals(
                "yalnız 1 reconnect planlanmalı (eski kod 3 planlıyordu)",
                1,
                client.reconnectScheduleCount,
            )
            assertEquals(
                "deneme sayacı tek artmalı (eski kod her iptalde artırıyordu)",
                1,
                client.reconnectAttempts,
            )
            assertTrue(
                "durum Reconnecting olmalı, gelen: ${client.connection.value}",
                client.connection.value is ConnectionState.Reconnecting,
            )
        } finally {
            client.close()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun `canli soket varken tekrar connect soketi iptal etmez`() {
        val fab = FakeFactory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = GatewayWsClient(
            profile(),
            readyTimeoutMs = 300,
            scopeOverride = scope,
            webSocketFactory = { req, l -> fab.create(req, l) },
        )
        try {
            client.connect(fresh = true)
            fab.listeners[0].onOpen(fab.sockets[0], openResponse(fab.sockets[0].request()))
            assertTrue(
                "onOpen sonrası Open beklenirdi, gelen: ${client.connection.value}",
                client.connection.value is ConnectionState.Open,
            )

            client.connect() // niyet yok → açık soket korunur (eski davranış)

            assertEquals("ikinci soket açılmamalı", 1, fab.calls)
            assertTrue(
                "canlı soket korunmalı, gelen: ${client.connection.value}",
                client.connection.value is ConnectionState.Open,
            )
            assertEquals("iptal edilen soket yok → plan yok", 0, client.reconnectScheduleCount)
        } finally {
            client.close()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun `onOpen yeniden baglanma deneme sayacini sifirlamaz`() {
        val fab = FakeFactory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = GatewayWsClient(
            profile(),
            readyTimeoutMs = 300,
            scopeOverride = scope,
            webSocketFactory = { req, l -> fab.create(req, l) },
        )
        try {
            client.connect(fresh = true)
            fab.listeners[0].onFailure(fab.sockets[0], IOException("kopma"), null)
            assertEquals(1, client.reconnectAttempts)

            // Yeniden bağlanma açılışı (planlı işi beklemeden doğrudan aç).
            client.connect()
            assertEquals("yeni soket açılmalıydı", 2, fab.calls)
            fab.listeners[1].onOpen(fab.sockets[1], openResponse(fab.sockets[1].request()))

            assertEquals(
                "onOpen deneme sayacını sıfırlamamalı (backoff 1→2→4→8→15 ezilmesin)",
                1,
                client.reconnectAttempts,
            )
        } finally {
            client.close()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun `30 sn kararli acikliktan sonra deneme sayaci sifirlanir`() {
        val fab = FakeFactory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = GatewayWsClient(
            profile(),
            readyTimeoutMs = 60_000,
            scopeOverride = scope,
            webSocketFactory = { req, l -> fab.create(req, l) },
        )
        try {
            client.connect(fresh = true)
            fab.listeners[0].onFailure(fab.sockets[0], IOException("kopma"), null)
            assertEquals(1, client.reconnectAttempts)
            assertEquals(1, client.reconnectScheduleCount)

            client.connect()
            val openedAt = System.nanoTime()
            fab.listeners[1].onOpen(fab.sockets[1], openResponse(fab.sockets[1].request()))
            assertEquals("onOpen hemen sıfırlamamalı", 1, client.reconnectAttempts)
            assertTrue(client.connection.value is ConnectionState.Open)

            fun elapsedMs(): Long = (System.nanoTime() - openedAt) / 1_000_000L
            while (elapsedMs() < 20_000L) Thread.sleep(50)
            assertEquals("20 sn'de hâlâ sıfırlanmamalı", 1, client.reconnectAttempts)
            assertTrue(client.connection.value is ConnectionState.Open)
            assertEquals(1, client.reconnectScheduleCount)
            assertEquals("yalnız kopma ve yeniden açılış", 2, fab.calls)

            var resetAt = -1L
            while (elapsedMs() < 36_000L) {
                if (client.reconnectAttempts == 0) {
                    resetAt = elapsedMs()
                    break
                }
                Thread.sleep(50)
            }
            assertTrue(
                "sıfırlama 30 sn kararlı açıktan sonra olmalı, resetAt=$resetAt attempts=${client.reconnectAttempts}",
                resetAt >= 30_000L,
            )
            assertTrue(client.connection.value is ConnectionState.Open)
            assertEquals("kararlı pencerede ek plan olmamalı", 1, client.reconnectScheduleCount)
            assertEquals(2, fab.calls)
        } finally {
            client.close()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
