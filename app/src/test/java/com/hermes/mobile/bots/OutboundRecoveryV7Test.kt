package com.hermes.mobile.bots

import android.content.Context
import com.hermes.mobile.data.bots.*
import com.hermes.mobile.data.remote.RemoteAgentTask
import com.hermes.mobile.ui.bots.BotConversationOutbox
import com.hermes.mobile.ui.bots.checkedBotConversation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OutboundRecoveryV7Test {
    private fun preferences() = RuntimeEnvironment.getApplication().getSharedPreferences(
        "recovery-v7-${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE,
    )

    @Test fun concreteRejectionDoesNotPermanentlyPoisonTheDeviceQueue() {
        val journal = PhongentTaskJournal(preferences())
        val first = journal.prepare("First", "conversation-a")
        journal.reject(first.id, "HTTP 409")
        assertTrue(journal.unresolved().isEmpty())
        assertNotEquals(first.id, journal.prepare("Corrected", "conversation-a").id)
    }

    @Test fun restoredUnclaimedTaskCannotBeSilentlyClaimedByANewGoal() {
        val prefs = preferences()
        prefs.edit().putString("tasks", Json.encodeToString(listOf(ControllerTask("request-old1", "Old goal")))).commit()
        val journal = PhongentTaskJournal(prefs)
        assertTrue(runCatching { journal.prepare("New goal", "") }.isFailure)
        assertEquals("queued", journal.unresolved().single().state)
        journal.discardQueued("request-old1")
        val next = journal.prepare("New goal", "")
        assertEquals("New goal", next.text)
        assertEquals("dispatching", next.state)
    }

    @Test fun unknownRequiresExplicitFreshListReconciliationAndNeverResendsOldPayload() {
        val prefs = preferences()
        val original = PhongentTaskJournal(prefs)
        val first = original.prepare("Original goal", "conversation-a")
        original.unknown(first.id)
        val restored = PhongentTaskJournal(prefs)
        assertTrue(runCatching { restored.prepare("New goal", "conversation-a") }.isFailure)
        val remote = listOf(RemoteAgentTask(first.id, "conversation-a", "Original goal", "running"))
        assertTrue(runCatching { restored.acknowledgeAbsent(first.id, remote) }.isFailure)
        restored.acknowledgeAbsent(first.id, emptyList())
        val next = restored.prepare("New goal", "conversation-a")
        assertNotEquals(first.id, next.id)
        assertEquals("New goal", next.text)
    }

    @Test fun corruptJournalCannotBecomeAnEmptyDispatchableQueue() {
        val prefs = preferences()
        prefs.edit().putString("tasks", "{broken").commit()
        val journal = PhongentTaskJournal(prefs)
        assertTrue(runCatching { journal.prepare("Never send", "") }.isFailure)
        assertEquals("{broken", prefs.getString("tasks", null))
        prefs.edit().putString("tasks", Json.encodeToString(listOf(ControllerTask("request-bad1", "Old goal", state = "invalid")))).commit()
        assertTrue(runCatching { PhongentTaskJournal(prefs).prepare("Never send", "") }.isFailure)
    }

    @Test fun olderJournalInstanceReadsAnotherInstancesDispatchFence() {
        val prefs = preferences()
        val a = PhongentTaskJournal(prefs)
        val b = PhongentTaskJournal(prefs)
        a.prepare("Only one", "")
        assertTrue(runCatching { b.prepare("Must not overwrite", "") }.isFailure)
        assertEquals("Only one", b.unresolved().single().text)
    }

    @Test fun pendingChatSurvivesRecreationAndNeedsHistoryAcknowledgement() {
        val prefs = preferences()
        BotConversationOutbox(prefs, "server-a/bot-a").begin("Do once")
        val recreated = BotConversationOutbox(prefs, "server-a/bot-a")
        assertTrue(recreated.pending())
        assertTrue(runCatching { recreated.begin("Do twice") }.isFailure)
        assertTrue(runCatching { recreated.acknowledgeHistory(false) }.isFailure)
        recreated.acknowledgeHistory(true)
        assertFalse(recreated.pending())
        assertFalse(BotConversationOutbox(prefs, "server-b/bot-a").pending())
    }

    @Test fun definitiveChatReceiptClearsOnlyItsOwnServerAndBotFence() {
        val prefs = preferences()
        val a = BotConversationOutbox(prefs, "server-a/bot-a")
        val b = BotConversationOutbox(prefs, "server-a/bot-b")
        a.begin("A"); b.begin("B")
        a.clearConfirmed()
        assertFalse(a.pending()); assertTrue(b.pending())
    }

    @Test fun chatResponseCannotSwitchExistingSessionOrBot() {
        fun response(session: String, bot: String) = Json.parseToJsonElement(
            """{"session_id":"$session","bot_id":"$bot","messages":[]}""",
        ).jsonObject
        checkedBotConversation(response("session-a", "bot-a"), "bot-a", "session-a")
        assertTrue(runCatching { checkedBotConversation(response("session-b", "bot-a"), "bot-a", "session-a") }.isFailure)
        assertTrue(runCatching { checkedBotConversation(response("session-a", "bot-b"), "bot-a", "session-a") }.isFailure)
    }
}
