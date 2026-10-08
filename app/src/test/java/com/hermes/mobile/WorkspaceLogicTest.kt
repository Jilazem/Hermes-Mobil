package com.hermes.mobile

import com.hermes.mobile.data.*
import com.hermes.mobile.ui.drawerRows
import com.hermes.mobile.ui.followedRows
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceLogicTest {
    private val working = LiveSession(id = "runtime", sessionKey = "database", status = "working")
    private val flags = SessionFlags(followed = setOf("database"))
    private fun rows(flags: SessionFlags, archived: Boolean = true) = drawerRows(
        sessions = emptyList(), live = listOf(working), liveByDbId = emptyMap(),
        flags = flags, cronNames = emptyMap(), showArchived = archived, currentSessionId = null)

    @Test fun `old flags retain pins without silently following them`() {
        val old = Json.decodeFromString<SessionFlags>("""{"pinned":["one"],"archived":[],"hidden":[],"renames":{}}""")
        assertEquals(setOf("one"), old.pinned)
        assertTrue(old.followed.isEmpty())
    }
    @Test fun `following survives serialization and is independent of pin`() {
        val restored = Json.decodeFromString<SessionFlags>(Json.encodeToString(flags.copy(pinned = setOf("other"))))
        assertEquals(setOf("database"), restored.followed)
        assertEquals(setOf("other"), restored.pinned)
    }
    @Test fun `runtime and database identifiers resolve to one followed row`() {
        assertEquals(1, followedRows(rows(flags)).size)
        assertEquals(1, followedRows(rows(SessionFlags(followed = setOf("runtime")))).size)
    }
    @Test fun `hidden live-only sessions cannot reappear in following`() {
        assertTrue(followedRows(rows(flags.copy(hidden = setOf("database")))).isEmpty())
        assertTrue(followedRows(rows(flags.copy(hidden = setOf("runtime")))).isEmpty())
    }
    @Test fun `archive remains followed while regular drawer respects archive filter`() {
        val archived = flags.copy(archived = setOf("database"))
        assertEquals(1, followedRows(rows(archived)).size)
        assertTrue(rows(archived, archived = false).isEmpty())
    }
    @Test fun `opening app or repeated polling does not generate historical updates`() {
        assertTrue(followedUpdates(null, listOf(working), flags).isEmpty())
        assertTrue(followedUpdates(listOf(working), listOf(working.copy(lastActive = 99.0)), flags).isEmpty())
    }
    @Test fun `working to idle is observed once with server preview`() {
        val idle = working.copy(status = "idle", preview = "İşlem sonucu hazır")
        val updates = followedUpdates(listOf(working), listOf(idle), flags)
        assertEquals("database", updates.single().sessionId)
        assertEquals("İşlem sonucu hazır", updates.single().text)
        assertTrue(followedUpdates(listOf(idle), listOf(idle), flags).isEmpty())
    }
    @Test fun `hidden and unfollowed sessions never generate updates`() {
        assertTrue(followedUpdates(listOf(working), emptyList(), SessionFlags()).isEmpty())
        assertTrue(followedUpdates(listOf(working), emptyList(), flags.copy(hidden = setOf("database"))).isEmpty())
    }
    @Test fun `leaving live list is not reported as a successful task`() {
        assertEquals("Canlı listeden ayrıldı", followedUpdates(listOf(working), emptyList(), flags).single().text)
    }
    @Test fun `notifications with identical session IDs stay isolated by server`() {
        val a = ActivityNotice(id = "a", profileId = "home", sessionId = "same", title = "A", text = "a")
        val b = a.copy(id = "b", profileId = "other", title = "B")
        assertEquals(listOf(a), noticesForProfile(listOf(a, b), "home"))
        assertTrue(noticesForProfile(listOf(a, b), null).isEmpty())
    }
    @Test fun `notification retention and replay cannot create duplicate keys`() {
        val base = (0..199).map { ActivityNotice(id = "$it", profileId = "home", title = "A", text = "a") }
        val replay = base[1].copy(read = true)
        assertEquals(200, appendNotice(base, replay).size)
        assertEquals(1, appendNotice(base, replay).count { it.id == replay.id })
        val next = replay.copy(id = "new")
        assertFalse(appendNotice(base, next).any { it.id == "199" })
    }
    @Test fun `inbox read status survives restart format`() {
        val item = ActivityNotice(id = "n", profileId = "p", title = "Notice", text = "body", read = true)
        val saved = Json.encodeToString(item)
        assertTrue(saved.contains("\"time\":"))
        assertEquals(item, Json.decodeFromString<ActivityNotice>(saved))
    }
    @Test fun `tool arguments and result objects are preserved`() {
        val started = GatewayEvent("tool.start", payload = buildJsonObject { put("arguments", buildJsonObject { put("path", "test.txt") }) })
        val ended = GatewayEvent("tool.complete", payload = buildJsonObject { put("result", "Tamamlandı") })
        assertEquals("{\"path\":\"test.txt\"}", started.toolDetail)
        assertEquals("Tamamlandı", ended.toolDetail)
    }
    @Test fun `complete history retains early messages after 150 rows`() {
        var key = 0
        val restored = restoreChatHistory((0..360).map { SessionMessage(role = "user", content = "Mesaj $it") }) { "${it}-${key++}" }
        assertEquals(361, restored.size)
        assertEquals("Mesaj 0", (restored.first() as ChatItem.User).text)
        assertEquals("Mesaj 360", (restored.last() as ChatItem.User).text)
    }
    @Test fun `history preserves supplied reasoning and tool arguments in order`() {
        var key = 0
        val source = Json.decodeFromString<SessionMessage>("""{"role":"assistant","reasoning":"Sunucunun açıklaması","content":"Kontrol ediyorum","tool_calls":[{"function":{"name":"search","arguments":"query"}}]}""")
        val restored = restoreChatHistory(listOf(source, SessionMessage(role = "tool", toolName = "search", content = "Sonuç"))) { "${it}-${key++}" }
        assertTrue(restored[0] is ChatItem.Thinking)
        assertTrue(restored[1] is ChatItem.Assistant)
        assertEquals("query", (restored[2] as ChatItem.Tool).detail)
        assertEquals("Sonuç", (restored[3] as ChatItem.Tool).detail)
        assertEquals(restored.size, restored.map { it.key }.distinct().size)
    }
    @Test fun `open REST session with idle live agent is not working`() {
        val idle = working.copy(status = "idle")
        val rest = HermesSession(id = "database", endedAt = null)
        val row = drawerRows(listOf(rest), listOf(idle), mapOf("database" to idle), flags, emptyMap(), currentSessionId = null).single()
        assertFalse(row.working)
        assertEquals("green", row.dot)
    }
    @Test fun `tool summary does not replace arguments or output`() {
        val event = GatewayEvent("tool.complete", payload = buildJsonObject { put("text", "Özet"); put("args", "Girdi"); put("output", "Sonuç"); put("result", "Sonuç") })
        assertEquals("Özet\n\nGirdi\n\nSonuç", event.toolDetail)
    }
}
