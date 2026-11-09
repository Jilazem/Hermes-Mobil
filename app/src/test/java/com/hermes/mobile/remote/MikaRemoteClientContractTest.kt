package com.hermes.mobile.remote

import com.hermes.mobile.data.remote.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Uses the actual client over loopback HTTP; no Mika installation or production task is touched. */
class MikaRemoteClientContractTest {
    private data class Captured(val line: String, val headers: Map<String, String>, val body: String)
    private fun connection(url: String) = RemoteAgentConnection(label = "Fixture", kind = RemoteAgentKind.MIKA,
        baseUrl = url, token = "fixture-token")

    private suspend fun <T> server(response: String, status: String = "200 OK", block: suspend (String, CompletableFuture<Captured>) -> T): T {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val captured = CompletableFuture<Captured>()
            val worker = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val input = socket.getInputStream()
                        val head = java.io.ByteArrayOutputStream()
                        while (true) {
                            val byte = input.read()
                            check(byte >= 0)
                            head.write(byte)
                            if (head.size() >= 4 && head.toByteArray().takeLast(4) == listOf<Byte>(13, 10, 13, 10)) break
                            check(head.size() <= 32768)
                        }
                        val lines = head.toString("ISO-8859-1").split("\r\n")
                        val headers = lines.drop(1).filter { ':' in it }.associate {
                            it.substringBefore(':').lowercase() to it.substringAfter(':').trim()
                        }
                        val body = ByteArray(headers["content-length"]?.toInt() ?: 0)
                        var offset = 0
                        while (offset < body.size) {
                            val read = input.read(body, offset, body.size - offset)
                            check(read > 0); offset += read
                        }
                        captured.complete(Captured(lines.first(), headers, body.toString(Charsets.UTF_8)))
                        val bytes = response.toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        socket.getOutputStream().write(bytes)
                        socket.getOutputStream().flush()
                    }
                } catch (error: Throwable) { captured.completeExceptionally(error) }
            }.apply { isDaemon = true; start() }
            return try { block("http://127.0.0.1:${server.localPort}", captured) }
            finally { server.close(); worker.join(1000) }
        }
    }

    private val conversation = "mcp-00000000-0000-4000-8000-000000000001"
    private fun task(status: String = "running", extra: String = "") =
        """{"task_id":"task-1","status":"$status","conversation":"$conversation","goal":"Araştır","answerable":false$extra}"""

    @Test fun capabilityDiscoveryRequiresActualV1TasksAndPreservesProxyPrefix() = runBlocking {
        server("""{"api_version":1,"server_version":"0.1.33","capabilities":["tasks","task_steer"],"limits":{"max_wait_s":50,"max_goal":4000,"max_answer":2000,"max_steer":4000}}""") { url, capture ->
            val capabilities = MikaRemoteClient(connection("$url/phone/mika/")).capabilities()
            assertEquals(1, capabilities.apiVersion)
            assertTrue("task_steer" in capabilities.capabilities)
            val request = capture.get(3, TimeUnit.SECONDS)
            assertEquals("GET /phone/mika/api/v1 HTTP/1.1", request.line)
            assertEquals("Bearer fixture-token", request.headers["authorization"])
            assertFalse(request.line.contains("fixture-token"))
        }
    }

    @Test fun wrongServiceIsNotReportedAsAConnectionSuccess() = runBlocking {
        server("""{"status":"ok"}""") { url, _ ->
            assertTrue(runCatching { MikaRemoteClient(connection(url)).capabilities() }.exceptionOrNull() is RemoteProtocolException)
        }
    }

    @Test fun submitSendsUnicodeGoalAndCanonicalConversationExactly() = runBlocking {
        server(task(), "201 Created") { url, capture ->
            val goal = "İlanları karşılaştır\nKaynakları sakla."
            val result = MikaRemoteClient(connection(url)).start(goal, conversation)
            val request = capture.get(3, TimeUnit.SECONDS)
            assertEquals("POST /api/v1/tasks HTTP/1.1", request.line)
            val body = Json.parseToJsonElement(request.body).jsonObject
            assertEquals(goal, body.getValue("goal").jsonPrimitive.content)
            assertEquals(conversation, body.getValue("conversation").jsonPrimitive.content)
            assertEquals("task-1", result.id)
            assertEquals("running", result.status)
            assertNull(result.verified)
        }
    }

    @Test fun malformedAcceptedWriteIsOutcomeUnknownInsteadOfFakeSuccess() = runBlocking {
        server("""{"accepted":true}""", "201 Created") { url, _ ->
            assertTrue(runCatching { MikaRemoteClient(connection(url)).start("Test") }.exceptionOrNull() is RemoteOutcomeUnknownException)
        }
    }

    @Test fun server5xxWriteIsUncertainAndNeverPresentedAsRejectedOrDone() = runBlocking {
        server("<html>gateway error</html>", "503 Unavailable") { url, capture ->
            val error = runCatching { MikaRemoteClient(connection(url)).start("Test") }.exceptionOrNull()
            assertTrue(error is RemoteOutcomeUnknownException)
            assertEquals("POST /api/v1/tasks HTTP/1.1", capture.get(3, TimeUnit.SECONDS).line)
        }
    }

    @Test fun authorizationFailureRemainsAConcreteHttpError() = runBlocking {
        server("""{"error":{"code":"access_denied","message":"fixture-token"}}""", "401 Unauthorized") { url, _ ->
            val error = runCatching { MikaRemoteClient(connection(url)).list() }.exceptionOrNull()
            assertTrue(error is RemoteApiException)
            assertEquals(401, (error as RemoteApiException).statusCode)
            assertFalse(error.message.orEmpty().contains("fixture-token"))
        }
    }

    @Test fun cancelAcknowledgementIsNotTerminalCancellation() = runBlocking {
        server("""{"task_id":"task-1","accepted":true}""", "202 Accepted") { url, capture ->
            val receipt = MikaRemoteClient(connection(url)).cancel("task-1")
            assertTrue(receipt.accepted)
            assertFalse(receipt.confirmedTerminal)
            assertEquals("cancellation_requested", receipt.status)
            assertEquals("{}", capture.get(3, TimeUnit.SECONDS).body)
        }
    }

    @Test fun recordedCancelledStatusCanBeRecognizedSeparately() = runBlocking {
        server(task("cancelled")) { url, _ ->
            assertTrue(MikaTaskStatus.finished(MikaRemoteClient(connection(url)).task("task-1").status))
        }
    }

    @Test fun waitIsBoundedReadAndRetainsSameTask() = runBlocking {
        server(task("running", ",\"changed\":false")) { url, capture ->
            val result = MikaRemoteClient(connection(url)).wait("task-1", 1)
            val request = capture.get(3, TimeUnit.SECONDS)
            assertEquals("POST /api/v1/tasks/task-1/wait HTTP/1.1", request.line)
            assertEquals(1, Json.parseToJsonElement(request.body).jsonObject["timeout_s"]!!.jsonPrimitive.int)
            assertEquals("task-1", result.id)
            assertFalse(MikaTaskStatus.finished(result.status))
        }
    }

    @Test fun questionAndSteeringHaveDistinctPayloadsAndReceipts() = runBlocking {
        server("""{"task_id":"task-1","accepted":true}""") { url, capture ->
            assertTrue(MikaRemoteClient(connection(url)).answer("task-1", "İkinci seçenek").accepted)
            assertEquals("POST /api/v1/tasks/task-1/answer HTTP/1.1", capture.get(3, TimeUnit.SECONDS).line)
        }
        server("""{"task_id":"task-1","message_id":"msg-2","accepted":true}""", "202 Accepted") { url, capture ->
            val receipt = MikaRemoteClient(connection(url)).steer("task-1", "Sadece güncel kaynakları kullan")
            assertEquals("msg-2", receipt.messageId)
            assertEquals("POST /api/v1/tasks/task-1/steer HTTP/1.1", capture.get(3, TimeUnit.SECONDS).line)
        }
    }

    @Test fun phoneOnlyInteractionNeverBecomesRemotelyAnswerable() = runBlocking {
        server(task("waiting_on_phone", ",\"waiting_for\":\"confirmation\"")) { url, _ ->
            val state = MikaRemoteClient(connection(url)).task("task-1")
            assertFalse(state.answerable)
            assertFalse(MikaTaskStatus.finished(state.status))
        }
        server("""{"error":{"code":"conflict"}}""", "409 Conflict") { url, _ ->
            val error = runCatching { MikaRemoteClient(connection(url)).answer("task-1", "yes") }.exceptionOrNull()
            assertTrue(error is RemoteApiException && error.statusCode == 409)
        }
    }

    @Test fun malformedTaskStatusCannotTurnIntoSuccessfulProgress() = runBlocking {
        server(task("everything-great")) { url, _ ->
            assertTrue(runCatching { MikaRemoteClient(connection(url)).task("task-1") }.exceptionOrNull() is RemoteProtocolException)
        }
    }
}
