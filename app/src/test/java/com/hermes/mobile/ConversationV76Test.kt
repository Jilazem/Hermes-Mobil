package com.hermes.mobile

import com.hermes.mobile.data.*
import com.hermes.mobile.ui.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ConversationV76Test {
    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test fun audioAndHtmlBecomeMediaWithoutLosingQuery() {
        val blocks = parseMarkdown("https://example.com/a.WAV?download=1\n\nhttps://example.com/report.html#x")
        assertEquals(MdBlock.Audio("https://example.com/a.WAV?download=1", "a.WAV"), blocks[0])
        assertTrue(blocks[1] is MdBlock.Html)
        assertEquals(MediaKind.AUDIO, mediaKindOf("https://example.com/a.opus"))
    }
    @Test fun serverFilesAreRoutedAsMediaButUnsafePathsRemainText() {
        assertTrue(parseMarkdown("/Volumes/EX/rapor/son.html").single() is MdBlock.Html)
        assertTrue(parseMarkdown("![Rapor](/Users/test/chart.png)").single() is MdBlock.Image)
        assertFalse(isServerMediaPath("//evil.example/chart.png"))
        assertFalse(isServerMediaPath("/Users/../private/chart.png"))
        assertFalse(isServerMediaPath("/Users/test/a\n.png"))
        assertEquals(MediaKind.VIDEO, conversationMediaKind("https://a.example/api/media?path=%2Ftmp%2Fvideo.mp4"))
        assertNull(safeExternalUrl("file:///tmp/a.wav"))
    }
    @Test fun serverAuthenticationNeverFollowsAnExternalOrigin() {
        val server = ServerProfile(name = "Test", baseUrl = "https://hermes.example:9150", token = "test-token")
        assertEquals("https://hermes.example:9150/api/files/download?path=%2FVolumes%2FEX%2Fa+b.wav", serverMediaUrl("/Volumes/EX/a b.wav", server))
        assertNull(serverMediaUrl("https://evil.example/api/media?path=x.png", server))
        assertNull(serverMediaUrl("https://hermes.example:9151/api/media?path=x.png", server))
        assertNull(serverMediaUrl("http://hermes.example:9150/api/media?path=x.png", server))
        assertNull(serverMediaUrl("https://hermes.example:9150/redirect?next=x", server))
        assertNotNull(serverMediaUrl("https://hermes.example:9150/api/media?path=x.png", server))
        assertEquals("https://hermes.example:9150/api/files/download?path=x.png", serverMediaUrl("https://hermes.example:9150/api/media?path=x.png", server))
    }
    @Test fun reconnectDoesNotLeaveAMissingChildPretendingToRun() {
        val start = mergeSubagentActivity(emptyList(), "subagent.start", obj("""{"subagent_id":"a"}"""))
        val result = mergeSubagentSnapshot(start, obj("""{"subagents":[],"delegations":[]}"""))
        assertEquals("unconfirmed", result.single().status)
    }
    @Test fun desktopMultimodalMessagesDoNotBreakTheConversation() {
        val message = Json.decodeFromString<SessionMessage>("""{"role":"assistant","content":[{"type":"text","text":"Rapor"},{"type":"image_url","image_url":{"url":"https://example.com/chart.png"}}]}""")
        assertEquals("Rapor\n\n![Görsel](https://example.com/chart.png)", message.content)
        assertNull(Json.decodeFromString<SessionMessage>("""{"content":null}""").content)
        assertEquals("metin", Json.decodeFromString<SessionMessage>("""{"content":"metin"}""").content)
        assertEquals("test", messageContentText(obj("""{"text":"test"}""")))
        assertEquals("[Bu medya bağlantısı önizlenemiyor]", messageContentText(Json.parseToJsonElement("""[{"type":"image_url","image_url":{"url":"javascript:alert(1)"}}]""")))
    }
    @Test fun inlineRasterImagesNeverEnableDataLinksOrSvg() {
        val raster = "data:image/png;base64,iVBORw0KGgo="
        assertTrue(isInlineConversationImage(raster))
        assertTrue(parseMarkdown("![Görsel]($raster)").single() is MdBlock.Image)
        assertFalse(isInlineConversationImage("data:image/svg+xml;base64,AAAA"))
        assertFalse(isInlineConversationImage("data:text/html;base64,AAAA"))
        assertNull(safeExternalUrl(raster))
    }
    @Test fun progressPreservesMeasuredTotalsAndCompletionDetails() {
        val start = mergeSubagentActivity(emptyList(), "subagent.start", obj("""{"subagent_id":"a","goal":"Denetle","model":"m"}"""))
        assertNull(start.single().outputTokens)
        val end = mergeSubagentActivity(start, "subagent.complete", obj("""{"subagent_id":"a","status":"succeeded","output_tokens":537,"duration_seconds":65.4,"files_written":["SONUC.md"],"output_tail":[{"tool":"read","preview":"Tamam","is_error":false}]}"""))
        assertEquals(1, end.size)
        assertEquals("Denetle", end.single().goal)
        assertEquals(537L, end.single().outputTokens)
        assertEquals("read: Tamam", end.single().output)
        assertEquals(listOf("SONUC.md"), end.single().filesWritten)
    }
    @Test fun failedDelegationsAndLiveRosterAreRecovered() {
        val result = mergeSubagentSnapshot(emptyList(), obj("""{"subagents":[{"subagent_id":"a","status":"RUNNING","goal":"İncele"}],"delegations":[{"delegation_id":"b","task_index":1,"status":"FAILED","error":"Kaynak yok"},{"delegation_id":"b","task_index":2,"status":"FAILED","error":"Zaman aşımı"}]}"""))
        assertEquals(3, result.size)
        assertEquals("Kaynak yok", result[1].summary)
        assertNotEquals(result[1].id, result[2].id)
    }
    @Test fun malformedIdentityAndForeignEventsDoNotCreateActivity() {
        assertTrue(mergeSubagentActivity(emptyList(), "message.delta", obj("""{"subagent_id":"x"}""")).isEmpty())
        assertTrue(mergeSubagentActivity(emptyList(), "subagent.start", obj("""{"goal":"No identity"}""")).isEmpty())
        val result = mergeSubagentActivity(emptyList(), "subagent.complete", obj("""{"subagent_id":"a","output_tokens":-4,"duration_seconds":-1}"""))
        assertNull(result.single().outputTokens)
        assertNull(result.single().duration)
    }
    @Test fun dismissAcknowledgesOnlyThatCrashAndPreservesLog() {
        val dir = java.nio.file.Files.createTempDirectory("crash-v76").toFile()
        try {
            val log = java.io.File(dir, "diag.log")
            val first = "11-05 01:00:00.000 C [crash] first\n"
            log.writeText(first)
            CrashGuard.lastCrash = null; CrashGuard.recoverFromDiagLog(dir)
            assertNotNull(CrashGuard.lastCrash)
            CrashGuard.dismissRecovered()
            CrashGuard.recoverFromDiagLog(dir)
            assertNull(CrashGuard.lastCrash)
            assertEquals(first, log.readText())
            log.appendText("11-05 02:00:00.000 C [crash] first\n")
            CrashGuard.recoverFromDiagLog(dir)
            assertNotNull(CrashGuard.lastCrash)
        } finally { CrashGuard.lastCrash = null; dir.deleteRecursively() }
    }
}
