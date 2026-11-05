package com.hermes.mobile.remote

import com.hermes.mobile.data.remote.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Real loopback HTTP through the production client; never touches a user's device or model. */
class PhongentRemoteClientContractTest {
    private data class Response(val code: Int = 200, val body: String)
    private data class Request(val line: String, val headers: Map<String, String>, val body: String)
    private val pendingId = "12345678-1234-1234-1234-12345678901212345678-1234-1234-1234-123456789012"
    private val taskId = "request-12345678"

    private suspend fun <T> withServer(responses: List<Response>, block: suspend (String, List<Request>) -> T): T {
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            val requests = Collections.synchronizedList(mutableListOf<Request>())
            val finished = CompletableFuture<Unit>()
            val worker = Thread {
                try {
                    responses.forEach { response -> server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().buffered()
                        fun line(): String {
                            val bytes = java.io.ByteArrayOutputStream()
                            while (true) {
                                val byte = input.read(); check(byte >= 0)
                                if (byte == 10) break
                                if (byte != 13) bytes.write(byte)
                                check(bytes.size() < 8192)
                            }
                            return bytes.toString("UTF-8")
                        }
                        val first = line()
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val header = line(); if (header.isEmpty()) break
                            headers[header.substringBefore(':').lowercase(Locale.ROOT)] = header.substringAfter(':').trim()
                        }
                        val size = headers["content-length"]?.toInt() ?: 0
                        val body = ByteArray(size)
                        var read = 0
                        while (read < size) { val count = input.read(body, read, size - read); check(count > 0); read += count }
                        requests += Request(first, headers, body.toString(Charsets.UTF_8))
                        val payload = response.body.toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().use { output ->
                            output.write(("HTTP/1.1 ${response.code} Result\r\nContent-Type: application/json\r\n" +
                                "Connection: close\r\nContent-Length: ${payload.size}\r\n\r\n").toByteArray())
                            output.write(payload); output.flush()
                        }
                    } }
                    finished.complete(Unit)
                } catch (error: Throwable) { finished.completeExceptionally(error) }
            }.apply { isDaemon = true; start() }
            try {
                val result = block("http://127.0.0.1:${server.localPort}", requests)
                finished.get(5, TimeUnit.SECONDS)
                return result
            } finally { server.close(); worker.join(2000) }
        }
    }

    private fun client(base: String, token: String = "contract-test-only") = PhongentRemoteClient(
        RemoteAgentConnection(label = "Contract device", kind = RemoteAgentKind.PHONGENT, baseUrl = base, token = token),
    )

    private fun state(phase: String = "idle", queueVersion: Int = 1, runId: String = "") =
        """{"phase":"$phase","device":"Test phone","detail":"","runId":"$runId","currentConversationId":"thread-a","taskQueueVersion":$queueVersion,"exchanges":[]}"""

    private fun rows(status: String) =
        """{"items":[{"id":"$taskId","text":"Contract goal","conversation":"thread-a","state":"$status","error":"","result":""}]}"""

    @Test fun pairingWaitsForPhysicalApprovalAndNeverSendsAnExistingBearer() = runBlocking {
        withServer(listOf(Response(202, """{"status":"pending"}"""), Response(body = """{"status":"approved","token":"pair-test-only"}"""))) { base, captured ->
            val api = client(base)
            val pending = api.beginPair("Hermes test", pendingId)
            assertEquals("pending", pending.status)
            assertNull(pending.token)
            val approved = api.pollPair(pendingId)
            assertEquals("approved", approved.status)
            assertEquals("pair-test-only", approved.token)
            assertFalse(approved.toString().contains("pair-test-only"))
            assertTrue(captured[0].line.startsWith("POST /pair "))
            assertTrue(captured[1].line.startsWith("GET /pair/$pendingId "))
            assertTrue(captured.all { "authorization" !in it.headers })
            assertEquals(pendingId, Json.parseToJsonElement(captured[0].body).jsonObject["requestId"]!!.jsonPrimitive.content)
        }
    }

    @Test fun queuedSubmissionPreservesConversationAndDoesNotPretendCompletion() = runBlocking {
        withServer(listOf(Response(body = state()), Response(body = """{"id":"$taskId","status":"queued","conversationId":"thread-a"}"""))) { base, captured ->
            val result = client(base).submit("Contract goal", requestId = taskId)
            assertEquals("queued", result.status)
            assertEquals("thread-a", result.conversation)
            assertNull(result.answer)
            assertNull(result.verified)
            assertTrue(captured[1].line.startsWith("POST /tasks "))
            val sent = Json.parseToJsonElement(captured[1].body).jsonObject
            assertEquals(taskId, sent["id"]!!.jsonPrimitive.content)
            assertEquals("thread-a", sent["conversationId"]!!.jsonPrimitive.content)
            assertTrue(captured.all { it.headers["authorization"] == "Bearer contract-test-only" })
        }
    }

    @Test fun legacyDeviceUsesItsCommandEndpointOnlyWhenQueueVersionIsAbsent() = runBlocking {
        withServer(listOf(Response(body = state(queueVersion = 0).replace(",\"taskQueueVersion\":0", "")), Response(202, """{"id":"$taskId","status":"accepted","conversationId":"thread-a"}"""))) { base, captured ->
            assertEquals("accepted", client(base).submit("Contract goal", requestId = taskId).status)
            assertTrue(captured[1].line.startsWith("POST /command "))
            assertEquals("task", Json.parseToJsonElement(captured[1].body).jsonObject["action"]!!.jsonPrimitive.content)
        }
    }

    @Test fun uncertainWriteUsesTheSameReceiptAndNeverReplaysThePost() = runBlocking {
        withServer(listOf(Response(body = state()), Response(503, """{"error":"server_unavailable"}"""), Response(body = rows("running")))) { base, captured ->
            val result = client(base).submit("Contract goal", requestId = taskId)
            assertEquals(taskId, result.id)
            assertEquals("running", result.status)
            assertEquals(1, captured.count { it.line.startsWith("POST ") })
            assertTrue(captured.last().line.startsWith("GET /tasks "))
        }
    }

    @Test fun uncertainWriteWithNoReceiptRemainsUnknownWithoutANewRequestId() = runBlocking {
        withServer(listOf(Response(body = state()), Response(503, "{}"), Response(body = """{"items":[]}"""))) { base, captured ->
            val error = runCatching { client(base).submit("Contract goal", requestId = taskId) }.exceptionOrNull()
            assertTrue(error is RemoteOutcomeUnknownException)
            assertEquals(1, captured.count { it.line.startsWith("POST ") })
            assertEquals(taskId, Json.parseToJsonElement(captured[1].body).jsonObject["id"]!!.jsonPrimitive.content)
        }
    }

    @Test fun stopAcknowledgementIsNotATerminalCancelledState() = runBlocking {
        withServer(listOf(Response(body = state("running", runId = "run-12345678")),
            Response(202, """{"status":"accepted"}"""), Response(body = state("running", runId = "run-12345678")))) { base, captured ->
            val receipt = client(base).stop("run-12345678")
            assertTrue(receipt.accepted)
            assertFalse(receipt.confirmedTerminal)
            val sent = Json.parseToJsonElement(captured[1].body).jsonObject
            assertEquals("stop", sent["action"]!!.jsonPrimitive.content)
            assertEquals("run-12345678", sent["runId"]!!.jsonPrimitive.content)
        }
    }

    @Test fun queuedCancellationRequiresTheReturnedTaskToBeCancelled() = runBlocking {
        withServer(listOf(Response(body = rows("cancelled")))) { base, captured ->
            val receipt = client(base).cancelQueued(taskId)
            assertTrue(receipt.confirmedTerminal)
            assertTrue(receipt.accepted)
            assertEquals("cancel", Json.parseToJsonElement(captured.single().body).jsonObject["action"]!!.jsonPrimitive.content)
        }
    }

    @Test fun stateVersionAndPrefixAreReadRatherThanAssumed() = runBlocking {
        withServer(listOf(Response(body = state("waiting", queueVersion = 0)))) { base, captured ->
            val state = client("$base/device").state()
            assertEquals(0, state.taskQueueVersion)
            assertFalse(state.canResume)
            assertTrue(captured.single().line.startsWith("GET /device/state "))
        }
    }
}
