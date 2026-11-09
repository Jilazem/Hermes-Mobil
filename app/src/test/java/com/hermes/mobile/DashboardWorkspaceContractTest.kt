package com.hermes.mobile

import com.hermes.mobile.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DashboardWorkspaceContractTest {
    private suspend fun serve(code: Int, body: String, action: suspend (String) -> Unit): String {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val captured = CompletableFuture<String>()
            val thread = Thread {
                try { server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; lines += line }
                    val count = lines.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                    repeat(count) { reader.read() }
                    val bytes = body.toByteArray()
                    socket.getOutputStream().write("HTTP/1.1 $code Response\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                    captured.complete(lines.joinToString("\n"))
                } } catch (error: Throwable) { captured.completeExceptionally(error) }
            }.also { it.isDaemon = true; it.start() }
            try { action("http://127.0.0.1:${server.localPort}/hermes") }
            finally { thread.join(3500) }
            return captured.get(1, TimeUnit.SECONDS)
        }
    }

    @Test fun dashboardTokenIsOnlySentToNamespacedDashboardPath() = runBlocking {
        val captured = serve(200, "{\"bots\":[]}") { base ->
            val client = BotApiClient.fromDashboard(ServerProfile(id="account-a", name="Dashboard", baseUrl=base, token="unit-secret"))
            assertTrue(client.request("GET", "/api/bots").containsKey("bots"))
        }
        assertTrue(captured.startsWith("GET /hermes/mobile-api/bot/api/bots HTTP/1.1"))
        assertTrue(captured.contains("X-Hermes-Session-Token: unit-secret", true))
        assertFalse(captured.contains("Authorization:", true))
        assertFalse(captured.lineSequence().first().contains("secret"))
    }

    @Test fun delete204IsAConfirmedEmptyResult() = runBlocking {
        serve(204, "") { base ->
            val client = BotApiClient.fromDashboard(ServerProfile(name="t",baseUrl=base,token="unit-secret"))
            assertTrue(client.request("DELETE", "/api/bots/test").isEmpty())
        }; Unit
    }

    @Test fun gateway503WriteStaysUnknown() = runBlocking {
        serve(503, "{}") { base ->
            val client = BotApiClient.fromDashboard(ServerProfile(name="t",baseUrl=base,token="unit-secret"))
            try { client.request("POST", "/api/bots", kotlinx.serialization.json.JsonObject(emptyMap())); fail() }
            catch (_: BotWriteOutcomeUnknownException) { }
        }; Unit
    }

    @Test fun changingLanAddressDoesNotResetTheConversationFence() {
        val profile = ServerProfile(id="same-account",name="t",baseUrl="http://192.168.1.101:9150",token="unit-secret")
        assertEquals(dashboardWorkspaceScope(profile), dashboardWorkspaceScope(profile.copy(baseUrl="https://example.org")))
        assertNotEquals(dashboardWorkspaceScope(profile), dashboardWorkspaceScope(profile.copy(token="another-secret")))
    }
}
