package com.hermes.mobile.ui

import com.hermes.mobile.ChatItem
import com.hermes.mobile.data.safeExternalUrl
import java.net.URI

data class ChatTextEntry(val key: String, val role: String, val text: String)
data class ChatLink(val label: String, val url: String) {
    val host: String get() = runCatching { URI(url).host }.getOrNull().orEmpty()
}

fun chatTextEntries(items: List<ChatItem>): List<ChatTextEntry> = items.mapNotNull {
    when (it) {
        is ChatItem.User -> ChatTextEntry(it.key, "user", it.text)
        is ChatItem.Assistant -> if (it.streaming) null else ChatTextEntry(it.key, "assistant", it.text)
        else -> null // Internal thinking, tool results and approvals are not exported as a conversation.
    }
}

fun messageLinks(text: String): List<ChatLink> {
    val found = mutableListOf<ChatLink>()
    val remaining = text.toCharArray()
    Regex("""\[([^\]\n]+)]\(""").findAll(text).forEach { match ->
        val destination = markdownLinkDestination(text, match.range.last) ?: return@forEach
        val safe = safeExternalUrl(destination.second) ?: return@forEach
        found += ChatLink(match.groupValues[1], safe)
        for (index in match.range.first..destination.first) remaining[index] = ' '
    }
    BARE_URL.findAll(String(remaining)).forEach { match ->
        safeExternalUrl(trimBareLink(match.value))?.let { found += ChatLink(it, it) }
    }
    return found.distinctBy { it.url }.take(100)
}

fun searchChatText(entries: List<ChatTextEntry>, query: String): List<ChatTextEntry> {
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return if (words.isEmpty()) entries else entries.filter { entry ->
        words.all { entry.text.contains(it, ignoreCase = true) }
    }
}

fun exportChatMarkdown(title: String, entries: List<ChatTextEntry>): String = buildString {
    append("# ").append(title.replace('\n', ' ').replace('\r', ' ').ifBlank { "Hermes sohbeti" }).append("\n\n")
    entries.forEach { entry ->
        append(if (entry.role == "user") "## Kullanıcı" else "## Hermes").append("\n\n")
        append(entry.text).append("\n\n")
    }
}

fun chatExportFilename(title: String): String {
    val base = title.replace(Regex("[\\p{Cntrl}<>:\"/\\\\|?*]+"), "-").trim(' ', '.', '-').take(70)
    return "${base.ifBlank { "Hermes-sohbet" }}.md"
}
