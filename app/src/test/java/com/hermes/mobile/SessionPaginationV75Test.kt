package com.hermes.mobile

import com.hermes.mobile.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionPaginationV75Test {
    @Test fun injectedLiveRowsDoNotSkipStoredSessionsOrDuplicateBots()=runBlocking {
        val offsets=mutableListOf<Int>()
        val result=collectSessionPages { offset ->
            offsets+=offset
            SessionsResponse(listOf(HermesSession(id="live"))+ (offset until minOf(offset+100,240)).map { HermesSession(id="s$it") },240,100,offset)
        }
        assertEquals(listOf(0,100,200),offsets)
        assertEquals(241,result.size)
        assertEquals((0 until 240).map { "s$it" }.toSet(),result.map { it.id }.filter { it!="live" }.toSet())
    }
    @Test fun nonPaginatedOldServerRemainsCompatible()=runBlocking {
        assertEquals(listOf("one"),collectSessionPages { SessionsResponse(listOf(HermesSession(id="one"))) }.map { it.id })
    }
    @Test fun unadvancedPageIsReportedInsteadOfPretendingListIsComplete()=runBlocking {
        val failure=runCatching {collectSessionPages { SessionsResponse(listOf(HermesSession(id="one")),240,100,0) }}.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
    }
}
