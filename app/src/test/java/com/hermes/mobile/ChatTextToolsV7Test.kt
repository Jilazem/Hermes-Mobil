package com.hermes.mobile

import com.hermes.mobile.ui.*
import com.hermes.mobile.data.PinnedChatMessage
import com.hermes.mobile.data.togglePinnedMessage
import org.junit.Assert.*
import org.junit.Test

class ChatTextToolsV7Test {
    @Test fun exportUsesVisibleCompletedConversationOnly() {
        val entries = chatTextEntries(listOf(ChatItem.User("u", "merhaba"),
            ChatItem.Thinking("private", "gizli"), ChatItem.Assistant("a", "tamam"),
            ChatItem.Assistant("stream", "yarım", streaming = true)))
        val exported = exportChatMarkdown("Sohbet\nbaşlığı", entries)
        assertTrue(exported.startsWith("# Sohbet başlığı\n"))
        assertTrue(exported.contains("## Kullanıcı\n\nmerhaba"))
        assertFalse(exported.contains("gizli")); assertFalse(exported.contains("yarım"))
    }
    @Test fun linksKeepLabelsBalancedUrlsAndDeduplicate() {
        val links = messageLinks("[Kaynak](https://example.com/wiki/Test_(v7)) https://example.com/wiki/Test_(v7). [kötü](javascript:alert(1))")
        assertEquals(1, links.size)
        assertEquals("Kaynak", links.single().label)
        assertEquals("https://example.com/wiki/Test_(v7)", links.single().url)
    }
    @Test fun chatSearchRequiresAllWordsAndIgnoresCase() {
        val entries = listOf(ChatTextEntry("a", "assistant", "Hermes Mobil görev"), ChatTextEntry("b", "user", "Mobil"))
        assertEquals(listOf(entries[0]), searchChatText(entries, "hermes GÖREV"))
        assertEquals(entries, searchChatText(entries, "  "))
    }
    @Test fun exportFilenameCannotEscapeOrContainInvalidCharacters() {
        val name = chatExportFilename("../../rapor: \"taslak\"/x")
        assertFalse(name.contains('/')); assertFalse(name.contains(':')); assertFalse(name.contains('"'))
        assertEquals("Hermes-sohbet.md", chatExportFilename("..."))
    }
    @Test fun bookmarksDoNotCrossConversationsAndTogglePrecisely() {
        val a = PinnedChatMessage("host/a", "m1", "assistant", "bir", 1)
        val b = a.copy(scope = "host/b", text = "iki")
        val both = togglePinnedMessage(togglePinnedMessage(emptyList(), a), b)
        assertEquals(2, both.size)
        assertEquals(listOf(b), togglePinnedMessage(both, a))
    }
    @Test fun longBookmarkIsRejectedWithoutSilentTruncation() {
        val result = runCatching { togglePinnedMessage(emptyList(), PinnedChatMessage("s", "k", "user", "x".repeat(20_001), 0)) }
        assertTrue(result.isFailure)
    }
}
