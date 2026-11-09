package com.hermes.mobile.ui

import com.hermes.mobile.ChatItem
import com.hermes.mobile.data.ChatReadCursor
import java.security.MessageDigest

internal data class ReadableChatRow(val index: Int, val anchor: String, val timestamp: Double?, val streaming: Boolean)
internal data class ChatOpeningPosition(val index: Int, val hasUnread: Boolean)

internal class ChatReadIdentityCache {
    private data class Entry(val role: String, val text: String, val previous: String, val fingerprint: String, val anchor: String)
    private val entries = mutableMapOf<String, Entry>()
    fun retain(keys: Set<String>) { entries.keys.retainAll(keys) }
    fun identity(key: String, role: String, text: String, previous: String): Pair<String, String> {
        val old = entries[key]
        if (old != null && old.role == role && old.text == text && old.previous == previous) return old.fingerprint to old.anchor
        val fingerprint = digest("$role|${text.length}|${text.take(8192)}|${text.takeLast(2048)}")
        val anchor = digest("$previous|$fingerprint")
        entries[key] = Entry(role, text, previous, fingerprint, anchor)
        return fingerprint to anchor
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}

/** History row keys are regenerated; a content/context digest survives reloads and missing timestamps. */
internal fun readableChatRows(rows: List<ChatRow>, cache: ChatReadIdentityCache = ChatReadIdentityCache()): List<ReadableChatRow> {
    cache.retain(rows.mapTo(mutableSetOf()) { it.key })
    var previous = ""
    return rows.mapIndexedNotNull { index, row ->
        val item = (row as? ChatRow.Single)?.item
        val role: String; val text: String; val timestamp: Double?; val streaming: Boolean
        when (item) {
            is ChatItem.User -> { role = "user"; text = item.text; timestamp = item.ts; streaming = false }
            is ChatItem.Assistant -> { role = "assistant"; text = item.text; timestamp = item.ts; streaming = item.streaming }
            else -> return@mapIndexedNotNull null
        }
        if (text.isBlank() || streaming) return@mapIndexedNotNull null
        val (fingerprint, anchor) = cache.identity(row.key, role, text, previous)
        previous = fingerprint
        ReadableChatRow(index, anchor, timestamp?.takeIf { it.isFinite() && it > 0 }, streaming)
    }
}

internal fun chatOpeningPosition(rows: List<ReadableChatRow>, total: Int, cursor: ChatReadCursor?): ChatOpeningPosition {
    val bottom = (total - 1).coerceAtLeast(0)
    if (cursor == null) return ChatOpeningPosition(bottom, false)
    val matches = rows.filter { it.anchor == cursor.anchor }
    // Repeated identical exchanges are resolved conservatively, never by the newest occurrence.
    val read = matches.firstOrNull { it.timestamp != null && it.timestamp == cursor.timestamp } ?: matches.firstOrNull()
    val next = if (read != null) rows.firstOrNull { it.index > read.index }
        else cursor.timestamp?.let { ts -> rows.firstOrNull { it.timestamp != null && it.timestamp > ts } }
    return if (next != null) ChatOpeningPosition(next.index, true) else ChatOpeningPosition(bottom, false)
}
