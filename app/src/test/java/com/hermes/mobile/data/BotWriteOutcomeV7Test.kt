package com.hermes.mobile.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class BotWriteOutcomeV7Test {
    private fun failure(status: String, response: String): Throwable? {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val received = CompletableFuture<String>()
            val worker = Thread {
                try { server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val input = socket.getInputStream().bufferedReader()
                    val line = input.readLine()
                    var length = 0
                    while (true) {
                        val header = input.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                    }
                    repeat(length) { check(input.read() >= 0) }
                    received.complete(line)
                    val bytes = response.toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                } } catch (e: Exception) { received.completeExceptionally(e) }
            }.apply { isDaemon = true; start() }
            try {
                val error = runCatching { runBlocking {
                    BotApiClient("http://127.0.0.1:${server.localPort}", "fixture-only").request(
                        "POST", "/api/bots/bot-a/chat", buildJsonObject { put("message", "Once") },
                    )
                } }.exceptionOrNull()
                assertEquals("POST /api/bots/bot-a/chat HTTP/1.1", received.get(3, TimeUnit.SECONDS))
                return error
            } finally { worker.join(1000) }
        }
    }

    @Test fun serverFailureAfterAWriteIsUnknownRatherThanSafeToResend() {
        assertTrue(failure("503 Service Unavailable", "<html>offline</html>") is BotWriteOutcomeUnknownException)
    }

    @Test fun malformedSuccessAfterAWriteIsAlsoUnknown() {
        assertTrue(failure("200 OK", "not-json") is BotWriteOutcomeUnknownException)
    }

    @Test fun explicitRejectionStaysConcreteAndDoesNotEchoCredentialBodies() {
        val error = failure("401 Unauthorized", """{"message":"fixture-only token rejected"}""")
        assertTrue(error is BotApiException)
        assertEquals(401, (error as BotApiException).statusCode)
        assertFalse(error.message.orEmpty().contains("fixture-only"))
    }
}
