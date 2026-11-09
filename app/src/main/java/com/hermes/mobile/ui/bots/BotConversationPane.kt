package com.hermes.mobile.ui.bots

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.*
import com.hermes.mobile.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** Durable send fence, scoped by server/account and bot. It never replays a saved message. */
internal class BotConversationOutbox(private val prefs: SharedPreferences, private val key: String) {
    fun pending(): Boolean = synchronized(LOCK) { prefs.contains(key) }
    fun begin(text: String) = synchronized(LOCK) {
        check(!prefs.contains(key)) { "Önce önceki mesajın sonucunu geçmişten kontrol edin." }
        check(prefs.edit().putString(key, text).commit()) { "Gönderim kaydı saklanamadı; mesaj gönderilmedi." }
    }
    fun clearConfirmed() = synchronized(LOCK) {
        check(prefs.edit().remove(key).commit()) { "Gönderim kaydı güncellenemedi; yeniden göndermeden geçmişi kontrol edin." }
    }
    fun acknowledgeHistory(checked: Boolean) {
        check(checked) { "Önce sunucudaki güncel geçmiş okunmalı." }
        clearConfirmed()
    }
    companion object { private val LOCK = Any() }
}

internal fun checkedBotConversation(response: JsonObject, botId: String, expectedSessionId: String? = null): JsonObject {
    val session = botChatSession(response)
    val id = botSessionId(session)
    check(id.isNotBlank() && session["messages"] is JsonArray &&
        (expectedSessionId.isNullOrBlank() || id == expectedSessionId) &&
        (session.text("bot_id").isBlank() || session.text("bot_id") == botId)) {
        "Sohbet yanıtı doğrulanamadı; yeniden göndermeden önce geçmişi kontrol edin."
    }
    return session
}

@Composable
internal fun BotConversationPane(
    bot: JsonObject, session: JsonObject?, history: List<JsonObject>, busy: Boolean,
    api: BotApiClient?, prefs: SharedPreferences,
    onSession: (JsonObject?) -> Unit, onHistory: (List<JsonObject>) -> Unit,
    onAct: (suspend () -> Unit) -> Unit, onInspect: (ChatTextEntry) -> Unit,
    onTask: (String) -> Unit,
) {
    var draft by rememberSaveable(bot.text("id")) { mutableStateOf("") }
    var historyOpen by remember { mutableStateOf(false) }
    val delivery = remember(api?.storageKey, bot.text("id")) {
        BotConversationOutbox(prefs, "v7-chat-pending-${api?.storageKey}-${bot.text("id")}")
    }
    var pending by remember(delivery) { mutableStateOf(delivery.pending()) }
    var historyChecked by remember(delivery) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        if (pending) OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Önceki mesaj ulaşmış olabilir. Aynı mesaj otomatik veya tek dokunuşla yeniden gönderilmez.")
                TextButton(enabled = !busy && api != null, onClick = { onAct {
                    val client = requireNotNull(api)
                    val result = client.request("GET", "/api/sessions?bot_id=${botApiSegment(bot.text("id"))}")
                    check(result["sessions"] is JsonArray) { "Sunucu güncel sohbet listesini doğrulamadı." }
                    val rows = result.rows("sessions")
                    val target = session?.let(::botSessionId)?.takeIf(String::isNotBlank)
                        ?: com.hermes.mobile.data.bots.BotWorkspaceLogic.latestSessionId(rows.map {
                            botSessionId(it) to ((it["updated_at"] as? JsonPrimitive)?.doubleOrNull ?: (it["created_at"] as? JsonPrimitive)?.doubleOrNull ?: 0.0)
                        })
                    val loaded = target?.let { checkedBotConversation(client.request("GET", "/api/sessions/${botApiSegment(it)}"), bot.text("id"), it) }
                    onHistory(rows); loaded?.let(onSession); historyOpen = true; historyChecked = true
                } }) { Text("Güncel geçmişi yükle ve incele") }
                TextButton(enabled = !busy && historyChecked, onClick = {
                    onAct { delivery.acknowledgeHistory(historyChecked); pending = false; historyChecked = false; draft = "" }
                }) { Text("Kontrol ettim; yeni bir mesaj yazacağım") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { onSession(null) }) { Text("Yeni sohbet") }
            TextButton(enabled = !busy, onClick = { historyOpen = !historyOpen }) { Text("Geçmiş · ${history.size}") }
            TextButton(enabled = draft.isNotBlank() && !busy && !pending, onClick = { onTask(draft) }) { Text("Göreve dönüştür") }
        }
        if (historyOpen) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(history.size) { index ->
                    val old = history[index]
                    val id = botSessionId(old)
                    FilterChip(selected = session?.let(::botSessionId) == id, enabled = !busy,
                        onClick = { onAct { onSession(checkedBotConversation(requireNotNull(api).request("GET", "/api/sessions/${botApiSegment(id)}"), bot.text("id"), id)); historyOpen = false } },
                        label = { Text("Sohbet ${history.size - index}") })
                }
            }
        }
        val messages = session?.let(::botVisibleMessages).orEmpty()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (messages.isEmpty()) item {
                Text("${bot.text("emoji")} ${bot.text("name")}", style = MaterialTheme.typography.headlineSmall)
                Text("Bir soru sor veya tek cümlelik bir görev yaz. Önceki sohbetler Geçmiş bölümünde.", style = MaterialTheme.typography.bodyMedium)
            }
            itemsIndexed(messages) { index, message ->
                val role = message.text("role")
                val text = message.text("content").ifBlank { message.text("text") }
                val entry = ChatTextEntry("${session?.let(::botSessionId)}-$index", role, text)
                Surface(color = if (role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(if (role == "user") "Sen" else bot.text("name"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        MarkdownText(text, modifier = Modifier.padding(top = 8.dp))
                        MessageTextActions(text, onInspect = { onInspect(entry) })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), enabled = !busy, maxLines = 5,
                placeholder = { Text("Mesaj veya görev yaz…") }, shape = MaterialTheme.shapes.large)
            FilledTonalButton(enabled = !busy && !pending && draft.isNotBlank() && api != null, onClick = {
                val sent = draft.trim()
                val sessionId = session?.let(::botSessionId)
                onAct {
                    delivery.begin(sent); pending = true; historyChecked = false
                    val response = try {
                        requireNotNull(api).request("POST", "/api/bots/${botApiSegment(bot.text("id"))}/chat", buildJsonObject {
                            put("message", sent); sessionId?.takeIf(String::isNotBlank)?.let { put("session_id", it) }
                        })
                    } catch (rejected: BotApiException) {
                        delivery.clearConfirmed(); pending = false
                        throw rejected
                    }
                    val confirmed = checkedBotConversation(response, bot.text("id"), sessionId)
                    onSession(confirmed); draft = ""
                    delivery.clearConfirmed(); pending = false
                    try { onHistory(api.request("GET", "/api/sessions?bot_id=${botApiSegment(bot.text("id"))}").rows("sessions")) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* The confirmed response remains visible; never replay a successful send. */ }
                }
            }) { Text(if (busy) "…" else "Gönder") }
        }
    }
}
