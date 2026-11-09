package com.hermes.mobile

import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test

/** Exercise real JSON-RPC frames, responses and events; no live production session is modified. */
class GatewayInterruptGenerationTest {
    private class Harness : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sent = mutableListOf<JsonObject>()
        var reply: (JsonObject) -> JsonObject = { buildJsonObject { put("result", buildJsonObject {}) } }
        private lateinit var listener: WebSocketListener
        private lateinit var socket: WebSocket
        val client = GatewayWsClient(
            ServerProfile(name = "test", baseUrl = "http://127.0.0.1:9", token = "test-only"),
            scopeOverride = scope,
            webSocketFactory = { request, callbacks ->
                listener = callbacks
                object : WebSocket {
                    override fun request(): Request = request
                    override fun queueSize() = 0L
                    override fun send(text: String): Boolean {
                        val frame = Json.parseToJsonElement(text).jsonObject
                        sent += frame
                        val response = buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", frame.getValue("id"))
                            reply(frame).forEach { (key, value) -> put(key, value) }
                        }
                        listener.onMessage(this, response.toString())
                        return true
                    }
                    override fun send(bytes: ByteString) = true
                    override fun close(code: Int, reason: String?) = true
                    override fun cancel() = Unit
                }.also { socket = it }
            },
        ).also { it.connect() }

        fun event(sid: String, generation: Long) {
            listener.onMessage(socket, buildJsonObject {
                put("method", "event")
                put("params", buildJsonObject {
                    put("type", "message.delta"); put("session_id", sid)
                    put("execution_generation", generation)
                    put("payload", buildJsonObject { put("text", "x") })
                })
            }.toString())
        }

        fun result(value: String) { reply = { buildJsonObject { put("result", Json.parseToJsonElement(value)) } } }
        fun lastParams() = sent.last().getValue("params").jsonObject
        override fun close() { client.close(); scope.cancel() }
    }

    @Test fun `legacy interrupt retains its original params`() = runBlocking {
        Harness().use { h ->
            h.client.interrupt("legacy")
            assertEquals(setOf("session_id"), h.lastParams().keys)
        }
    }

    @Test fun `resume generation zero is sent and later event advances it`() = runBlocking {
        Harness().use { h ->
            h.result("""{"session_id":"chat","execution_generation":0}""")
            h.client.request("session.resume", buildJsonObject { put("session_id", "chat") })
            h.result("{}")
            h.client.interrupt("chat")
            assertEquals(0L, h.lastParams().getValue("execution_generation").jsonPrimitive.long)
            h.event("chat", 5)
            h.client.interrupt("chat")
            assertEquals(5L, h.lastParams().getValue("execution_generation").jsonPrimitive.long)
        }
    }

    @Test fun `receipt ref identifies session and nullable queued receipt cannot erase generation`() = runBlocking {
        Harness().use { h ->
            h.result("""{"ref":{"session_id":"chat"},"execution_generation":3}""")
            h.client.request("prompt.receipt")
            h.result("""{"ref":{"session_id":"chat"},"execution_generation":null}""")
            h.client.request("prompt.receipt")
            h.result("{}")
            h.client.interrupt("chat")
            assertEquals(3L, h.lastParams().getValue("execution_generation").jsonPrimitive.long)
        }
    }

    @Test fun `delayed reply cannot lower observed turn and another session cannot inherit it`() = runBlocking {
        Harness().use { h ->
            h.event("chat", 8)
            h.result("""{"session_id":"chat","execution_generation":2}""")
            h.client.request("session.resume", buildJsonObject { put("session_id", "chat") })
            h.result("{}")
            h.client.interrupt("chat")
            assertEquals(8L, h.lastParams().getValue("execution_generation").jsonPrimitive.long)
            h.client.interrupt("other")
            assertFalse(h.lastParams().containsKey("execution_generation"))
        }
    }

    @Test fun `stale generation refusal is surfaced without retrying a newer turn`() = runBlocking {
        Harness().use { h ->
            h.event("chat", 4)
            h.reply = { buildJsonObject {
                put("error", buildJsonObject { put("code", 4001); put("message", "stale_generation") })
            } }
            val failure = runCatching { h.client.interrupt("chat") }.exceptionOrNull()
            assertEquals("stale_generation", failure?.message)
            assertEquals(1, h.sent.count { it["method"]?.jsonPrimitive?.content == "session.interrupt" })
        }
    }

    @Test fun `socket reopening discards old owner generations`() = runBlocking {
        Harness().use { h ->
            h.event("chat", 9)
            h.client.connect(fresh = true)
            h.client.interrupt("chat")
            assertFalse(h.lastParams().containsKey("execution_generation"))
        }
    }
}
