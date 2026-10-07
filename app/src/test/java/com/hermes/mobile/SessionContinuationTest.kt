package com.hermes.mobile

import com.hermes.mobile.data.SessionBinding
import com.hermes.mobile.data.restoreConversation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SessionContinuationTest {
    @Test fun `cold restore submits to new runtime while retaining durable conversation`() = runBlocking {
        val calls = mutableListOf<String>()
        val saved = "20260923_065221_eb4c52"
        val sid = restoreConversation("old-runtime", saved,
            activate = { calls += "activate:$it"; error("not live") },
            resume = {
                calls += "resume:$it"
                SessionBinding.parse(Json.parseToJsonElement("""{"session_id":"new-runtime","session_key":"$saved"}"""), it).runtimeId
            })
        calls += "prompt.submit:$sid"
        assertEquals(listOf("activate:old-runtime", "resume:$saved", "prompt.submit:new-runtime"), calls)
    }

    @Test fun `successful activation never resumes another conversation`() = runBlocking {
        val sid = restoreConversation("live", "stored", { it }, { fail("unexpected resume"); "" })
        assertEquals("live", sid)
    }

    @Test fun `restore failure propagates rather than starting empty conversation`() = runBlocking {
        val failure = runCatching { restoreConversation("old", "selected", { error("gone") }, { error("selected missing") }) }
        assertEquals("selected missing", failure.exceptionOrNull()?.message)
    }

    @Test fun `cancelled selection does not resume or change another conversation`() = runBlocking {
        val failure = runCatching { restoreConversation("old", "stored",
            { throw CancellationException("selection changed") }, { fail("unexpected resume"); "" }) }
        assertTrue(failure.exceptionOrNull() is CancellationException)
    }

    @Test fun `create and activate use server stored key`() {
        val result = SessionBinding.parse(Json.parseToJsonElement("""{"session_id":"runtime","stored_session_id":"durable"}"""), "runtime")
        assertEquals(SessionBinding("runtime", "durable"), result)
    }

    @Test fun `malformed success cannot silently use old identifier`() {
        for (wire in listOf("null", "{}", """{"session_id":null}""", """{"session_id":""}""")) {
            assertTrue(runCatching { SessionBinding.parse(Json.parseToJsonElement(wire), "old") }.isFailure)
        }
    }
}
