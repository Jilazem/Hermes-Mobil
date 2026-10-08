package com.hermes.mobile

import com.hermes.mobile.data.GatewayEvent
import com.hermes.mobile.data.NotificationReply
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class NotificationReplyTest {
    private fun event(type: String, text: String = "", sid: String = "chat") =
        GatewayEvent(type, sid, buildJsonObject { put("text", text) })

    @Test fun `fragments wait for completion and are accumulated`() {
        val reply = NotificationReply("chat")
        assertFalse(reply.accept(event("message.delta", "Merhaba ")))
        assertFalse(reply.accept(event("message.delta", "Gökhan.")))
        assertNull(reply.result)
        assertTrue(reply.accept(event("message.complete")))
        assertEquals("Merhaba Gökhan.", reply.result)
    }
    @Test fun `completion text works without deltas and is not duplicated`() {
        val reply = NotificationReply("chat")
        assertTrue(reply.accept(event("message.complete", "Tam yanıt")))
        assertFalse(reply.accept(event("message.delta", "eski parça")))
        assertEquals("Tam yanıt", reply.result)
    }
    @Test fun `other sessions and unscoped events cannot complete this reply`() {
        val reply = NotificationReply("chat")
        assertFalse(reply.accept(event("message.complete", "başka", "other")))
        assertFalse(reply.accept(GatewayEvent("message.complete")))
        assertNull(reply.result)
    }
    @Test fun `approval does not execute anything and requests phone continuation`() {
        val reply = NotificationReply("chat")
        assertTrue(reply.accept(event("approval.request", "private tool arguments")))
        assertTrue(reply.result!!.contains("onayın"))
        assertFalse(reply.result!!.contains("private"))
    }
    @Test fun `server errors do not reveal endpoints or credentials`() {
        val reply = NotificationReply("chat")
        assertTrue(reply.accept(event("error", "secret-token@private-server")))
        assertFalse(reply.result!!.contains("secret"))
    }
    @Test fun `empty completion gives a useful fallback rather than waiting forever`() {
        val reply = NotificationReply("chat")
        assertTrue(reply.accept(event("message.complete")))
        assertFalse(reply.result.isNullOrBlank())
    }
}
