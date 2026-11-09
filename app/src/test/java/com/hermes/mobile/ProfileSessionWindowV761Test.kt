package com.hermes.mobile

import com.hermes.mobile.data.*
import org.junit.Assert.*
import org.junit.Test

class ProfileSessionWindowV761Test {
    private fun row(id: String) = HermesSession(id = id)
    @Test fun pinnedRowsDoNotSkipTheNextPageAndDuplicatesStayUnique() {
        val first = appendProfileSessionPage(ProfileSessionWindow(), SessionsResponse(listOf(row("a"),row("b"),row("pinned")),total=5,limit=2,offset=0),0)
        assertEquals(2,first.nextOffset)
        val second = appendProfileSessionPage(first,SessionsResponse(listOf(row("c"),row("d"),row("pinned")),total=5,limit=2,offset=2),2)
        assertEquals(4,second.nextOffset)
        assertEquals(listOf("a","b","pinned","c","d"),second.rows.map { it.id })
    }
    @Test fun refreshReplacesOnlyTheVisibleInventoryAndEmptyResultEndsPaging() {
        val old=ProfileSessionWindow(listOf(row("old")),99,10)
        val fresh=appendProfileSessionPage(old,SessionsResponse(emptyList(),total=0,limit=100,offset=0),0)
        assertTrue(fresh.rows.isEmpty()); assertNull(fresh.nextOffset)
    }
    @Test fun wrongOffsetAndPrematureEmptyPageCannotLookComplete() {
        assertTrue(runCatching { appendProfileSessionPage(ProfileSessionWindow(),SessionsResponse(listOf(row("a")),total=10,limit=2,offset=0),2) }.isFailure)
        assertTrue(runCatching { appendProfileSessionPage(ProfileSessionWindow(),SessionsResponse(emptyList(),total=10,limit=2,offset=0),0) }.isFailure)
    }
    @Test fun legacyServerWithoutPaginationIsStillReadable() {
        val page=appendProfileSessionPage(ProfileSessionWindow(),SessionsResponse(listOf(row("a"))),0)
        assertEquals("a",page.rows.single().id);assertNull(page.nextOffset)
    }
}
