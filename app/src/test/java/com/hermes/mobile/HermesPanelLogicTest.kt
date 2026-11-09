package com.hermes.mobile

import android.hardware.display.DisplayManager
import com.hermes.mobile.car.PanelContentLogic
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.SessionMessage
import org.junit.Assert.*
import org.junit.Test

class HermesPanelLogicTest {
    @Test fun `panel display cannot fall through to phone auto mirroring`() {
        assertEquals(10, PanelContentLogic.DISPLAY_FLAGS)
        assertEquals(0, PanelContentLogic.DISPLAY_FLAGS and DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC)
        assertEquals(0, PanelContentLogic.DISPLAY_FLAGS and DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
        assertTrue(PanelContentLogic.DISPLAY_FLAGS and DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY != 0)
    }

    @Test fun `selected session never falls back to another conversation`() {
        val sessions = listOf(HermesSession(id = "other", startedAt = 30.0))
        assertNull(PanelContentLogic.session(sessions, "selected"))
    }

    @Test fun `recent active means newest open session excluding archived and empty ids`() {
        val sessions = listOf(
            HermesSession(id = "old", startedAt = 1.0),
            HermesSession(id = "recent", startedAt = 2.0),
            HermesSession(id = "ended", startedAt = 3.0, endedAt = 4.0),
            HermesSession(id = "", startedAt = 5.0),
        )
        assertEquals("recent", PanelContentLogic.session(sessions, null)?.id)
        assertEquals("ended", PanelContentLogic.session(sessions, "ended")?.id)
    }

    @Test fun `last message ignores tool output system prompts and empty assistant turns`() {
        val messages = listOf(
            SessionMessage(role = "user", content = "hello"),
            SessionMessage(role = "assistant", content = "A\n B"),
            SessionMessage(role = "tool", content = "private tool output"),
            SessionMessage(role = "system", content = "internal instruction"),
            SessionMessage(role = "assistant", content = "  "),
        )
        assertEquals("A B", PanelContentLogic.lastMessage(messages))
        assertNull(PanelContentLogic.lastMessage(emptyList()))
        assertEquals(400, PanelContentLogic.lastMessage(listOf(SessionMessage(role = "user", content = "a".repeat(1000))))?.length)
    }
}
