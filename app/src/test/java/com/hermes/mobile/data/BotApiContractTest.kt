package com.hermes.mobile.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class BotApiContractTest {
    private data class CapturedRequest(val line: String, val headers: List<String>, val body: String)

    private fun <T> withServer(status: String = "200 OK", response: String, block: (String, CompletableFuture<CapturedRequest>) -> T): T {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val captured = CompletableFuture<CapturedRequest>()
            val worker = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val reader = socket.getInputStream().bufferedReader()
                        val line = reader.readLine()
                        val headers = mutableListOf<String>()
                        while (true) { val header = reader.readLine(); if (header.isNullOrEmpty()) break; headers += header }
                        val length = headers.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                        val chars = CharArray(length)
                        var offset = 0
                        while (offset < length) { val n = reader.read(chars, offset, length - offset); if (n < 0) break; offset += n }
                        captured.complete(CapturedRequest(line, headers, String(chars, 0, offset)))
                        val bytes = response.toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes); flush()
                        }
                    }
                } catch (e: Exception) { captured.completeExceptionally(e) }
            }.apply { isDaemon = true; start() }
            return try { block("http://127.0.0.1:${server.localPort}", captured) } finally { worker.join(3000) }
        }
    }

    @Test fun `chat preserves the selected conversation over an actual HTTP request`() = runBlocking {
        withServer(response = """{"session_id":"session-a","messages":[{"role":"assistant","content":"ok"}]}""") { base, captured ->
            runBlocking {
                val result = BotApiClient(base, "test-only-token").request("POST", "/api/bots/bot-a/chat", buildJsonObject {
                    put("message", "follow up"); put("session_id", "session-a")
                })
                assertEquals("session-a", botSessionId(result))
                val request = captured.get(3, TimeUnit.SECONDS)
                assertEquals("POST /api/bots/bot-a/chat HTTP/1.1", request.line)
                assertTrue(request.headers.any { it == "Authorization: Bearer test-only-token" })
                assertEquals("session-a", Json.parseToJsonElement(request.body).jsonObject.text("session_id"))
            }
        }
    }

    @Test fun `HTML error retains HTTP status and is not a JSON parse failure`() {
        withServer("503 Service Unavailable", "<html>offline</html>") { base, _ ->
            val error = runCatching { runBlocking { BotApiClient(base, "test").request("GET", "/api/bots") } }.exceptionOrNull()
            assertTrue(error is BotApiException)
            assertEquals(503, (error as BotApiException).statusCode)
        }
    }

    @Test fun `invalid success response cannot masquerade as an empty bot roster`() {
        withServer(response = "<html>wrong server</html>") { base, _ ->
            assertNotNull(runCatching { runBlocking { BotApiClient(base, "test").request("GET", "/api/bots") } }.exceptionOrNull())
        }
    }

    @Test fun `queues are isolated by both endpoint and account without storing token in key`() {
        val first = BotApiClient("http://localhost:9151", "account-one").storageKey
        assertEquals(first, BotApiClient("http://localhost:9151/", "account-one").storageKey)
        assertNotEquals(first, BotApiClient("http://localhost:9152", "account-one").storageKey)
        assertNotEquals(first, BotApiClient("http://localhost:9151", "account-two").storageKey)
        assertFalse(first.contains("account"))
    }

    @Test fun `unread keys count actual assistant messages including duplicates and exclude prompts`() {
        val session = Json.parseToJsonElement("""{"id":"a","messages":[{"role":"system","content":"hidden"},{"role":"user","content":"hi"},{"role":"assistant","content":"same"},{"role":"assistant","content":"same"}]}""").jsonObject
        val keys = botAssistantMessageKeys(listOf(session))
        assertEquals(2, keys.size)
        assertEquals(keys, botAssistantMessageKeys(listOf(session)))
        val otherSession = JsonObject(session + ("id" to JsonPrimitive("b")))
        assertEquals(4, botAssistantMessageKeys(listOf(session, otherSession)).size)
        assertEquals(3, botVisibleMessages(session).size)
    }
}
