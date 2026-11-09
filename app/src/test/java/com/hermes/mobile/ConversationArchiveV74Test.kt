package com.hermes.mobile

import android.app.Application
import android.content.Context
import com.hermes.mobile.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ConversationArchiveV74Test {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun archive()=ConversationArchive(createdAt=1000,sourceName="Test Dashboard",conversations=listOf(
        ArchivedConversation("a","Birinci",listOf(SessionMessage(role="user",content="Türkçe soru",timestamp=100.0),SessionMessage(role="assistant",content="[Bağlantı](https://example.com)",timestamp=101.0))),
        ArchivedConversation("b","İkinci",listOf(SessionMessage(role="assistant",content="yanıt"))),
    ))
    @Before fun reset() {
        // Only this Robolectric application's temporary test directory.
        java.io.File(app.filesDir,"conversation-archives").deleteRecursively()
    }
    @Test fun selectedConversationsRoundTripWithLinksTimestampsAndNoServerCredentials() {
        val raw=ConversationArchiveCodec.encode(archive())
        assertEquals(archive(),ConversationArchiveCodec.decode(raw).archive)
        val text=raw.toString(Charsets.UTF_8)
        assertFalse(text.contains("geminiApiKey"));assertFalse(text.contains("ServerProfile"))
        assertFalse(text.contains("unselected-session"))
    }
    @Test fun changedMessageOrUnknownVersionIsRejected() {
        val raw=ConversationArchiveCodec.encode(archive()).toString(Charsets.UTF_8)
        assertTrue(runCatching {ConversationArchiveCodec.decode(raw.replace("Türkçe soru","değişti").toByteArray())}.isFailure)
        assertTrue(runCatching {ConversationArchiveCodec.decode(raw.replace("\"version\":1","\"version\":99").toByteArray())}.isFailure)
    }
    @Test fun settingsBackupIsNotMisreadAsConversationBackupAndDuplicateIdsAreRejected() {
        assertTrue(runCatching {ConversationArchiveCodec.decode("{\"schemaVersion\":1}".toByteArray())}.isFailure)
        assertTrue(runCatching {ConversationArchiveCodec.encode(archive().copy(conversations=listOf(archive().conversations[0],archive().conversations[0])))}.isFailure)
        assertTrue(runCatching {ConversationArchiveCodec.encode(archive().copy(conversations=emptyList()))}.isFailure)
    }
    @Test fun localImportIsDeduplicatedAndDoesNotOverwriteAnotherArchive() {
        val store=ConversationArchiveStore(app)
        val first=store.save(ConversationArchiveCodec.encode(archive()))
        assertEquals(first,store.save(ConversationArchiveCodec.encode(archive())))
        assertEquals(1,store.list().size)
        val other=store.save(ConversationArchiveCodec.encode(archive().copy(createdAt=2000)))
        assertEquals(2,store.list().size)
        assertEquals(archive(),ConversationArchiveStore(app).open(first.id))
        assertEquals(2000L,ConversationArchiveStore(app).open(other.id).createdAt)
        assertTrue(runCatching {store.open("../other")}.isFailure)
    }
    @Test fun oversizedStreamIsRejectedBeforeJsonParsing() {
        val stream=object:java.io.InputStream() {
            var left=ConversationArchiveCodec.MAX_BYTES+1
            override fun read():Int=if(left-->0)65 else -1
            override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
                if(left<=0)return -1
                val count=minOf(left,length);left-=count;return count
            }
        }
        assertTrue(runCatching {ConversationArchiveCodec.read(stream)}.isFailure)
    }
    @Test fun recordingArchiveIdsCannotAddressOtherAppFilesAndRecordingModuleIsPresent() {
        assertFalse(RecordingArchiveStore.validId("../../settings"))
        assertTrue(runCatching {RecordingArchiveStore(app).file("../secret")}.isFailure)
        assertTrue(BuiltInModules.all().any {it.kind==ModuleKind.RECORDINGS && it.builtIn})
    }
    @Test fun audioImportCannotExceedRemainingArchiveCapacity() {
        RecordingArchiveStore.checkCapacity(4096,8192)
        assertTrue(runCatching { RecordingArchiveStore.checkCapacity(4097,8192) }.isFailure)
        assertTrue(runCatching { RecordingArchiveStore.checkCapacity(1,4096) }.isFailure)
        assertTrue(runCatching { RecordingArchiveStore.checkCapacity(RecordingArchiveStore.MAX_BYTES+1,Long.MAX_VALUE) }.isFailure)
    }
    @Test fun failedMetadataWriteDoesNotLeaveAnUnlistedArchiveBody() {
        val raw=ConversationArchiveCodec.encode(archive())
        val id=ConversationArchiveCodec.decode(raw).sha256
        val directory=java.io.File(app.filesDir,"conversation-archives").apply {mkdirs()}
        val blocked=java.io.File(directory,"$id.meta").apply {mkdir()}
        java.io.File(blocked,"blocker").writeText("Simulated filesystem write failure")
        assertTrue(runCatching {ConversationArchiveStore(app).save(raw)}.isFailure)
        assertFalse(java.io.File(directory,"$id.json").exists())
    }
}
