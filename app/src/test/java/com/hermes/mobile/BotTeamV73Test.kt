package com.hermes.mobile

import com.hermes.mobile.data.*
import com.hermes.mobile.data.bots.BotTeamLogic
import org.junit.Assert.*
import org.junit.Test

class BotTeamV73Test {
    @Test fun stackingMergesBothTeamsAndLeavesUnrelatedAssignments() {
        val groups=mapOf("a" to "Bir","b" to "Bir","c" to "Iki","d" to "Iki","e" to "Uc")
        assertEquals(mapOf("a" to "Iki","b" to "Iki","c" to "Iki","d" to "Iki","e" to "Uc"),BotTeamLogic.stack("a","c",groups.keys,groups))
    }
    @Test fun invalidOrSelfDropDoesNothing() {
        val groups=mapOf("a" to "Bir")
        assertEquals(groups,BotTeamLogic.stack("a","a",setOf("a","b"),groups))
        assertEquals(groups,BotTeamLogic.stack("missing","b",setOf("a","b"),groups))
    }
    @Test fun newTeamGetsUnusedNameAndOnlyTwoMembers() {
        val result=BotTeamLogic.stack("a","b",setOf("a","b","c"),mapOf("c" to "Minik ekip 1"))
        assertEquals("Minik ekip 2",result["a"]); assertEquals(result["a"],result["b"]); assertEquals("Minik ekip 1",result["c"])
    }
    @Test fun batchHasUniqueIdsSharedInstructionAndPreservesPausedQueue() {
        val tasks=BotTeamLogic.tasks("Minikler",listOf("a","b","a"),"Ortak istek",100)
        assertEquals(listOf(100L,101L),tasks.map { it.id });assertEquals(listOf("a","b"),tasks.map { it.target })
        assertTrue(tasks.all { "Ortak istek" in it.text && "#100" in it.text })
        val queued=tasks.fold(BotQueueState(paused=true)) { state,task -> BotFleetLogic.enqueue(state,task) }
        assertTrue(BotFleetLogic.readyToDispatch(queued).isEmpty()); assertTrue(queued.tasks.all { it.state=="queued" })
        assertEquals(2,BotFleetLogic.readyToDispatch(BotFleetLogic.resume(queued)).size)
    }
    @Test fun completionPreservesBoundedAnswerAcrossRecovery() {
        val state=BotQueueState(tasks=listOf(FleetTaskData(1,"a","test",state="running")))
        val done=BotFleetLogic.complete(state,1,"session",emptyList(),"x".repeat(5000)).tasks.single()
        assertEquals(4000,done.answer?.length);assertEquals(done,BotFleetLogic.recover(listOf(done)).single())
    }
}
