package com.hermes.mobile

import com.hermes.mobile.car.MolaGuideLogic
import com.hermes.mobile.car.MolaHostState
import com.hermes.mobile.car.MolaSessionActionLogic
import com.hermes.mobile.car.PanelConnection
import com.hermes.mobile.car.PanelContentLogic
import com.hermes.mobile.data.CarConversation
import com.hermes.mobile.data.SessionMessage
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class MolaV7BehaviorTest {
    @Test fun `saved session belongs only to its own server profile`() {
        assertEquals("chat", PanelContentLogic.savedSession("a", "a|chat"))
        assertNull(PanelContentLogic.savedSession("a", "b|chat"))
        assertNull(PanelContentLogic.savedSession("a", "a|"))
    }

    @Test fun `speaker reads assistant answer rather than last user instruction`() {
        val messages = listOf(
            SessionMessage(role = "assistant", content = "The answer"),
            SessionMessage(role = "user", content = "Next question"),
            SessionMessage(role = "tool", content = "internal output"),
        )
        assertEquals("The answer", PanelContentLogic.lastAssistantMessage(messages))
        assertNull(PanelContentLogic.lastAssistantMessage(listOf(SessionMessage(role = "user", content = "hello"))))
    }

    @Test fun `server preview success never becomes real car surface evidence`() {
        assertEquals(2, MolaGuideLogic.nextStep(PanelConnection.Connected, true, MolaHostState.NotObserved))
        assertFalse(MolaGuideLogic.hasCarSurfaceEvidence(MolaHostState.SurfaceAvailable))
        assertFalse(MolaGuideLogic.hasCarSurfaceEvidence(MolaHostState.Released))
        assertFalse(MolaGuideLogic.hasCarSurfaceEvidence(MolaHostState.Failed))
        assertTrue(MolaGuideLogic.hasCarSurfaceEvidence(MolaHostState.Rendering))
        assertEquals(3, MolaGuideLogic.nextStep(PanelConnection.Connected, true, MolaHostState.Rendering))
        assertEquals(1, MolaGuideLogic.nextStep(PanelConnection.Unavailable, true, MolaHostState.Rendering))
    }

    @Test fun `stop refuses missing session and a changed active server`() {
        MolaSessionActionLogic.validateTarget("a", "a", "chat")
        assertTrue(runCatching { MolaSessionActionLogic.validateTarget("a", "b", "chat") }.isFailure)
        assertTrue(runCatching { MolaSessionActionLogic.validateTarget("a", "a", null) }.isFailure)
        assertTrue(runCatching { MolaSessionActionLogic.validateTarget(null, null, "chat") }.isFailure)
    }

    @Test fun `car conversation returns an atomic profile and session scoped snapshot`() {
        val failed = AtomicReference<Throwable?>(null)
        val writer = Thread {
            try {
                repeat(500) { i ->
                    val profile = if (i % 2 == 0) "a" else "b"
                    CarConversation.start(profile, "chat", "title", listOf(CarConversation.Msg(false, profile, i.toLong())))
                }
            } catch (e: Throwable) { failed.set(e) }
        }
        try {
            writer.start()
            repeat(500) {
                for (profile in listOf("a", "b")) {
                    val snapshot = CarConversation.messagesFor(profile, "chat")
                    assertTrue(snapshot == null || snapshot.all { it.text == profile })
                    assertNull(CarConversation.messagesFor(profile, "wrong-session"))
                }
            }
            writer.join()
            failed.get()?.let { throw it }
        } finally {
            writer.join()
            CarConversation.close("a", "chat")
            CarConversation.close("b", "chat")
        }
    }
}
