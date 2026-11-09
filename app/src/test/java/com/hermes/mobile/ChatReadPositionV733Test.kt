package com.hermes.mobile

import android.app.Application
import android.content.Context
import com.hermes.mobile.data.ChatReadCursor
import com.hermes.mobile.data.ChatScrollPositionStore
import com.hermes.mobile.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class ChatReadPositionV733Test {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun rows(vararg items: ChatItem) = readableChatRows(items.map { ChatRow.Single(it) })
    @Before fun clear() { app.getSharedPreferences("chat_scroll_pos", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun missingLiveTimestampsAndRegeneratedKeysStillRestoreFirstUnread() {
        val first=rows(ChatItem.User("u1","merhaba"),ChatItem.Assistant("a1","yanıt"))
        val marker=ChatReadCursor(first.last().anchor)
        val reloaded=rows(ChatItem.User("new-u","merhaba",100.0),ChatItem.Assistant("new-a","yanıt",ts=101.0),
            ChatItem.Assistant("new-b","yeni mesaj",ts=102.0))
        assertEquals(ChatOpeningPosition(2,true), chatOpeningPosition(reloaded,3,marker))
    }
    @Test fun firstRowUnreadIsAValidTargetAndNoUnreadOpensTheLatestExchange() {
        val history=rows(ChatItem.Assistant("a","ilk",ts=20.0),ChatItem.Assistant("b","son",ts=30.0))
        assertEquals(ChatOpeningPosition(0,true),chatOpeningPosition(history,2,ChatReadCursor("0".repeat(64),10.0)))
        assertEquals(ChatOpeningPosition(1,false),chatOpeningPosition(history,2,ChatReadCursor(history.last().anchor,30.0)))
        assertEquals(ChatOpeningPosition(1,false),chatOpeningPosition(history,2,null))
    }
    @Test fun serverAndAgentNamespacesWithIdenticalSessionIdsRemainIsolated() {
        val marker=ChatReadCursor("a".repeat(64),100.0)
        ChatScrollPositionStore(app).save("server-a|agent-a|same-session",marker)
        val reopened=ChatScrollPositionStore(app)
        assertEquals(marker,reopened.read("server-a|agent-a|same-session"))
        assertNull(reopened.read("server-b|agent-a|same-session"))
        assertNull(reopened.read("server-a|agent-b|same-session"))
    }
    @Test fun readTimestampNeverMovesBackAndInvalidTimestampsAreNotPersisted() {
        val store=ChatScrollPositionStore(app)
        store.save("scope",ChatReadCursor("a".repeat(64),100.0))
        store.save("scope",ChatReadCursor("b".repeat(64),90.0))
        assertEquals("a".repeat(64),store.read("scope")?.anchor)
        store.save("scope",ChatReadCursor("c".repeat(64),Double.NaN))
        assertEquals(100.0,store.read("scope")?.timestamp)
        store.save("empty",ChatReadCursor("z",10.0));assertNull(store.read("empty"))
    }
    @Test fun toolsReasoningAndStreamingFragmentsNeverBecomeReadCursors() {
        val history=rows(ChatItem.User("u","soru",10.0),ChatItem.Thinking("r","devam"),
            ChatItem.Tool("t","terminal",ToolState.Done),ChatItem.Assistant("a","henüz bitmedi",true,20.0))
        assertEquals(1,history.size)
        assertEquals(0,history.single().index)
    }
    @Test fun removingOldRowsUsesTimestampFallbackInsteadOfAnUnrelatedRuntimeKey() {
        val history=rows(ChatItem.Assistant("a","okunmamış",ts=200.0),ChatItem.Assistant("b","son",ts=210.0))
        assertEquals(ChatOpeningPosition(0,true),chatOpeningPosition(history,2,ChatReadCursor("a".repeat(64),150.0)))
    }
    @Test fun identicalRepeatedExchangesDoNotSkipUnseenMessagesWithoutATimestamp() {
        val history=rows(ChatItem.User("u1","soru"),ChatItem.Assistant("a1","aynı yanıt"),
            ChatItem.User("u2","soru"),ChatItem.Assistant("a2","aynı yanıt"),ChatItem.Assistant("a3","yeni"))
        assertEquals(ChatOpeningPosition(2,true),chatOpeningPosition(history,5,ChatReadCursor(history[1].anchor)))
    }
    @Test fun cachedAndFreshIdentityMatchAcrossStreamingUpdatesAndToolFolding() {
        val cache=ChatReadIdentityCache()
        val base=listOf<ChatRow>(ChatRow.Single(ChatItem.User("u","test")),ChatRow.Single(ChatItem.Assistant("a","cevap")))
        assertEquals(readableChatRows(base),readableChatRows(base,cache))
        val next=base+ChatRow.Single(ChatItem.Assistant("b","akıyor",true))
        assertEquals(readableChatRows(next),readableChatRows(next,cache))
        assertEquals(readableChatRows(base),readableChatRows(base,cache))
    }
}
