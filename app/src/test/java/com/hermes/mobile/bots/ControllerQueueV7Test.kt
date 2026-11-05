package com.hermes.mobile.bots

import com.hermes.mobile.data.bots.*
import org.junit.Assert.*
import org.junit.Test

class ControllerQueueV7Test {
    @Test fun saveMustSucceedBeforeDispatchIsClaimed() {
        var fail = false
        val queue = DurableControllerQueue { if (fail) error("disk unavailable") }
        queue.enqueue(ControllerTask("request-0001", "Araştır"))
        fail = true
        assertTrue(runCatching { queue.claim() }.isFailure)
        assertEquals("queued", queue.snapshot().single().state)
    }

    @Test fun sameIdIsNotReplayedAndUnknownBlocksNextClaim() {
        val first = ControllerTask("request-0001", "Bir kez gönder")
        val restored = DurableControllerQueue(listOf(first.copy(state = "running"))) {}
        restored.enqueue(first)
        restored.enqueue(ControllerTask("request-0002", "Sonraki iş"))
        assertEquals(2, restored.snapshot().size)
        assertEquals("uncertain", restored.snapshot().first().state)
        assertNull(restored.claim())
        restored.update(first.id, "completed")
        assertEquals("request-0002", restored.claim()!!.id)
    }

    @Test fun conversationCannotDriftAndRunningWorkCannotBeCancelledLocally() {
        val queue = DurableControllerQueue { }
        queue.enqueue(ControllerTask("request-0001", "İş", "thread-a")); queue.claim()
        assertTrue(runCatching { queue.bindConversation("request-0001", "thread-b") }.isFailure)
        assertTrue(runCatching { queue.cancel("request-0001") }.isFailure)
    }

    @Test fun workspaceSearchAndGroupComposeWithoutChangingSource() {
        val source = listOf(AgentSummary("1", "Kaynak araştırmacısı", "Güncel kaynaklar", "m1", "Araştırma"), AgentSummary("2", "Özet", "Kısa not", "m2", "Ofis"))
        assertEquals(listOf("1"), BotWorkspaceLogic.filter(source, "kaynak m1", "Araştırma").map { it.id })
        assertTrue(BotWorkspaceLogic.filter(source, "kaynak", "Ofis").isEmpty())
        assertEquals(2, source.size)
        assertEquals("a", BotWorkspaceLogic.latestSessionId(listOf("b" to 1.0, "a" to 3.0)))
    }
}
