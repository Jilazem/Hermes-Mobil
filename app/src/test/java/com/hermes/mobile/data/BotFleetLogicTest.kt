package com.hermes.mobile.data

import org.junit.Assert.*
import org.junit.Test

class BotFleetLogicTest {
    @Test fun `group filter uses persistent bot assignments`() {
        val groups = mapOf("a" to "Saha", "b" to "Ofis")
        assertEquals(listOf("a"), BotFleetLogic.visibleBotIds(listOf("a", "b"), "Saha", groups))
        assertEquals(listOf("a", "b"), BotFleetLogic.visibleBotIds(listOf("a", "b"), BotFleetLogic.ALL, groups))
    }

    @Test fun `mode is bot specific`() {
        assertEquals(BotMode.ASK, BotFleetLogic.modeFor("a", mapOf("a" to "ask", "b" to "auto")))
        assertEquals(BotMode.AUTO, BotFleetLogic.modeFor("b", mapOf("a" to "ask", "b" to "auto")))
    }

    @Test fun `missing progress is unknown and never fabricated`() {
        assertEquals(BotProgress.Unknown, BotFleetLogic.progress(null))
        assertEquals(BotProgress.Known("working", 37, listOf("observe", "tap")), BotFleetLogic.progress(RemoteBotState("working", 37, listOf("observe", "tap"))))
    }

    @Test fun `unread counts messages newer than local read timestamp`() {
        assertEquals(2, BotFleetLogic.unreadCount(listOf(10L, 20L, 30L), 10L))
        assertEquals(0, BotFleetLogic.unreadCount(emptyList(), 0L))
    }

    @Test fun `pause queues new work resume releases queue and stop marks request`() {
        var state = BotQueueState()
        state = BotFleetLogic.pause(state)
        state = BotFleetLogic.enqueue(state, FleetTaskData(1, "a", "ilk"))
        assertEquals("queued", state.tasks.single().state)
        state = BotFleetLogic.resume(state)
        assertEquals("ready", state.tasks.single().state)
        state = BotFleetLogic.stop(state, 1)
        assertEquals("stopped", state.tasks.single().state)
    }

    @Test fun `queued instruction can be edited and deleted`() {
        val initial = BotQueueState(tasks = listOf(FleetTaskData(1, "a", "eski")))
        val edited = BotFleetLogic.edit(initial, 1, "yeni")
        assertEquals("yeni", edited.tasks.single().text)
        assertTrue(BotFleetLogic.delete(edited, 1).tasks.isEmpty())
    }

    @Test fun `group deletion unassigns members and returns all filter`() {
        val result = BotFleetLogic.deleteGroup(
            selectedGroup = "Saha",
            deletedGroup = "Saha",
            assignments = mapOf("a" to "Saha", "b" to "Ofis"),
        )
        assertEquals(BotFleetLogic.ALL, result.selectedGroup)
        assertEquals(mapOf("b" to "Ofis"), result.assignments)
    }

    @Test fun `ask requires explicit approval and auto dispatches immediately`() {
        assertTrue(BotFleetLogic.requiresApproval(BotMode.ASK))
        assertFalse(BotFleetLogic.requiresApproval(BotMode.AUTO))
    }

    @Test fun `skill labels trim deduplicate and remove`() {
        assertEquals(listOf("kamera", "ses"), BotFleetLogic.addLabel(listOf("kamera"), " ses "))
        assertEquals(listOf("kamera"), BotFleetLogic.addLabel(listOf("kamera"), "kamera"))
        assertTrue(BotFleetLogic.removeLabel(listOf("kamera"), "kamera").isEmpty())
    }

    @Test fun `schedule stores enabled preset and custom time`() {
        val preset = BotSchedule(enabled = true, preset = SchedulePreset.DAILY, time = "09:30")
        assertTrue(BotFleetLogic.validSchedule(preset))
        assertFalse(BotFleetLogic.validSchedule(preset.copy(time = "25:00")))
    }

    @Test fun `missing api is queued offline not successful`() {
        assertEquals(DispatchOutcome.QUEUED_OFFLINE, BotFleetLogic.dispatchOutcome(apiAvailable = false, error = null))
        assertEquals(DispatchOutcome.FAILED, BotFleetLogic.dispatchOutcome(apiAvailable = true, error = IllegalStateException("HTTP 404")))
        assertEquals(DispatchOutcome.SENT, BotFleetLogic.dispatchOutcome(apiAvailable = true, error = null))
    }

    @Test fun `resume and repeated enqueue never duplicate an approved task`() {
        val task = FleetTaskData(1, "a", "first")
        val paused = BotFleetLogic.enqueue(BotQueueState(paused = true), task)
        val resumed = BotFleetLogic.resume(paused)
        val repeated = BotFleetLogic.enqueue(resumed, task)
        assertEquals(1, repeated.tasks.size)
        assertEquals("ready", repeated.tasks.single().state)
    }

    @Test fun `dispatch preserves order within each bot and allows independent bots`() {
        val state = BotQueueState(tasks = listOf(
            FleetTaskData(1, "a", "first", "ready"),
            FleetTaskData(2, "a", "second", "ready"),
            FleetTaskData(3, "b", "other", "ready"),
        ))
        assertEquals(listOf(1L, 3L), BotFleetLogic.readyToDispatch(state).map { it.id })
        val running = BotFleetLogic.start(state, 1, "conversation-a")
        assertEquals(listOf(3L), BotFleetLogic.readyToDispatch(running).map { it.id })
        val completed = BotFleetLogic.complete(running, 1, "conversation-a", listOf("assistant"))
        assertEquals(listOf(2L, 3L), BotFleetLogic.readyToDispatch(completed).map { it.id })
        assertEquals("conversation-a", completed.tasks.first().sessionId)
    }

    @Test fun `pause keeps accepted request observable and never claims remote stop`() {
        val state = BotQueueState(tasks = listOf(FleetTaskData(1, "a", "accepted", "running")))
        assertEquals("running", BotFleetLogic.pause(state).tasks.single().state)
        assertEquals("running", BotFleetLogic.stop(state, 1).tasks.single().state)
        assertTrue(BotFleetLogic.readyToDispatch(BotFleetLogic.pause(state)).isEmpty())
    }

    @Test fun `unobserved request blocks dependent work and is never replayed after restart`() {
        val state = BotQueueState(tasks = listOf(
            FleetTaskData(1, "a", "accepted", "running"), FleetTaskData(2, "a", "next", "ready"),
        ))
        val interrupted = BotFleetLogic.interrupted(state, 1, "connection lost")
        assertEquals("unknown", interrupted.tasks.first().state)
        assertTrue(BotFleetLogic.readyToDispatch(interrupted).isEmpty())
        assertEquals("unknown", BotFleetLogic.recover(state.tasks).first().state)
        assertEquals("unknown", BotFleetLogic.resume(interrupted).tasks.first().state)
    }

    @Test fun `blank edit cannot erase a queued instruction`() {
        val state = BotQueueState(tasks = listOf(FleetTaskData(1, "a", "keep me")))
        assertEquals("keep me", BotFleetLogic.edit(state, 1, " ").tasks.single().text)
    }
}
